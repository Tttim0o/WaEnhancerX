package com.waenhancer.xposed.features.web

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import com.waenhancer.utils.ContactNameResolver
import com.waenhancer.web.WebJson
import com.waenhancer.xposed.core.db.DelMessageStore
import com.waenhancer.xposed.core.db.MessageStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads chats, messages and media for the web interface from WhatsApp's own msgstore.db,
 * through a read-only connection inside WhatsApp's process. Locked chats are never returned.
 * Nothing read here is logged.
 */
class WebData(private val app: Context, private val account: WebAccounts.Account? = null) {

    private var database: SQLiteDatabase? = null
    private val columnCache = HashMap<String, Set<String>>()
    private val names = ConcurrentHashMap<String, String>()
    @Volatile private var namesLoadedAt = 0L

    @Synchronized
    private fun db(): SQLiteDatabase {
        // Another account's saved database, or the database of the account in use.
        val file = (if (account != null) account.dbFile.takeIf { it.isFile && it.canRead() }
            else MessageStore.findMsgstoreDbFile(app)) ?: error("msgstore.db not found")
        val current = database
        if (current != null && current.isOpen && current.path == file.absolutePath) return current
        try { current?.close() } catch (_: Throwable) {}
        columnCache.clear()
        return SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).also { database = it }
    }

    @Synchronized
    private fun columns(table: String): Set<String> = columnCache.getOrPut(table) {
        val result = HashSet<String>()
        try {
            db().rawQuery("PRAGMA table_info($table)", null).use { c ->
                val index = c.getColumnIndex("name")
                while (c.moveToNext()) result.add(c.getString(index))
            }
        } catch (_: Throwable) {
        }
        result
    }

    /** Changes whenever WhatsApp writes to the database; used to wake up waiting browsers. */
    fun revision(): Long = try {
        db().rawQuery("PRAGMA data_version", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } +
            statusRevision() * 1_000_003L
    } catch (_: Throwable) {
        0L
    }

    /** Changes when a status is added, seen or removed. */
    private fun statusRevision(): Long = try {
        statusDb()?.rawQuery("PRAGMA data_version", null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L
    } catch (_: Throwable) {
        0L
    }

    // ─── Chats ──────────────────────────────────────────────────────────

    class Chat(val jid: String) {
        val rows = ArrayList<Long>()
        var subject: String? = null
        var sort = 0L
        var unread = 0
        var archived = false
        var lastFromMe = false
        var lastType = 0
        var lastText: String? = null
        var lastStatus = 0
        /** JID of the most recently used row. */
        var sendJid: String = jid
        /** The contact's LID chat, when there is one; newer WhatsApp only accepts messages there. */
        var lidJid: String? = null
        /** A WhatsApp channel the account follows; read only. */
        var channel = false
        var muted = false
    }

    private fun notLocked(alias: String): String {
        val chat = columns("chat")
        val parts = ArrayList<String>()
        if ("chat_lock" in chat) parts.add("COALESCE($alias.chat_lock,0)=0")
        if ("hidden" in chat) parts.add("COALESCE($alias.hidden,0)=0")
        return if (parts.isEmpty()) "1=1" else parts.joinToString(" AND ")
    }

    private fun hasJidMap(): Boolean = "lid_row_id" in columns("jid_map")

    /** WhatsApp keeps channels as hidden chats; followed ones have a row in `newsletter`. */
    private fun hasChannels(): Boolean = "chat_row_id" in columns("newsletter")
    private fun channelFilter(alias: String): String =
        if (!hasChannels()) "0" else "$alias._id IN (SELECT chat_row_id FROM newsletter WHERE COALESCE(deleted,0)=0)"
    /** Chats the page may show: visible, unlocked chats, and followed channels. */
    private fun shown(alias: String): String = "((${notLocked(alias)}) OR ${channelFilter(alias)})"

    /** Every visible chat, newest first; a contact's phone and LID chats are merged. */
    fun chats(): List<Chat> {
        val chat = columns("chat")
        fun col(name: String, fallback: String = "NULL") = if (name in chat) "c.$name" else fallback
        val jidExpr = if (hasJidMap()) "COALESCE(pj.raw_string, j.raw_string)" else "j.raw_string"
        val mapJoin = if (hasJidMap())
            " LEFT JOIN jid_map jm ON jm.lid_row_id = j._id LEFT JOIN jid pj ON pj._id = jm.jid_row_id" else ""
        val lastJoin = if ("last_message_row_id" in chat) "c.last_message_row_id" else
            "(SELECT MAX(x._id) FROM message x WHERE x.chat_row_id = c._id)"
        val sql = """
            SELECT c._id, $jidExpr, j.raw_string, ${col("subject")}, ${col("sort_timestamp", "m.timestamp")},
                   ${col("archived", "0")}, ${col("unseen_message_count", "0")},
                   m.from_me, m.message_type, m.text_data, m.status
            FROM chat c JOIN jid j ON j._id = c.jid_row_id$mapJoin
            LEFT JOIN message m ON m._id = $lastJoin
            WHERE ${notLocked("c")}
        """.trimIndent()
        val byJid = LinkedHashMap<String, Chat>()
        db().rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val jid = c.getString(1) ?: continue
                val sort = c.getLong(4)
                if (sort <= 0 || jid == "status@broadcast" || !JID.matches(jid)) continue
                val ref = byJid.getOrPut(jid) { Chat(jid) }
                ref.rows.add(c.getLong(0))
                c.getString(2)?.takeIf { it.endsWith("@lid") }?.let { ref.lidJid = it }
                if (ref.subject.isNullOrBlank()) ref.subject = c.getString(3)
                ref.unread += maxOf(0, c.getInt(6))
                if (c.getInt(5) == 1) ref.archived = true
                if (sort > ref.sort) {
                    ref.sort = sort
                    ref.sendJid = c.getString(2) ?: jid
                    ref.lastFromMe = c.getInt(7) == 1
                    ref.lastType = c.getInt(8)
                    ref.lastText = c.getString(9)
                    ref.lastStatus = c.getInt(10)
                }
            }
        }
        if (hasChannels()) try { addChannels(byJid, lastJoin, col("sort_timestamp", "m.timestamp")) } catch (_: Throwable) {}
        return byJid.values.sortedByDescending { it.sort }
    }

    private fun addChannels(byJid: LinkedHashMap<String, Chat>, lastJoin: String, sortCol: String) {
        val news = columns("newsletter")
        fun n(name: String, fallback: String = "NULL") = if (name in news) "n.$name" else fallback
        val sql = """
            SELECT c._id, j.raw_string, ${n("name")}, $sortCol, ${if ("unseen_message_count" in columns("chat")) "c.unseen_message_count" else "0"},
                   m.from_me, m.message_type, m.text_data, m.status, ${n("muted", "0")}
            FROM newsletter n JOIN chat c ON c._id = n.chat_row_id JOIN jid j ON j._id = c.jid_row_id
            LEFT JOIN message m ON m._id = $lastJoin
            WHERE COALESCE(${n("deleted", "0")},0)=0
        """.trimIndent()
        db().rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val jid = c.getString(1) ?: continue
                if (!JID.matches(jid) || byJid.containsKey(jid)) continue
                val ref = Chat(jid)
                ref.channel = true
                ref.rows.add(c.getLong(0))
                ref.subject = c.getString(2)
                ref.sort = maxOf(1L, c.getLong(3))
                ref.unread = maxOf(0, c.getInt(4))
                ref.lastFromMe = c.getInt(5) == 1
                ref.lastType = c.getInt(6)
                ref.lastText = c.getString(7)
                ref.lastStatus = c.getInt(8)
                ref.muted = c.getInt(9) == 1
                byJid[jid] = ref
            }
        }
    }

    fun chatsJson(query: String?): String =
        chatItems(query, null).joinToString(",", "{\"chats\":[", "]}") { it.second }

    /**
     * The chats as (time, JSON object) pairs. With [tag] (account id and name) every chat also says
     * which account it belongs to, for the merged inbox.
     */
    fun chatItems(query: String?, tag: Pair<String, String>?): List<Pair<Long, String>> {
        val q = query?.trim().orEmpty()
        val list = ArrayList<Pair<Long, String>>()
        for (chat in chats()) {
            val name = nameOf(chat.jid, chat.subject)
            if (q.isNotEmpty() && !name.contains(q, ignoreCase = true) && !chat.jid.contains(q)) continue
            val item = linkedMapOf<String, Any?>(
                "jid" to chat.jid,
                "name" to name,
                "group" to ContactNameResolver.isGroupJid(chat.jid),
                "ts" to chat.sort,
                "unread" to chat.unread,
                "archived" to chat.archived,
                "lastFromMe" to chat.lastFromMe,
                "lastType" to chat.lastType,
                "lastText" to chat.lastText?.take(160),
                "lastStatus" to chat.lastStatus
            )
            if (chat.channel) { item["channel"] = true; if (chat.muted) item["muted"] = true }
            if (tag != null) { item["account"] = tag.first; item["accountName"] = tag.second }
            list.add(chat.sort to WebJson.obj(*item.toList().toTypedArray()))
            if (list.size >= MAX_CHATS) break
        }
        return list
    }

    /** The chat for [jid], or null when it does not exist or is locked. */
    fun chat(jid: String): Chat? = if (!JID.matches(jid)) null else chats().firstOrNull { it.jid == jid }

    /**
     * Where a message for [chat] goes. WhatsApp moved one-to-one chats to LID addresses and refuses
     * to create a chat for a phone number ("disallow creating PN chat"), so the LID wins when known.
     */
    fun sendTarget(chat: Chat): String {
        chat.lidJid?.let { return it }
        if (!chat.sendJid.endsWith("@s.whatsapp.net") || !hasJidMap()) return chat.sendJid
        return try {
            db().rawQuery(
                "SELECT l.raw_string FROM jid p JOIN jid_map m ON m.jid_row_id = p._id " +
                    "JOIN jid l ON l._id = m.lid_row_id WHERE p.raw_string = ? LIMIT 1",
                arrayOf(chat.sendJid)
            ).use { c -> if (c.moveToFirst()) c.getString(0)?.takeIf { JID.matches(it) } else null } ?: chat.sendJid
        } catch (_: Throwable) {
            chat.sendJid
        }
    }

    fun nameOf(jid: String, subject: String? = null): String {
        if (!subject.isNullOrBlank()) return subject
        val now = System.currentTimeMillis()
        if (now - namesLoadedAt > NAME_CACHE_MS) {
            names.clear()
            namesLoadedAt = now
        }
        return names.getOrPut(jid) {
            try {
                if (account != null) contactName(jid) ?: ContactNameResolver.cleanUserPart(jid)
                else ContactNameResolver.resolveName(app, jid)
            } catch (_: Throwable) {
                ContactNameResolver.cleanUserPart(jid)
            }
        }
    }

    /** A name from another account's own address book (wa.db), read-only. */
    private fun contactName(jid: String): String? {
        val file = account?.contactsFile?.takeIf { it.isFile && it.canRead() } ?: return null
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).use { db ->
                db.rawQuery("SELECT display_name, wa_name FROM wa_contacts WHERE jid = ? LIMIT 1", arrayOf(jid)).use { c ->
                    if (c.moveToFirst()) (c.getString(0)?.takeIf { it.isNotBlank() } ?: c.getString(1)?.takeIf { it.isNotBlank() })
                    else null
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    // ─── Messages ───────────────────────────────────────────────────────

    /**
     * One page of a chat as JSON, oldest first. With [afterId] only newer rows are returned;
     * otherwise the page that ends before ([beforeSort], [beforeId]), or the latest page.
     */
    fun messagesJson(chat: Chat, beforeSort: Long?, beforeId: Long?, afterId: Long?, limit: Int): String {
        val message = columns("message")
        val media = columns("message_media")
        val quoted = columns("message_quoted")
        val thumbs = columns("message_thumbnail")
        fun col(table: Set<String>, alias: String, name: String) = if (name in table) "$alias.$name" else "NULL"
        val joins = StringBuilder()
        if ("sender_jid_row_id" in message) joins.append(" LEFT JOIN jid sj ON sj._id = m.sender_jid_row_id")
        if ("message_row_id" in quoted) joins.append(" LEFT JOIN message_quoted q ON q.message_row_id = m._id")
        if ("message_row_id" in media) joins.append(" LEFT JOIN message_media mm ON mm.message_row_id = m._id")
        if ("message_row_id" in thumbs) joins.append(" LEFT JOIN message_thumbnail mt ON mt.message_row_id = m._id")
        val sender = if ("sender_jid_row_id" in message) "sj.raw_string" else "NULL"
        val sortCol = if ("sort_id" in message) "m.sort_id" else "m._id"
        val hasThumb = if ("message_row_id" in thumbs) "mt.message_row_id IS NOT NULL" else "0"
        val where = StringBuilder("m.chat_row_id IN (${chat.rows.joinToString(",")}) AND m.timestamp > 0")
        if ("message_type" in message) where.append(" AND m.message_type != $TYPE_SYSTEM")
        val args = ArrayList<String>()
        val order: String
        if (afterId != null) {
            where.append(" AND m._id > ?")
            args.add(afterId.toString())
            order = "m._id ASC"
        } else {
            if (beforeSort != null && beforeId != null) {
                where.append(" AND ($sortCol < ? OR ($sortCol = ? AND m._id < ?))")
                args.add(beforeSort.toString()); args.add(beforeSort.toString()); args.add(beforeId.toString())
            }
            order = "$sortCol DESC, m._id DESC"
        }
        val sql = """
            SELECT m._id, $sortCol, m.from_me, m.timestamp, ${col(message, "m", "message_type")},
                   ${col(message, "m", "text_data")}, ${col(message, "m", "status")}, $sender,
                   ${col(quoted, "q", "text_data")}, ${col(media, "mm", "file_path")}, ${col(media, "mm", "mime_type")},
                   ${col(media, "mm", "media_caption")}, ${col(media, "mm", "media_name")}, ${col(media, "mm", "file_size")},
                   ${col(media, "mm", "media_duration")}, ${col(media, "mm", "width")}, ${col(media, "mm", "height")},
                   $hasThumb, ${col(message, "m", "starred")}, ${col(message, "m", "key_id")}
            FROM message m$joins
            WHERE $where ORDER BY $order LIMIT ${limit.coerceIn(1, MAX_PAGE)}
        """.trimIndent()
        val isGroup = ContactNameResolver.isGroupJid(chat.jid)
        class Row(val id: Long, val json: LinkedHashMap<String, Any?>)
        val rows = ArrayList<Row>()
        val revokedKeys = LinkedHashMap<String, Long>()
        db().rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                val type = c.getInt(4)
                if (type == TYPE_SYSTEM) continue
                val fromMe = c.getInt(2) == 1
                val item = LinkedHashMap<String, Any?>()
                item["id"] = c.getLong(0)
                item["sort"] = c.getLong(1)
                item["fromMe"] = fromMe
                item["ts"] = c.getLong(3)
                item["type"] = type
                item["text"] = c.getString(5) ?: c.getString(11)
                item["status"] = c.getInt(6)
                if (isGroup && !fromMe) {
                    c.getString(7)?.let { item["sender"] = nameOf(it) }
                }
                c.getString(8)?.let { item["quote"] = it.take(300) }
                val path = c.getString(9)
                val mime = c.getString(10)
                if (path != null || mime != null) {
                    val file = path?.let(::mediaFileOf)
                    item["media"] = linkedMapOf(
                        "mime" to mime,
                        "name" to (c.getString(12) ?: path?.substringAfterLast('/')),
                        "size" to c.getLong(13),
                        "duration" to c.getLong(14),
                        "w" to c.getInt(15),
                        "h" to c.getInt(16),
                        "voice" to (path?.contains("Voice Notes") == true),
                        "available" to (file != null && file.canRead())
                    )
                }
                if (c.getInt(17) == 1) item["thumb"] = true
                if (c.getInt(18) == 1) item["starred"] = true
                if (type == 42 || type == 43) item["viewOnce"] = true
                // Kept by WAEX's anti-revoke: the sender deleted it, the phone still has it.
                c.getString(19)?.let { keyId ->
                    deletedAt(keyId)?.let { item["deletedAt"] = it }
                    // WhatsApp replaced it with "This message was deleted"; WAEX's vault may have the text.
                    if (type == 15) revokedKeys[keyId] = c.getLong(0)
                }
                rows.add(Row(c.getLong(0), item))
            }
        }
        if (afterId == null) rows.reverse()
        if (revokedKeys.isNotEmpty()) {
            val texts = preservedTexts(revokedKeys.keys.toList())
            val byId = rows.associateBy { it.id }
            for ((keyId, rowId) in revokedKeys) {
                val text = texts[keyId] ?: continue
                byId[rowId]?.json?.let { json ->
                    json["type"] = 0
                    json["text"] = text
                    if (json["deletedAt"] == null) json["deletedAt"] = json["ts"]
                }
            }
        }
        val reactions = reactionsFor(rows.map { it.id })
        for (row in rows) reactions[row.id]?.let { row.json["reactions"] = it }
        val mine = myReactionsFor(rows.map { it.id })
        for (row in rows) mine[row.id]?.let { row.json["myReaction"] = it }
        // Channels are read only here: posting, replying and deleting need the channel's admin tools.
        val open = !chat.channel
        return WebJson.obj("messages" to rows.map { it.json }, "canSend" to (open && WebSender.available),
            "canReact" to (open && WebSender.canReact), "canReply" to (open && WebSender.canReply),
            "canDelete" to (open && WebSender.canDelete), "channel" to chat.channel,
            "canMarkRead" to (account == null && WebSender.canMarkRead),
            "canSendImage" to (open && account == null && WebSender.canSendImage),
            "canSendVideo" to (open && account == null && WebSender.canSendVideo))
    }

    /** Original texts from WAEX's deleted messages vault (kept in the WAEX app), by key id. */
    private fun preservedTexts(keyIds: List<String>): Map<String, String> = try {
        val extras = android.os.Bundle()
        extras.putStringArray("ids", keyIds.toTypedArray())
        val answer = app.contentResolver.call(android.net.Uri.parse("content://com.waenhancer.hookprovider"),
            "web_preserved_texts", null, extras)
        val texts = answer?.getBundle("texts")
        texts?.keySet()?.associateWith { texts.getString(it).orEmpty() }?.filterValues { it.isNotEmpty() } ?: emptyMap()
    } catch (_: Throwable) {
        emptyMap()
    }

    /** When the sender deleted a message that WAEX kept, or null. */
    private fun deletedAt(keyId: String): Long? = try {
        DelMessageStore.getInstance(app).getTimestampByMessageId(keyId).takeIf { it > 0 }
    } catch (_: Throwable) {
        null
    }

    /** The reaction this phone's user put on each message. */
    private fun myReactionsFor(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty() || "from_me" !in columns("message_add_on")) return emptyMap()
        val result = HashMap<Long, String>()
        try {
            db().rawQuery("""
                SELECT a.parent_message_row_id, r.reaction
                FROM message_add_on a JOIN message_add_on_reaction r ON r.message_add_on_row_id = a._id
                WHERE a.parent_message_row_id IN (${ids.joinToString(",")}) AND a.from_me = 1
                      AND r.reaction IS NOT NULL AND r.reaction != ''
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) result[c.getLong(0)] = c.getString(1)
            }
        } catch (_: Throwable) {
        }
        return result
    }

    /** The key WhatsApp knows message [id] by, when it is in [chat]. */
    fun messageKey(chat: Chat, id: Long): WebSender.Key? = try {
        db().rawQuery("""
            SELECT j.raw_string, m.key_id, m.from_me, m.sort_id
            FROM message m JOIN chat c ON c._id = m.chat_row_id JOIN jid j ON j._id = c.jid_row_id
            WHERE m._id = ? AND m.chat_row_id IN (${chat.rows.joinToString(",")})
        """.trimIndent(), arrayOf(id.toString())).use { c ->
            if (c.moveToFirst() && c.getString(0) != null && c.getString(1) != null) {
                WebSender.Key(c.getString(0), c.getString(1), c.getInt(2) == 1, c.getLong(3))
            } else null
        }
    } catch (_: Throwable) {
        null
    }

    private fun reactionsFor(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val result = HashMap<Long, MutableList<String>>()
        try {
            db().rawQuery("""
                SELECT a.parent_message_row_id, r.reaction
                FROM message_add_on a JOIN message_add_on_reaction r ON r.message_add_on_row_id = a._id
                WHERE a.parent_message_row_id IN (${ids.joinToString(",")}) AND r.reaction IS NOT NULL AND r.reaction != ''
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) result.getOrPut(c.getLong(0)) { ArrayList() }.add(c.getString(1))
            }
        } catch (_: Throwable) {
        }
        return result.mapValues { (_, list) ->
            list.groupingBy { it }.eachCount().entries.joinToString("  ") { (emoji, n) -> if (n > 1) "$emoji $n" else emoji }
        }
    }

    /** Text search over all visible chats, newest first. */
    fun searchJson(query: String, limit: Int): String {
        val q = query.trim()
        if (q.length < 2) return "{\"results\":[]}"
        val all = chats()
        val byRow = HashMap<Long, Chat>()
        for (chat in all) for (row in chat.rows) byRow[row] = chat
        val like = "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val sortCol = if ("sort_id" in columns("message")) "m.sort_id" else "m._id"
        val results = ArrayList<String>()
        db().rawQuery("""
            SELECT m._id, $sortCol, m.chat_row_id, m.from_me, m.timestamp, m.text_data
            FROM message m WHERE m.text_data LIKE ? ESCAPE '\' AND m.timestamp > 0
            ORDER BY m.timestamp DESC LIMIT ${limit.coerceIn(1, 200) * 2}
        """.trimIndent(), arrayOf(like)).use { c ->
            while (c.moveToNext() && results.size < limit) {
                val chat = byRow[c.getLong(2)] ?: continue
                results.add(WebJson.obj(
                    "id" to c.getLong(0),
                    "sort" to c.getLong(1),
                    "jid" to chat.jid,
                    "chat" to nameOf(chat.jid, chat.subject),
                    "fromMe" to (c.getInt(3) == 1),
                    "ts" to c.getLong(4),
                    "text" to snippet(c.getString(5).orEmpty(), q)
                ))
            }
        }
        return results.joinToString(",", "{\"results\":[", "]}")
    }

    private fun snippet(text: String, q: String): String {
        val at = text.indexOf(q, ignoreCase = true)
        if (at < 0 || text.length <= 200) return text.take(200)
        val start = maxOf(0, at - 60)
        return (if (start > 0) "…" else "") + text.substring(start, minOf(text.length, start + 200))
    }

    /** True once a message from this phone with [text] exists after row [afterId]. */
    fun sentAfter(afterId: Long, text: String): Boolean = try {
        db().rawQuery(
            "SELECT 1 FROM message WHERE _id > ? AND from_me = 1 AND text_data = ? LIMIT 1",
            arrayOf(afterId.toString(), text)
        ).use { it.moveToFirst() }
    } catch (_: Throwable) {
        false
    }

    fun maxMessageId(): Long = try {
        db().rawQuery("SELECT MAX(_id) FROM message", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
    } catch (_: Throwable) {
        0L
    }

    // ─── Media ──────────────────────────────────────────────────────────

    class Media(val file: File, val mime: String, val name: String)

    // ─── Statuses ───────────────────────────────────────────────────────
    // WhatsApp keeps statuses in their own database. Reading them here sends no "seen" receipt.

    private var statusDatabase: SQLiteDatabase? = null
    private val statusColumnCache = HashMap<String, Set<String>>()

    @Synchronized
    private fun statusDb(): SQLiteDatabase? {
        val dir = account?.dataDir ?: MessageStore.findAccountDataDir(app)
        val file = File(dir, "databases/status.db").takeIf { it.isFile && it.canRead() } ?: return null
        val current = statusDatabase
        if (current != null && current.isOpen && current.path == file.absolutePath) return current
        try { current?.close() } catch (_: Throwable) {}
        statusColumnCache.clear()
        return SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS).also { statusDatabase = it }
    }

    @Synchronized
    private fun statusColumns(table: String): Set<String> = statusColumnCache.getOrPut(table) {
        val result = HashSet<String>()
        try {
            statusDb()?.rawQuery("PRAGMA table_info($table)", null)?.use { c -> while (c.moveToNext()) result.add(c.getString(1)) }
        } catch (_: Throwable) {}
        result
    }

    /** Statuses of the last 24 hours, grouped by person: unseen ones first, then the newest. */
    fun statusesJson(): String {
        val people = LinkedHashMap<String, LinkedHashMap<String, Any?>>()
        try {
            val db = statusDb() ?: return WebJson.obj("people" to emptyList<Any>(), "available" to false)
            val status = statusColumns("status")
            if ("sender_user_jid" !in status || "timestamp" !in status) {
                return WebJson.obj("people" to emptyList<Any>(), "available" to false)
            }
            val media = statusColumns("media_content")
            val text = statusColumns("status_text")
            val link = statusColumns("status_media_link")
            val thumbs = statusColumns("status_thumbnail")
            fun col(table: Set<String>, alias: String, name: String) = if (name in table) "$alias.$name" else "NULL"
            val joins = StringBuilder()
            if ("status_row_id" in link && "row_id" in media)
                joins.append(" LEFT JOIN status_media_link l ON l.status_row_id = s.row_id LEFT JOIN media_content mc ON mc.row_id = l.media_content_row_id")
            if ("status_row_id" in text) joins.append(" LEFT JOIN status_text st ON st.status_row_id = s.row_id")
            val hasThumb = if ("status_row_id" in thumbs)
                "EXISTS(SELECT 1 FROM status_thumbnail th WHERE th.status_row_id = s.row_id AND th.thumbnail IS NOT NULL)" else "0"
            val m = if (joins.contains(" mc ")) media else emptySet()
            val tx = if (joins.contains(" st ")) text else emptySet()
            val sort = if ("sort_id" in status) "s.sort_id" else "s.row_id"
            val archived = if ("is_archived" in status) " AND COALESCE(s.is_archived,0)=0" else ""
            val since = System.currentTimeMillis() - 24 * 3600_000L
            val sql = """
                SELECT s.row_id, s.sender_user_jid, ${col(status, "s", "type")}, s.timestamp, ${col(status, "s", "text_data")}, $sort,
                       ${col(m, "mc", "file_path")}, ${col(m, "mc", "mime_type")}, ${col(m, "mc", "width")}, ${col(m, "mc", "height")},
                       ${col(m, "mc", "media_duration")}, ${col(m, "mc", "file_size")},
                       ${col(tx, "st", "background_color")}, ${col(tx, "st", "text_color")}, ${col(tx, "st", "font_style")}, $hasThumb
                FROM status s$joins
                WHERE s.timestamp > ?$archived
                ORDER BY $sort ASC LIMIT 2000
            """.trimIndent()
            val unread = unreadStatusFrom()
            val me = ownJid()
            db.rawQuery(sql, arrayOf(since.toString())).use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(2)
                    if (type == 15 || type == TYPE_SYSTEM) continue
                    val raw = c.getString(1)?.takeIf { it.isNotBlank() }
                    val mine = raw == null || raw == me
                    val key = if (mine) "me" else raw!!
                    val person = people.getOrPut(key) {
                        linkedMapOf("jid" to key, "name" to (if (mine) null else nameOf(raw!!)), "mine" to mine,
                            "unseen" to 0, "ts" to 0L, "items" to ArrayList<Any?>())
                    }
                    val item = LinkedHashMap<String, Any?>()
                    item["id"] = c.getLong(0)
                    item["type"] = type
                    item["ts"] = c.getLong(3)
                    c.getString(4)?.let { item["text"] = it }
                    val path = c.getString(6)
                    val mime = c.getString(7)
                    if (path != null || mime != null) {
                        val file = path?.let(::mediaFileOf)
                        item["media"] = linkedMapOf("mime" to mime, "w" to c.getInt(8), "h" to c.getInt(9),
                            "duration" to c.getLong(10), "size" to c.getLong(11),
                            "available" to (file != null && file.canRead() && insideMediaRoots(file)))
                    }
                    if (!c.isNull(12)) item["bg"] = c.getInt(12)
                    if (!c.isNull(13)) item["fg"] = c.getInt(13)
                    if (!c.isNull(14)) item["font"] = c.getInt(14)
                    if (c.getInt(15) == 1) item["thumb"] = true
                    val from = unread[raw ?: ""]
                    val unseen = !mine && from != null && c.getLong(5) >= from
                    if (unseen) {
                        item["unseen"] = true
                        person["unseen"] = (person["unseen"] as Int) + 1
                    }
                    if (item["ts"] as Long > person["ts"] as Long) person["ts"] = item["ts"]
                    @Suppress("UNCHECKED_CAST")
                    (person["items"] as ArrayList<Any?>).add(item)
                }
            }
        } catch (_: Throwable) {
            return WebJson.obj("people" to emptyList<Any>(), "available" to false)
        }
        val list = people.values.sortedWith(compareByDescending<Map<String, Any?>> { it["mine"] == true }
            .thenByDescending { (it["unseen"] as Int) > 0 }.thenByDescending { it["ts"] as Long })
        return WebJson.obj("people" to list, "available" to true)
    }

    /** For each person with unseen statuses, the sort id of the first one not seen yet. */
    private fun unreadStatusFrom(): Map<String, Long> {
        val info = statusColumns("status_info")
        if ("chat_jid" !in info || "unread_count" !in info || "first_unread_sort_id" !in info) return emptyMap()
        val result = HashMap<String, Long>()
        try {
            statusDb()?.rawQuery("SELECT chat_jid, first_unread_sort_id FROM status_info WHERE unread_count > 0", null)?.use { c ->
                while (c.moveToNext()) c.getString(0)?.let { result[it] = c.getLong(1) }
            }
        } catch (_: Throwable) {}
        return result
    }

    /** The account's own JID, to tell its own statuses apart. */
    private fun ownJid(): String? = try {
        val dir = account?.dataDir ?: MessageStore.findAccountDataDir(app)
        val prefs = File(dir, "shared_prefs/" + app.packageName + "_preferences_light.xml")
        if (!prefs.isFile) null else Regex("<string name=\"registration_jid\">([^<]*)</string>")
            .find(prefs.readText())?.groupValues?.get(1)?.let { if (it.contains('@')) it else "$it@s.whatsapp.net" }
    } catch (_: Throwable) {
        null
    }

    fun statusMedia(id: Long): Media? = try {
        val m = statusColumns("media_content")
        if ("file_path" !in m || "status_row_id" !in statusColumns("status_media_link")) null else {
            val nameCol = if ("media_name" in m) "mc.media_name" else "NULL"
            statusDb()?.rawQuery("""SELECT mc.file_path, mc.mime_type, $nameCol FROM status_media_link l
                JOIN media_content mc ON mc.row_id = l.media_content_row_id WHERE l.status_row_id = ? LIMIT 1""",
                arrayOf(id.toString()))?.use { c ->
                if (!c.moveToFirst()) return@use null
                val file = c.getString(0)?.let(::mediaFileOf) ?: return@use null
                if (!file.isFile || !file.canRead() || !insideMediaRoots(file)) return@use null
                val mime = c.getString(1)?.takeIf { MIME.matches(it) } ?: "application/octet-stream"
                Media(file, mime, c.getString(2) ?: file.name)
            }
        }
    } catch (_: Throwable) {
        null
    }

    fun statusThumbnail(id: Long): ByteArray? = try {
        if ("status_row_id" !in statusColumns("status_thumbnail")) null
        else statusDb()?.rawQuery("SELECT thumbnail FROM status_thumbnail WHERE status_row_id = ? AND thumbnail IS NOT NULL LIMIT 1",
            arrayOf(id.toString()))?.use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
    } catch (_: Throwable) {
        null
    }

    /** Whether message [id] belongs to a chat the web interface may show. */
    private fun messageVisible(id: Long): Boolean = try {
        db().rawQuery(
            "SELECT 1 FROM message m JOIN chat c ON c._id = m.chat_row_id WHERE m._id = ? AND ${shown("c")}",
            arrayOf(id.toString())
        ).use { it.moveToFirst() }
    } catch (_: Throwable) {
        false
    }

    fun media(id: Long): Media? {
        if (!messageVisible(id)) return null
        val media = columns("message_media")
        if ("file_path" !in media) return null
        val nameCol = if ("media_name" in media) "media_name" else "NULL"
        db().rawQuery("SELECT file_path, mime_type, $nameCol FROM message_media WHERE message_row_id = ?",
            arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            val path = c.getString(0) ?: return null
            val file = mediaFileOf(path) ?: return null
            if (!file.isFile || !file.canRead() || !insideMediaRoots(file)) return null
            val mime = c.getString(1)?.takeIf { MIME.matches(it) } ?: "application/octet-stream"
            return Media(file, mime, c.getString(2) ?: file.name)
        }
    }

    fun thumbnail(id: Long): ByteArray? {
        if (!messageVisible(id)) return null
        return try {
            db().rawQuery("SELECT thumbnail FROM message_thumbnail WHERE message_row_id = ?",
                arrayOf(id.toString())).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
        } catch (_: Throwable) {
            null
        }
    }

    /** The small profile picture WhatsApp keeps for a chat, when it has one. */
    fun avatar(jid: String): File? {
        val chat = chat(jid) ?: return null
        val dir = File(account?.dataDir ?: MessageStore.findAccountDataDir(app), "files/Avatars")
        val candidates = LinkedHashSet<String>()
        candidates.add(chat.jid)
        candidates.add(chat.sendJid)
        for (name in candidates) {
            val file = File(dir, "$name.j")
            if (file.isFile && file.canRead()) return file
        }
        return null
    }

    private val mediaRoots: List<File> by lazy {
        @Suppress("DEPRECATION")
        val storage = Environment.getExternalStorageDirectory()
        listOf(
            File(storage, "Android/media/${app.packageName}/WhatsApp"),
            File(storage, "WhatsApp"),
            app.filesDir,
            // View once media stays in WhatsApp's private folders until it is opened.
            app.cacheDir,
            account?.dataDir ?: MessageStore.findAccountDataDir(app)
        )
    }

    private fun mediaFileOf(path: String): File? {
        if (path.startsWith("/")) return File(path)
        return mediaRoots.map { File(it, path) }.firstOrNull { it.exists() }
    }

    /** Only files inside WhatsApp's own media folders are served, whatever the database says. */
    private fun insideMediaRoots(file: File): Boolean = try {
        val canonical = file.canonicalPath
        // The account folder also holds databases and settings; those are never media.
        !canonical.contains("/databases/") && !canonical.contains("/shared_prefs/") &&
            mediaRoots.any { canonical.startsWith(it.canonicalPath + File.separator) }
    } catch (_: Throwable) {
        false
    }

    companion object {
        const val TYPE_SYSTEM = 7
        const val MAX_PAGE = 200
        const val MAX_CHATS = 500
        private const val NAME_CACHE_MS = 10 * 60_000L
        val JID = Regex("^[0-9A-Za-z._:-]{1,64}@[a-z0-9.]{1,32}$")
        private val MIME = Regex("^[a-zA-Z0-9.+-]{1,40}/[a-zA-Z0-9.+;= -]{1,60}$")
    }
}
