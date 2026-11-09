package com.waenhancer.xposed.features.conversation

import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes one chat as HTML or plain text. Has no Android or WhatsApp dependencies. */
class ChatExportWriter(
    private val out: Writer,
    private val html: Boolean,
    private val title: String,
    private val isGroup: Boolean,
    private val locale: Locale = Locale.getDefault()
) {
    class Message(
        val timestamp: Long,
        val fromMe: Boolean,
        val sender: String,
        val type: Int,
        val text: String?,
        val quote: String?,
        val mediaName: String?,
        /** Path relative to the export folder when the media file was copied, else null. */
        val mediaLink: String?,
        val reactions: String?
    )

    private val dayFormat = SimpleDateFormat("EEEE, d MMMM yyyy", locale)
    private val timeFormat = SimpleDateFormat("HH:mm", locale)
    private val fullFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
    private var lastDay: String? = null
    var count = 0
        private set

    fun begin(exportedAt: Long) {
        if (!html) {
            out.write("$title\n")
            out.write("Exported with WAEX, ${fullFormat.format(Date(exportedAt))}\n\n")
            return
        }
        out.write("""<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><style>
:root{--bg:#efeae2;--in:#fff;--out:#d9fdd3;--fg:#111b21;--muted:#667781;--q:#0000000d}
@media(prefers-color-scheme:dark){:root{--bg:#0b141a;--in:#202c33;--out:#005c4b;--fg:#e9edef;--muted:#8696a0;--q:#ffffff14}}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.4 system-ui,sans-serif}
header{position:sticky;top:0;background:#008069;color:#fff;padding:12px 16px;font-weight:600}
header small{display:block;font-weight:400;opacity:.8}
main{max-width:760px;margin:0 auto;padding:8px 12px 40px}
.day{text-align:center;margin:14px 0 6px}.day span{background:var(--in);color:var(--muted);font-size:12px;padding:4px 10px;border-radius:8px}
.m{display:flex;margin:3px 0}.m.me{justify-content:flex-end}
.b{max-width:80%;background:var(--in);padding:6px 9px 4px;border-radius:8px;box-shadow:0 1px .5px #0002;overflow-wrap:anywhere;white-space:pre-wrap}
.me .b{background:var(--out)}
.s{font-size:13px;font-weight:600;color:#06cf9c;margin-bottom:2px}
.q{background:var(--q);border-left:3px solid #06cf9c;border-radius:4px;padding:4px 6px;margin-bottom:4px;font-size:13px;color:var(--muted)}
.t{float:right;margin:6px 0 0 10px;font-size:11px;color:var(--muted)}
.x{color:var(--muted);font-style:italic}.r{font-size:13px;margin-top:2px}
.sys{text-align:center;color:var(--muted);font-size:12px;margin:8px 0}
img,video{max-width:100%;border-radius:6px;display:block;margin-bottom:4px}audio{max-width:100%}
</style></head><body><header>${esc(title)}<small>Exported with WAEX · ${esc(fullFormat.format(Date(exportedAt)))}</small></header><main>
""")
    }

    fun add(m: Message) {
        count++
        val day = dayFormat.format(Date(m.timestamp))
        if (html) {
            if (day != lastDay) out.write("<div class=\"day\"><span>${esc(day)}</span></div>\n")
            lastDay = day
            if (m.type == TYPE_SYSTEM) {
                out.write("<div class=\"sys\">${esc(m.text ?: "")}</div>\n")
                return
            }
            out.write(if (m.fromMe) "<div class=\"m me\"><div class=\"b\">" else "<div class=\"m\"><div class=\"b\">")
            if (isGroup && !m.fromMe) out.write("<div class=\"s\">${esc(m.sender)}</div>")
            m.quote?.takeIf { it.isNotBlank() }?.let { out.write("<div class=\"q\">${esc(it)}</div>") }
            writeHtmlMedia(m)
            when {
                m.type == TYPE_DELETED -> out.write("<span class=\"x\">${esc(m.text ?: "Deleted message")}</span>")
                !m.text.isNullOrEmpty() -> out.write(esc(m.text))
            }
            out.write("<span class=\"t\">${timeFormat.format(Date(m.timestamp))}</span>")
            m.reactions?.takeIf { it.isNotBlank() }?.let { out.write("<div class=\"r\">${esc(it)}</div>") }
            out.write("</div></div>\n")
        } else {
            if (day != lastDay) out.write("\n--- $day ---\n")
            lastDay = day
            val line = StringBuilder()
            line.append('[').append(timeFormat.format(Date(m.timestamp))).append("] ")
            if (m.type != TYPE_SYSTEM) line.append(m.sender).append(": ")
            m.quote?.takeIf { it.isNotBlank() }?.let { line.append("(> ").append(oneLine(it, 80)).append(") ") }
            mediaLabel(m)?.let { line.append('[').append(it).append("] ") }
            if (!m.text.isNullOrEmpty()) line.append(m.text)
            m.reactions?.takeIf { it.isNotBlank() }?.let { line.append("  {").append(it).append('}') }
            out.write(line.toString().trimEnd())
            out.write("\n")
        }
    }

    fun end() {
        if (html) out.write("</main></body></html>\n")
        out.flush()
    }

    private fun writeHtmlMedia(m: Message) {
        val link = m.mediaLink
        if (link == null) {
            mediaLabel(m)?.let { out.write("<div class=\"x\">[${esc(it)}]</div>") }
            return
        }
        val href = escAttr(link)
        when (m.type) {
            TYPE_IMAGE, TYPE_STICKER, TYPE_GIF, TYPE_VIEW_ONCE_IMAGE ->
                out.write("<a href=\"$href\"><img src=\"$href\" loading=\"lazy\" alt=\"\"></a>")
            TYPE_VIDEO, TYPE_VIEW_ONCE_VIDEO, TYPE_VIDEO_NOTE ->
                out.write("<video src=\"$href\" controls preload=\"none\"></video>")
            TYPE_AUDIO, TYPE_VOICE ->
                out.write("<audio src=\"$href\" controls preload=\"none\"></audio>")
            else -> out.write("<div><a href=\"$href\">${esc(m.mediaName ?: link)}</a></div>")
        }
    }

    /** One chat in the index of an "export all chats" folder. */
    class IndexEntry(val title: String, val file: String, val count: Int, val lastTimestamp: Long)

    companion object {
        /** Writes the list of exported chats, each linking to its file in the same folder. */
        @JvmStatic
        fun writeIndex(out: Writer, html: Boolean, title: String, entries: List<IndexEntry>,
                       exportedAt: Long, locale: Locale = Locale.getDefault()) {
            val full = SimpleDateFormat("dd.MM.yyyy HH:mm", locale)
            if (!html) {
                out.write("$title\n")
                out.write("Exported with WAEX, ${full.format(Date(exportedAt))}\n\n")
                for (e in entries) {
                    out.write("${e.file}  ·  ${e.title}  ·  ${e.count}  ·  ${full.format(Date(e.lastTimestamp))}\n")
                }
                out.flush()
                return
            }
            out.write("""<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>${esc(title)}</title><style>
:root{--bg:#fff;--fg:#111b21;--muted:#667781;--line:#e9edef}
@media(prefers-color-scheme:dark){:root{--bg:#111b21;--fg:#e9edef;--muted:#8696a0;--line:#222d34}}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.4 system-ui,sans-serif}
header{position:sticky;top:0;background:#008069;color:#fff;padding:12px 16px;font-weight:600}
header small{display:block;font-weight:400;opacity:.8}
main{max-width:760px;margin:0 auto}
a{display:flex;justify-content:space-between;gap:12px;padding:12px 16px;border-bottom:1px solid var(--line);color:inherit;text-decoration:none}
a span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}a small{color:var(--muted);white-space:nowrap}
</style></head><body><header>${esc(title)}<small>${entries.size} · Exported with WAEX · ${esc(full.format(Date(exportedAt)))}</small></header><main>
""")
            for (e in entries) {
                out.write("<a href=\"${escAttr(e.file)}\"><span>${esc(e.title)}</span>" +
                    "<small>${e.count} · ${esc(full.format(Date(e.lastTimestamp)))}</small></a>\n")
            }
            out.write("</main></body></html>\n")
            out.flush()
        }

        // WhatsApp message_type values.
        const val TYPE_TEXT = 0
        const val TYPE_IMAGE = 1
        const val TYPE_AUDIO = 2
        const val TYPE_VIDEO = 3
        const val TYPE_CONTACT = 4
        const val TYPE_LOCATION = 5
        const val TYPE_SYSTEM = 7
        const val TYPE_DOCUMENT = 9
        const val TYPE_GIF = 13
        const val TYPE_DELETED = 15
        const val TYPE_LIVE_LOCATION = 16
        const val TYPE_STICKER = 20
        const val TYPE_POLL = 46
        const val TYPE_VIEW_ONCE_IMAGE = 42
        const val TYPE_VIEW_ONCE_VIDEO = 43
        const val TYPE_VIDEO_NOTE = 81
        /** Not a WhatsApp value: an audio message recorded as a voice note. */
        const val TYPE_VOICE = 1002

        fun mediaLabel(m: Message): String? {
            val name = m.mediaName?.takeIf { it.isNotBlank() }
            val kind = when (m.type) {
                TYPE_TEXT, TYPE_SYSTEM, TYPE_DELETED -> return null
                TYPE_IMAGE -> "image"
                TYPE_AUDIO -> "audio"
                TYPE_VOICE -> "voice message"
                TYPE_VIDEO -> "video"
                TYPE_VIDEO_NOTE -> "video message"
                TYPE_CONTACT -> "contact"
                TYPE_LOCATION, TYPE_LIVE_LOCATION -> "location"
                TYPE_DOCUMENT -> "document"
                TYPE_GIF -> "GIF"
                TYPE_STICKER -> "sticker"
                TYPE_POLL -> "poll"
                TYPE_VIEW_ONCE_IMAGE, TYPE_VIEW_ONCE_VIDEO -> "view once"
                else -> if (name != null) "file" else return null
            }
            return if (name != null) "$kind: $name" else kind
        }

        fun esc(s: String): String {
            val b = StringBuilder(s.length + 16)
            for (c in s) {
                when (c) {
                    '<' -> b.append("&lt;")
                    '>' -> b.append("&gt;")
                    '&' -> b.append("&amp;")
                    '"' -> b.append("&quot;")
                    else -> b.append(c)
                }
            }
            return b.toString()
        }

        /** Escapes a relative link; each path segment is percent-encoded. */
        fun escAttr(path: String): String = path.split('/').joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

        private fun oneLine(s: String, max: Int): String {
            val flat = s.replace('\n', ' ').trim()
            return if (flat.length > max) flat.substring(0, max) + "…" else flat
        }
    }
}
