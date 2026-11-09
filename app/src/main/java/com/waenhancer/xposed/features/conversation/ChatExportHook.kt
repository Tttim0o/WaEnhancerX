package com.waenhancer.xposed.features.conversation

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.waenhancer.app.R
import com.waenhancer.utils.ContactNameResolver
import com.waenhancer.utils.UiText
import com.waenhancer.xposed.core.BaseFeature
import com.waenhancer.xposed.core.db.MessageStore
import de.robv.android.xposed.XC_MethodHook
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adds "Export chat" to a chat's ⋮ menu. The chat is read from WhatsApp's own msgstore.db in
 * WhatsApp's process and written to Download/WAEX/Exports as HTML (optionally with copies of
 * the media files) or plain text. Message text is written only to that file, never logged.
 */
class ChatExportHook(context: Context, classLoader: ClassLoader, prefs: SharedPreferences) :
    BaseFeature(context, classLoader, prefs) {

    override val name: String
        get() = "Chat Export"

    private val running = AtomicBoolean(false)

    override fun hook() {
        val hook = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val activity = param.thisObject as? Activity ?: return
                val menu = param.args.firstOrNull() as? Menu ?: return
                try {
                    addMenuItem(activity, menu)
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG Menu item unavailable: ${t.javaClass.simpleName}")
                }
            }
        }
        val conversation = try {
            classLoader.loadClass("com.whatsapp.Conversation")
        } catch (_: Throwable) {
            null
        }
        var hooked = false
        conversation?.declaredMethods?.forEach { m ->
            if (m.parameterTypes.size == 1 && m.parameterTypes[0] == Menu::class.java) {
                try {
                    XposedBridge.hookMethod(m, hook)
                    hooked = true
                } catch (_: Throwable) {
                }
            }
        }
        if (!hooked) {
            XposedHelpers.findAndHookMethod(Activity::class.java, "onCreateOptionsMenu", Menu::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val activity = param.thisObject as? Activity ?: return
                        if (!activity.javaClass.name.contains("Conversation")) return
                        val menu = param.args.firstOrNull() as? Menu ?: return
                        try {
                            addMenuItem(activity, menu)
                        } catch (t: Throwable) {
                            XposedBridge.log("$TAG Menu item unavailable: ${t.javaClass.simpleName}")
                        }
                    }
                })
        }
    }

    private fun addMenuItem(activity: Activity, menu: Menu) {
        if (!isEnabled(PREF_KEY, true) || menu.findItem(MENU_ITEM_ID) != null) return
        val item = com.waenhancer.xposed.utils.WaexMenu.of(menu).add(Menu.NONE, MENU_ITEM_ID, Menu.NONE,
            UiText.get(R.string.waex_ui_chat_export, "Export chat"))
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        item.setOnMenuItemClickListener {
            try {
                askFormat(activity)
            } catch (t: Throwable) {
                XposedBridge.log("$TAG Dialog failed: ${t.javaClass.simpleName}")
            }
            true
        }
    }

    private fun askFormat(activity: Activity) {
        val options = arrayOf(
            UiText.get(R.string.waex_ui_chat_export_html, "HTML (looks like the chat)"),
            UiText.get(R.string.waex_ui_chat_export_html_media, "HTML with media files"),
            UiText.get(R.string.waex_ui_chat_export_txt, "Plain text (TXT)")
        )
        AlertDialog.Builder(activity)
            .setTitle(UiText.get(R.string.waex_ui_chat_export, "Export chat"))
            .setItems(options) { _, which ->
                start(activity, html = which != 2, withMedia = which == 1)
            }
            .setNegativeButton(UiText.get(R.string.action_cancel, "Cancel"), null)
            .show()
    }

    private fun start(activity: Activity, html: Boolean, withMedia: Boolean) {
        if (!running.compareAndSet(false, true)) return
        val jid = ContactNameResolver.extractJidFromActivity(activity)
        val title = try {
            ContactNameResolver.resolveFromActivity(activity, jid)
        } catch (_: Throwable) {
            ContactNameResolver.cleanUserPart(jid)
        }
        val app = activity.applicationContext
        Toast.makeText(activity, UiText.get(R.string.waex_ui_chat_export_running, "Exporting chat…"),
            Toast.LENGTH_SHORT).show()
        EXECUTOR.execute {
            val started = System.currentTimeMillis()
            try {
                val result = export(app, jid, title, html, withMedia)
                XposedBridge.log("$TAG Exported ${result.count} messages, ${result.mediaCopied} media files, " +
                    "in ${System.currentTimeMillis() - started} ms")
                activity.runOnUiThread { finished(activity, result) }
            } catch (t: Throwable) {
                XposedBridge.log("$TAG Export failed: ${t.javaClass.simpleName}")
                activity.runOnUiThread {
                    Toast.makeText(activity, UiText.get(R.string.waex_ui_chat_export_failed,
                        "Chat export failed"), Toast.LENGTH_LONG).show()
                }
            } finally {
                running.set(false)
            }
        }
    }

    private class Result(val uri: Uri, val folder: String, val mime: String, val count: Int, val mediaCopied: Int)

    private fun finished(activity: Activity, result: Result) {
        if (result.count == 0) {
            Toast.makeText(activity, UiText.get(R.string.waex_ui_no_message_history_found,
                "No message history found"), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(activity, UiText.get(R.string.waex_ui_chat_export_saved, "Saved to %s",
            result.folder), Toast.LENGTH_LONG).show()
        try {
            val share = Intent(Intent.ACTION_SEND)
                .setType(result.mime)
                .putExtra(Intent.EXTRA_STREAM, result.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            activity.startActivity(Intent.createChooser(share, null))
        } catch (_: Throwable) {
        }
    }

    // ─── Reading the chat ───────────────────────────────────────────────

    private fun export(app: Context, jid: String, title: String, html: Boolean, withMedia: Boolean): Result {
        val dbFile = MessageStore.findMsgstoreDbFile(app) ?: error("msgstore.db not found")
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())
        val baseName = safeName(title) + "_" + stamp
        val folder = "WAEX/Exports" + if (withMedia) "/$baseName" else ""
        val fileName = baseName + if (html) ".html" else ".txt"
        val mime = if (html) "text/html" else "text/plain"
        val target = createOutput(app, folder, fileName, mime)
        val isGroup = ContactNameResolver.isGroupJid(jid)
        val names = HashMap<String, String>()
        var mediaCopied = 0
        var count = 0
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val chatRows = chatRowIds(db, jid)
            if (chatRows.isEmpty()) error("chat not found")
            val reactions = reactionsByMessage(db, chatRows)
            target.stream.use { stream ->
                val writer = ChatExportWriter(OutputStreamWriter(stream, Charsets.UTF_8).buffered(), html, title, isGroup)
                writer.begin(System.currentTimeMillis())
                db.rawQuery(messagesSql(db, chatRows), null).use { c ->
                    while (c.moveToNext()) {
                        val rowId = c.getLong(0)
                        val fromMe = c.getInt(1) == 1
                        var type = c.getInt(3)
                        val filePath = c.getString(7)
                        val mimeType = c.getString(8)
                        if (type == ChatExportWriter.TYPE_AUDIO && (filePath?.contains("Voice Notes") == true
                                || mimeType?.contains("opus") == true)) type = ChatExportWriter.TYPE_VOICE
                        val senderJid = c.getString(5)
                        val sender = when {
                            fromMe -> UiText.get(R.string.waex_ui_chat_export_you, "You")
                            isGroup && senderJid != null -> names.getOrPut(senderJid) { nameOf(app, senderJid) }
                            else -> title
                        }
                        val mediaFile = filePath?.let { mediaFileOf(app, it) }
                        var link: String? = null
                        if (withMedia && html && mediaFile != null && mediaFile.canRead()) {
                            link = copyMedia(app, folder, mediaFile)
                            if (link != null) mediaCopied++
                        }
                        val text = when (type) {
                            ChatExportWriter.TYPE_DELETED -> UiText.get(R.string.waex_ui_chat_export_deleted, "This message was deleted")
                            else -> c.getString(4) ?: c.getString(9)
                        }
                        writer.add(ChatExportWriter.Message(
                            timestamp = c.getLong(2),
                            fromMe = fromMe,
                            sender = sender,
                            type = type,
                            text = text,
                            quote = c.getString(6),
                            mediaName = c.getString(10) ?: filePath?.substringAfterLast('/'),
                            mediaLink = link,
                            reactions = reactions[rowId]
                        ))
                    }
                }
                writer.end()
                count = writer.count
            }
        }
        target.finish()
        return Result(target.uri, "Download/$folder", mime, count, mediaCopied)
    }

    /** The chat's rows; a contact can have one chat under its phone JID and one under its LID. */
    private fun chatRowIds(db: SQLiteDatabase, jid: String): List<Long> {
        val sql = """
            WITH target_jid(jid_row_id) AS (
                SELECT _id FROM jid WHERE raw_string=?
                UNION
                SELECT jm.jid_row_id FROM jid_map jm JOIN jid j ON j._id = jm.lid_row_id WHERE j.raw_string=?
                UNION
                SELECT jm.lid_row_id FROM jid_map jm JOIN jid j ON j._id = jm.jid_row_id WHERE j.raw_string=?
            )
            SELECT _id FROM chat WHERE jid_row_id IN (SELECT jid_row_id FROM target_jid)
        """.trimIndent()
        val rows = ArrayList<Long>()
        try {
            db.rawQuery(sql, arrayOf(jid, jid, jid)).use { c -> while (c.moveToNext()) rows.add(c.getLong(0)) }
        } catch (_: Throwable) {
            // Databases without jid_map.
            db.rawQuery("SELECT c._id FROM chat c JOIN jid j ON c.jid_row_id = j._id WHERE j.raw_string=?",
                arrayOf(jid)).use { c -> while (c.moveToNext()) rows.add(c.getLong(0)) }
        }
        return rows
    }

    /**
     * Columns differ between WhatsApp versions, so optional ones are selected as NULL when
     * missing. Result columns: 0 _id, 1 from_me, 2 timestamp, 3 message_type, 4 text,
     * 5 sender JID, 6 quoted text, 7 file_path, 8 mime_type, 9 media_caption, 10 media_name.
     */
    private fun messagesSql(db: SQLiteDatabase, chatRows: List<Long>): String {
        val message = columns(db, "message")
        val media = columns(db, "message_media")
        val quoted = columns(db, "message_quoted")
        fun col(table: Set<String>, alias: String, name: String) = if (name in table) "$alias.$name" else "NULL"
        val joins = StringBuilder()
        if ("sender_jid_row_id" in message) joins.append(" LEFT JOIN jid sj ON sj._id = m.sender_jid_row_id")
        if ("message_row_id" in quoted) joins.append(" LEFT JOIN message_quoted q ON q.message_row_id = m._id")
        if ("message_row_id" in media) joins.append(" LEFT JOIN message_media mm ON mm.message_row_id = m._id")
        val sender = if ("sender_jid_row_id" in message) "sj.raw_string" else "NULL"
        val order = if ("sort_id" in message) "m.sort_id, m._id" else "m.timestamp, m._id"
        return """
            SELECT m._id, m.from_me, m.timestamp, ${col(message, "m", "message_type")}, ${col(message, "m", "text_data")},
                   $sender, ${col(quoted, "q", "text_data")}, ${col(media, "mm", "file_path")},
                   ${col(media, "mm", "mime_type")}, ${col(media, "mm", "media_caption")}, ${col(media, "mm", "media_name")}
            FROM message m$joins
            WHERE m.chat_row_id IN (${chatRows.joinToString(",")}) AND m.timestamp > 0
            ORDER BY $order
        """.trimIndent()
    }

    private fun columns(db: SQLiteDatabase, table: String): Set<String> {
        val names = HashSet<String>()
        try {
            db.rawQuery("PRAGMA table_info($table)", null).use { c ->
                val index = c.getColumnIndex("name")
                while (c.moveToNext()) names.add(c.getString(index))
            }
        } catch (_: Throwable) {
        }
        return names
    }

    /** Reactions per message row, e.g. "❤️ 2  👍"; empty when this WhatsApp version stores them elsewhere. */
    private fun reactionsByMessage(db: SQLiteDatabase, chatRows: List<Long>): Map<Long, String> {
        val result = HashMap<Long, MutableList<String>>()
        try {
            db.rawQuery("""
                SELECT a.parent_message_row_id, r.reaction
                FROM message_add_on a JOIN message_add_on_reaction r ON r.message_add_on_row_id = a._id
                WHERE a.chat_row_id IN (${chatRows.joinToString(",")}) AND r.reaction IS NOT NULL AND r.reaction != ''
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) result.getOrPut(c.getLong(0)) { ArrayList() }.add(c.getString(1))
            }
        } catch (_: Throwable) {
        }
        return result.mapValues { (_, list) ->
            list.groupingBy { it }.eachCount().entries.joinToString("  ") { (emoji, n) ->
                if (n > 1) "$emoji $n" else emoji
            }
        }
    }

    private fun nameOf(app: Context, senderJid: String): String = try {
        ContactNameResolver.resolveName(app, senderJid)
    } catch (_: Throwable) {
        ContactNameResolver.cleanUserPart(senderJid)
    }

    /** WhatsApp stores media paths relative to its media folder in shared storage. */
    private fun mediaFileOf(app: Context, path: String): File? {
        if (path.startsWith("/")) return File(path)
        @Suppress("DEPRECATION")
        val roots = listOf(
            File(Environment.getExternalStorageDirectory(), "Android/media/${app.packageName}/WhatsApp"),
            File(Environment.getExternalStorageDirectory(), "WhatsApp"),
            app.filesDir
        )
        return roots.map { File(it, path) }.firstOrNull { it.exists() }
    }

    // ─── Writing to Download ────────────────────────────────────────────

    private class Output(val uri: Uri, val stream: OutputStream, val finish: () -> Unit)

    private fun createOutput(app: Context, folder: String, name: String, mime: String): Output {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = app.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore insert failed")
            val stream = resolver.openOutputStream(uri) ?: error("cannot open output")
            return Output(uri, stream) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder)
        dir.mkdirs()
        val file = File(dir, name)
        return Output(Uri.fromFile(file), file.outputStream()) {}
    }

    /** Copies one media file next to the export; returns its link relative to the HTML file. */
    private fun copyMedia(app: Context, folder: String, file: File): String? = try {
        val name = file.name
        val mime = android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
        val out = createOutput(app, "$folder/media", name, mime)
        out.stream.use { o -> file.inputStream().use { it.copyTo(o) } }
        out.finish()
        "media/$name"
    } catch (_: Throwable) {
        null
    }

    private fun safeName(title: String): String =
        title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().replace(' ', '_').take(40).ifEmpty { "chat" }

    companion object {
        private const val TAG = "[WAEX][ChatExport]"
        const val PREF_KEY = "chat_export"
        private const val MENU_ITEM_ID = 0x57414558 // 'WAEX'
        private val EXECUTOR = Executors.newSingleThreadExecutor()
    }
}
