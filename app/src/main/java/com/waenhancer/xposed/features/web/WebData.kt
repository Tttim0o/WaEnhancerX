package com.waenhancer.xposed.features.web

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Environment
import com.waenhancer.utils.ContactNameResolver
import com.waenhancer.web.WebJson
import com.waenhancer.xposed.core.db.MessageStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads chats, messages and media for the web interface from WhatsApp's own msgstore.db,
 * through a read-only connection inside WhatsApp's process. Locked chats are never returned.
 * Nothing read here is logged.
 */
class WebData(private val app: Context) {

    private var database: SQLiteDatabase? = null
    private val columnCache = HashMap<String, Set<String>>()
    private val names = ConcurrentHashMap<String, String>()
    @Volatile private var namesLoadedAt = 0L

    @Synchronized
    private fun db(): SQLiteDatabase {
        val file = MessageStore.findMsgstoreDbFile(app) ?: error("msgstore.db not found")
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
        db().rawQuery("PRAGMA data_version", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
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
        /** JID of the most recently used row; messages are sent to this one. */
        var sendJid: String = jid
    }

    private fun notLocked(alias: String): String {
        val chat = columns("chat")
        val parts = ArrayList<String>()
        if ("chat_lock" in chat) parts.add("COALESCE($alias.chat_lock,0)=0")
        if ("hidden" in chat) parts.add("COALESCE($alias.hidden,0)=0")
        return if (parts.isEmpty()) "1=1" else parts.joinToString(" AND ")
    }

    private fun hasJidMap(): Boolean = "lid_row_id" in columns("jid_map")

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
        return byJid.values.sortedByDescending { it.sort }
    }

    fun chatsJson(query: String?): String {
        val q = query?.trim().orEmpty()
        val list = ArrayList<String>()
        for (chat in chats()) {
            val name = nameOf(chat.jid, chat.subject)
            if (q.isNotEmpty() && !name.contains(q, ignoreCase = true) && !chat.jid.contains(q)) continue
            list.add(WebJson.obj(
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
            ))
            if (list.size >= MAX_CHATS) break
        }
        return list.joinToString(",", "{\"chats\":[", "]}")
    }

    /** The chat for [jid], or null when it does not exist or is locked. */
    fun chat(jid: String): Chat? = if (!JID.matches(jid)) null else chats().firstOrNull { it.jid == jid }

    fun nameOf(jid: String, subject: String? = null): String {
        if (!subject.isNullOrBlank()) return subject
        val now = System.currentTimeMillis()
        if (now - namesLoadedAt > NAME_CACHE_MS) {
            names.clear()
            namesLoadedAt = now
        }
        return names.getOrPut(jid) {
            try {
                ContactNameResolver.resolveName(app, jid)
            } catch (_: Throwable) {
                ContactNameResolver.cleanUserPart(jid)
            }
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
                   $hasThumb, ${col(message, "m", "starred")}
            FROM message m$joins
            WHERE $where ORDER BY $order LIMIT ${limit.coerceIn(1, MAX_PAGE)}
        """.trimIndent()
        val isGroup = ContactNameResolver.isGroupJid(chat.jid)
        class Row(val id: Long, val json: LinkedHashMap<String, Any?>)
        val rows = ArrayList<Row>()
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
                rows.add(Row(c.getLong(0), item))
            }
        }
        if (afterId == null) rows.reverse()
        val reactions = reactionsFor(rows.map { it.id })
        for (row in rows) reactions[row.id]?.let { row.json["reactions"] = it }
        return WebJson.obj("messages" to rows.map { it.json }, "canSend" to WebSender.available)
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

    /** Whether message [id] belongs to a chat the web interface may show. */
    private fun messageVisible(id: Long): Boolean = try {
        db().rawQuery(
            "SELECT 1 FROM message m JOIN chat c ON c._id = m.chat_row_id WHERE m._id = ? AND ${notLocked("c")}",
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
        val dir = File(MessageStore.findAccountDataDir(app), "files/Avatars")
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
            app.filesDir
        )
    }

    private fun mediaFileOf(path: String): File? {
        if (path.startsWith("/")) return File(path)
        return mediaRoots.map { File(it, path) }.firstOrNull { it.exists() }
    }

    /** Only files inside WhatsApp's own media folders are served, whatever the database says. */
    private fun insideMediaRoots(file: File): Boolean = try {
        val canonical = file.canonicalPath
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
