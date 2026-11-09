package com.waenhancer.app.viewer

/**
 * Reads a chat exported as text: WhatsApp's own "Export chat" (Android and iPhone layouts, any
 * date order) or a WAEX TXT export. Lines that do not start a message continue the previous one.
 */
object ChatTranscriptParser {

    class Message(
        /** Date as written in the file; messages on the same day share it. */
        val day: String,
        val time: String,
        /** Null for system lines such as "Messages are end-to-end encrypted". */
        val sender: String?,
        val text: String
    )

    class Chat(val title: String?, val messages: List<Message>, val fromWaex: Boolean = false) {
        /** Senders, most active first. */
        val senders: List<String>
            get() = messages.mapNotNull { it.sender }.groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }
    }

    private const val TIME = """\d{1,2}[:.]\d{2}(?:[:.]\d{2})?(?:[\s  ]?[AaPp]\.?\s?[Mm]\.?)?"""
    private const val DATE = """\d{1,4}[./-]\d{1,2}[./-]\d{1,4}"""
    private val ANDROID = Regex("""^($DATE),?\s($TIME)\s[-–]\s(.*)$""")
    private val IPHONE = Regex("""^\[($DATE),?\s($TIME)]\s(.*)$""")
    private val WAEX_DAY = Regex("""^--- (.+) ---$""")
    private val WAEX_LINE = Regex("""^\[(\d{1,2}:\d{2})]\s(.*)$""")
    private const val WAEX_STAMP = "Exported with WAEX"
    // Direction marks WhatsApp puts before dates and attachment names.
    private val MARKS = Regex("[‎‏‪-‮⁦-⁩﻿]")

    fun parse(raw: String): Chat {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { it.replace(MARKS, "") }
        val waex = lines.size > 1 && lines[1].startsWith(WAEX_STAMP)
        val title = if (waex) lines[0].trim().ifEmpty { null } else null
        val messages = ArrayList<Message>()
        var day = ""
        val pending = StringBuilder()
        var head: Triple<String, String, String>? = null // day, time, "sender: text" or text

        fun flush() {
            val h = head ?: return
            messages.add(split(h.first, h.second, pending.toString()))
            head = null
            pending.setLength(0)
        }

        for ((index, line) in lines.withIndex()) {
            if (waex && index < 2) continue
            if (waex) {
                val dayMatch = WAEX_DAY.matchEntire(line)
                if (dayMatch != null) {
                    flush()
                    day = dayMatch.groupValues[1]
                    continue
                }
                val m = WAEX_LINE.matchEntire(line)
                if (m != null) {
                    flush()
                    head = Triple(day, m.groupValues[1], "")
                    pending.append(m.groupValues[2])
                    continue
                }
            } else {
                val m = IPHONE.matchEntire(line) ?: ANDROID.matchEntire(line)
                if (m != null) {
                    flush()
                    head = Triple(m.groupValues[1], m.groupValues[2], "")
                    pending.append(m.groupValues[3])
                    continue
                }
            }
            if (head != null) {
                pending.append('\n').append(line)
            }
        }
        flush()
        return Chat(title, messages.map { it.trimmed() }, waex)
    }

    /** "Name: text" becomes a message from Name; anything else is a system line. */
    private fun split(day: String, time: String, body: String): Message {
        val colon = body.indexOf(": ")
        val firstLineEnd = body.indexOf('\n').let { if (it < 0) body.length else it }
        if (colon in 1 until firstLineEnd && colon <= 60) {
            return Message(day, time, body.substring(0, colon).trim(), body.substring(colon + 2))
        }
        return Message(day, time, null, body)
    }

    private fun Message.trimmed() = Message(day, time, sender, text.trimEnd())
}
