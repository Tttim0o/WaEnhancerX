package com.waenhancer.app.viewer

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Writes imported text messages into WhatsApp's msgstore.db with root (experimental).
 *
 * Steps: stop WhatsApp, copy the database files to a backup folder in WAEX's private storage,
 * insert the messages in one transaction with the device's sqlite3, run a quick integrity
 * check and give the files back their owner and SELinux label. When anything fails after the
 * backup, the backup is put back. Message text goes to sqlite3 through stdin only; it is never
 * written to a file or a log.
 */
object WhatsAppImporter {
    private const val PACKAGE = "com.whatsapp"
    private const val DB_DIR = "/data/data/$PACKAGE/databases"
    private const val DB = "$DB_DIR/msgstore.db"
    private const val WA_DB = "$DB_DIR/wa.db"
    private const val BACKUP_ROOT = "/data/data/com.waenhancer/files/msgstore_backups"
    private const val KEEP_BACKUPS = 2

    class TargetChat(val rowId: Long, val jid: String, val name: String, val lastTimestamp: Long)

    class Result(
        val inserted: Int,
        val duplicates: Int,
        val newer: Int,
        val noRoom: Int,
        val backup: String
    )

    class ImportException(message: String) : Exception(message)

    // ─── Root shell ─────────────────────────────────────────────────────

    private class Shell(val exit: Int, val out: String)

    private fun su(script: String, stdin: String? = null, timeoutSeconds: Long = 120): Shell {
        val process = ProcessBuilder("su").redirectErrorStream(true).start()
        val buffer = ByteArrayOutputStream()
        val reader = Thread {
            try {
                process.inputStream.use { it.copyTo(buffer) }
            } catch (_: Throwable) {
            }
        }
        reader.start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { w ->
            if (stdin == null) {
                w.write(script)
                w.write("\nexit\n")
            } else {
                // The script runs sqlite3 reading its SQL from the rest of stdin.
                w.write(script)
                w.write("\n")
                w.write(stdin)
                w.write("\n")
            }
        }
        // Process.waitFor(timeout) needs API 26; minSdk is lower.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (true) {
            try {
                process.exitValue()
                break
            } catch (_: IllegalThreadStateException) {
                if (System.nanoTime() > deadline) {
                    process.destroy()
                    throw ImportException("root command timed out")
                }
                Thread.sleep(50)
            }
        }
        reader.join(2000)
        return Shell(process.exitValue(), buffer.toString("UTF-8"))
    }

    private fun sql(db: String, query: String): List<List<String>> {
        val shell = su("sqlite3 -batch -noheader -separator '\u001f' '$db' <<'WAEX_SQL'\n$query\nWAEX_SQL")
        if (shell.exit != 0) throw ImportException("sqlite3 failed")
        return shell.out.lineSequence().filter { it.isNotEmpty() }.map { it.split('\u001f') }.toList()
    }

    private fun hexText(hex: String): String = String(hexBytes(hex), Charsets.UTF_8)

    private fun hexBytes(hex: String): ByteArray {
        val out = ByteArray(hex.length / 2)
        for (i in out.indices) out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return out
    }

    private fun toHex(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format(Locale.ROOT, "%02X", b))
        return sb.toString()
    }

    // ─── Queries ────────────────────────────────────────────────────────

    fun hasRoot(): Boolean = try {
        su("id -u").out.trim().lines().lastOrNull() == "0"
    } catch (_: Throwable) {
        false
    }

    /** One-to-one chats that already have messages, newest first. */
    fun listChats(): List<TargetChat> {
        if (!hasRoot()) throw ImportException("root access is needed")
        val names = HashMap<String, String>()
        try {
            sql(WA_DB, "SELECT jid, hex(coalesce(nullif(display_name,''), nullif(wa_name,''), '')) FROM wa_contacts;")
                .forEach { row -> if (row.size == 2 && row[1].isNotEmpty()) names[row[0]] = hexText(row[1]) }
        } catch (_: Throwable) {
            // Names are optional; numbers are shown instead.
        }
        val rows = sql(DB, """
            SELECT c._id, j.raw_string, coalesce(pj.raw_string, ''),
                   (SELECT max(m.timestamp) FROM message m WHERE m.chat_row_id = c._id) AS last
            FROM chat c JOIN jid j ON j._id = c.jid_row_id
            LEFT JOIN jid_map jm ON jm.lid_row_id = j._id LEFT JOIN jid pj ON pj._id = jm.jid_row_id
            WHERE j.server IN ('s.whatsapp.net', 'lid') AND last IS NOT NULL
            ORDER BY last DESC;
        """.trimIndent())
        return rows.mapNotNull { row ->
            if (row.size < 4) return@mapNotNull null
            val jid = row[1]
            val phone = row[2].ifEmpty { jid }
            val number = "+" + phone.substringBefore('@')
            val name = names[phone] ?: names[jid]
            TargetChat(row[0].toLong(), jid, if (name != null) "$name ($number)" else number, row[3].toLong())
        }
    }

    fun latestBackup(): String? = try {
        su("ls -1 '$BACKUP_ROOT' 2>/dev/null").out.lines().map { it.trim() }
            .filter { it.matches(Regex("\\d{8}-\\d{6}")) }.maxOrNull()
    } catch (_: Throwable) {
        null
    }

    // ─── Import ─────────────────────────────────────────────────────────

    fun import(chat: TargetChat, messages: List<ImportMessage>): Result {
        if (!hasRoot()) throw ImportException("root access is needed")
        if (messages.isEmpty()) throw ImportException("nothing to import")

        val columns = sql(DB, "SELECT name FROM pragma_table_info('message');").map { it[0] }.toSet()
        val required = listOf("chat_row_id", "from_me", "key_id", "timestamp", "message_type", "text_data", "sort_id")
        if (!columns.containsAll(required)) throw ImportException("unsupported WhatsApp database")

        // Sender rows are copied from the chat's own messages, so they match what WhatsApp uses.
        val senders = HashMap<Int, String>()
        sql(DB, """
            SELECT from_me, coalesce(sender_jid_row_id, 'NULL'), count(*) FROM message
            WHERE chat_row_id = ${chat.rowId} AND message_type = 0 GROUP BY 1, 2 ORDER BY 3 ASC;
        """.trimIndent()).forEach { row -> senders[row[0].toInt()] = row[1] }
        if (senders[1] == null) {
            sql(DB, """
                SELECT coalesce(m.sender_jid_row_id, 'NULL'), count(*) FROM message m
                JOIN chat c ON c._id = m.chat_row_id JOIN jid j ON j._id = c.jid_row_id
                WHERE m.from_me = 1 AND m.message_type = 0 AND j.server IN ('s.whatsapp.net', 'lid')
                GROUP BY 1 ORDER BY 2 DESC LIMIT 1;
            """.trimIndent()).firstOrNull()?.let { senders[1] = it[0] }
        }
        if (messages.any { !it.fromMe } && senders[0] == null) {
            throw ImportException("this chat has no received text messages to copy the sender from")
        }

        val existing = sql(DB, """
            SELECT sort_id, timestamp, hex(coalesce(text_data, '')) FROM message
            WHERE chat_row_id = ${chat.rowId} ORDER BY sort_id;
        """.trimIndent()).map { row ->
            ImportPlanner.Existing(row[0].toLong(), row[1].toLongOrNull() ?: 0L,
                row.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let(::hexText))
        }
        if (existing.isEmpty()) throw ImportException("the chat has no messages")
        val plan = ImportPlanner.plan(existing, messages)
        if (plan.planned.isEmpty()) {
            return Result(0, plan.duplicates, plan.newer, plan.noRoom, "")
        }

        val labels = labels()
        val backup = backup()
        try {
            val statements = buildInsert(chat.rowId, plan.planned, senders, columns)
            val run = su("sqlite3 -bail -batch '$DB'", stdin = statements, timeoutSeconds = 300)
            if (run.exit != 0 || !run.out.contains("WAEX_DONE")) throw ImportException("writing failed")
            val check = su("sqlite3 -batch '$DB' 'PRAGMA quick_check;'", timeoutSeconds = 300)
            if (check.exit != 0 || check.out.trim().lines().lastOrNull()?.trim() != "ok") {
                throw ImportException("integrity check failed")
            }
            fixFiles(labels)
        } catch (t: Throwable) {
            restore(backup, labels)
            throw if (t is ImportException) ImportException("${t.message}; the backup was restored")
            else ImportException("import failed; the backup was restored")
        }
        return Result(plan.planned.size, plan.duplicates, plan.newer, plan.noRoom, backup)
    }

    private fun buildInsert(
        chatRow: Long,
        planned: List<ImportPlanner.Planned>,
        senders: Map<Int, String>,
        columns: Set<String>
    ): String {
        val random = SecureRandom()
        val sb = StringBuilder()
        sb.append("PRAGMA busy_timeout=15000;\nBEGIN IMMEDIATE;\n")
        for (p in planned) {
            val m = p.message
            val values = linkedMapOf(
                "chat_row_id" to chatRow.toString(),
                "from_me" to if (m.fromMe) "1" else "0",
                "key_id" to "'${keyId(random)}'",
                "sender_jid_row_id" to (senders[if (m.fromMe) 1 else 0] ?: "NULL"),
                "status" to if (m.fromMe) "13" else "0",
                "broadcast" to "0",
                "recipient_count" to "0",
                "origination_flags" to "0",
                "origin" to "0",
                "timestamp" to m.timestamp.toString(),
                "received_timestamp" to if (m.fromMe) "0" else m.timestamp.toString(),
                "receipt_server_timestamp" to if (m.fromMe) m.timestamp.toString() else "0",
                "message_type" to "0",
                "text_data" to "CAST(X'${toHex(m.text)}' AS TEXT)",
                "starred" to "0",
                "lookup_tables" to "0",
                "message_add_on_flags" to "0",
                "sort_id" to p.sortId.toString(),
                "view_mode" to "0"
            ).filterKeys { it in columns }
            sb.append("INSERT INTO message(").append(values.keys.joinToString(","))
                .append(") VALUES(").append(values.values.joinToString(",")).append(");\n")
        }
        sb.append("COMMIT;\nPRAGMA wal_checkpoint(TRUNCATE);\nSELECT 'WAEX_DONE';\n.quit")
        return sb.toString()
    }

    /** 32 upper-case hex characters, the length WhatsApp uses for its own message ids. */
    private fun keyId(random: SecureRandom): String {
        val bytes = ByteArray(14).also { random.nextBytes(it) }
        return "WAEX" + bytes.joinToString("") { String.format(Locale.ROOT, "%02X", it) }
    }

    // ─── Backup and restore ─────────────────────────────────────────────

    private fun stopWhatsApp() {
        su("am force-stop $PACKAGE")
    }

    /** Stops WhatsApp and copies its database files; returns the backup name. */
    private fun backup(): String {
        stopWhatsApp()
        val name = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(java.util.Date())
        val dir = "$BACKUP_ROOT/$name"
        val shell = su("""
            set -e
            mkdir -p '$dir'
            chmod 700 '$BACKUP_ROOT' '$dir'
            cp -p '$DB' '$dir/'
            for f in msgstore.db-wal msgstore.db-shm; do
              if [ -f '$DB_DIR'/${'$'}f ]; then cp -p '$DB_DIR'/${'$'}f '$dir/'; fi
            done
            echo WAEX_BACKUP_OK
        """.trimIndent(), timeoutSeconds = 300)
        if (shell.exit != 0 || !shell.out.contains("WAEX_BACKUP_OK")) {
            su("rm -rf '$dir'")
            throw ImportException("backup failed (enough free space?)")
        }
        pruneBackups()
        return name
    }

    private fun pruneBackups() {
        val all = su("ls -1 '$BACKUP_ROOT'").out.lines().map { it.trim() }
            .filter { it.matches(Regex("\\d{8}-\\d{6}")) }.sorted()
        all.dropLast(KEEP_BACKUPS).forEach { su("rm -rf '$BACKUP_ROOT/$it'") }
    }

    /** Puts a backup back in place of WhatsApp's current database. */
    fun restore(name: String, known: Map<String, String> = labels()) {
        require(name.matches(Regex("\\d{8}-\\d{6}"))) { "bad backup name" }
        stopWhatsApp()
        val dir = "$BACKUP_ROOT/$name"
        val shell = su("""
            set -e
            [ -f '$dir/msgstore.db' ]
            rm -f '$DB_DIR/msgstore.db-wal' '$DB_DIR/msgstore.db-shm'
            cp -p '$dir/msgstore.db' '$DB'
            for f in msgstore.db-wal msgstore.db-shm; do
              if [ -f '$dir'/${'$'}f ]; then cp -p '$dir'/${'$'}f '$DB_DIR/'; fi
            done
            echo WAEX_RESTORE_OK
        """.trimIndent(), timeoutSeconds = 300)
        if (shell.exit != 0 || !shell.out.contains("WAEX_RESTORE_OK")) throw ImportException("restore failed")
        fixFiles(known)
    }

    /** SELinux labels of the database files, read before they are touched. */
    private fun labels(): Map<String, String> {
        val out = su("ls -Z '$DB' '$DB-wal' '$DB-shm' 2>/dev/null").out
        val result = HashMap<String, String>()
        out.lines().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size == 2 && parts[0].startsWith("u:")) result[parts[1]] = parts[0]
        }
        return result
    }

    /**
     * Gives the database files WhatsApp's owner and their SELinux labels again. A file without a
     * known label (created by sqlite3 running as root) gets the label of another database file.
     */
    private fun fixFiles(known: Map<String, String>) {
        val fallback = known["$DB-wal"] ?: known["$DB-shm"] ?: known[DB] ?: return
        val script = StringBuilder("owner=${'$'}(stat -c %u:%g '$DB_DIR')\n")
        for (f in listOf(DB, "$DB-wal", "$DB-shm", "$DB-journal")) {
            val label = known[f] ?: fallback
            script.append("if [ -e '$f' ]; then chown \"${'$'}owner\" '$f'; chmod 600 '$f'; chcon '$label' '$f'; fi\n")
        }
        su(script.toString())
    }
}
