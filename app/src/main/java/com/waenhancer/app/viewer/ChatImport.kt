package com.waenhancer.app.viewer

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** A text message read from an export, ready to be written into WhatsApp. */
class ImportMessage(val timestamp: Long, val fromMe: Boolean, val text: String)

/** Turns exports into [ImportMessage]s. Only text messages with a known time are kept. */
object ImportSource {

    private val HTML_MESSAGE = Regex("""<div class="m( me)?" data-ts="(\d+)"><div class="b">""")
    private val HTML_TEXT = Regex("""<span class="c">(.*?)</span>""", RegexOption.DOT_MATCHES_ALL)

    /** Reads a WAEX HTML export; exports made before import support have no timestamps. */
    fun fromWaexHtml(html: String): List<ImportMessage> {
        val starts = HTML_MESSAGE.findAll(html).toList()
        val result = ArrayList<ImportMessage>(starts.size)
        for ((i, start) in starts.withIndex()) {
            val end = if (i + 1 < starts.size) starts[i + 1].range.first else html.length
            val body = html.substring(start.range.last + 1, end)
            val text = HTML_TEXT.find(body)?.groupValues?.get(1)?.let(::unescape) ?: continue
            if (text.isBlank()) continue
            val ts = start.groupValues[2].toLongOrNull() ?: continue
            result.add(ImportMessage(ts, start.groupValues[1].isNotEmpty(), text))
        }
        return result
    }

    /** Reads a parsed text export; [me] is the sender name that belongs to this phone's owner. */
    fun fromTranscript(chat: ChatTranscriptParser.Chat, me: String): List<ImportMessage> {
        val dates = DateResolver(chat)
        val result = ArrayList<ImportMessage>()
        for (m in chat.messages) {
            val sender = m.sender ?: continue
            if (m.text.isBlank() || isPlaceholder(m.text)) continue
            val ts = dates.timestamp(m.day, m.time) ?: continue
            result.add(ImportMessage(ts, sender == me, m.text))
        }
        return result
    }

    /** WhatsApp writes these instead of media; they are not real text messages. */
    private fun isPlaceholder(text: String): Boolean {
        val t = text.trim()
        return (t.startsWith("<") && t.endsWith(">")) || t.endsWith("(file attached)")
    }

    fun unescape(s: String): String = s.replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")

    /** Converts the day and time strings of one export into epoch milliseconds (local time). */
    class DateResolver(chat: ChatTranscriptParser.Chat) {
        private val waex = chat.fromWaex
        private val order: Order = if (waex) Order.DMY else detectOrder(chat.messages.map { it.day })
        private val dayCache = HashMap<String, Long?>()

        fun timestamp(day: String, time: String): Long? {
            val dayStart = dayCache.getOrPut(day) { if (waex) parseWaexDay(day) else parseNumericDay(day) } ?: return null
            val seconds = parseTime(time) ?: return null
            return dayStart + seconds * 1000L
        }

        private fun parseNumericDay(day: String): Long? {
            val parts = day.split('.', '/', '-').mapNotNull { it.trim().toIntOrNull() }
            if (parts.size != 3) return null
            val (y, mo, d) = when (order) {
                Order.YMD -> Triple(parts[0], parts[1], parts[2])
                Order.MDY -> Triple(parts[2], parts[0], parts[1])
                Order.DMY -> Triple(parts[2], parts[1], parts[0])
            }
            val year = if (y < 100) 2000 + y else y
            if (mo !in 1..12 || d !in 1..31) return null
            return Calendar.getInstance().apply {
                clear()
                set(year, mo - 1, d, 0, 0, 0)
            }.timeInMillis
        }

        private fun parseWaexDay(day: String): Long? {
            for (locale in listOf(Locale.getDefault(), Locale.ENGLISH)) {
                try {
                    val format = SimpleDateFormat("EEEE, d MMMM yyyy", locale).apply { isLenient = false }
                    return format.parse(day)?.time
                } catch (_: Exception) {
                }
            }
            return null
        }
    }

    enum class Order { DMY, MDY, YMD }

    fun detectOrder(days: List<String>): Order {
        var firstOver12 = false
        var secondOver12 = false
        for (day in days.distinct()) {
            val parts = day.split('.', '/', '-')
            if (parts.size != 3) continue
            if (parts[0].length == 4) return Order.YMD
            if ((parts[0].toIntOrNull() ?: 0) > 12) firstOver12 = true
            if ((parts[1].toIntOrNull() ?: 0) > 12) secondOver12 = true
        }
        return if (secondOver12 && !firstOver12) Order.MDY else Order.DMY
    }

    /** Seconds since midnight for "21:41", "9:41 PM", "21.41.05" or "9:41 p. m.". */
    fun parseTime(time: String): Long? {
        val digits = Regex("""(\d{1,2})[:.](\d{2})(?:[:.](\d{2}))?""").find(time) ?: return null
        var h = digits.groupValues[1].toInt()
        val m = digits.groupValues[2].toInt()
        val s = digits.groupValues[3].toIntOrNull() ?: 0
        val rest = time.substring(digits.range.last + 1).lowercase(Locale.ROOT).replace(".", "").replace(" ", "")
        when {
            rest.startsWith("pm") || rest.startsWith("p") -> if (h < 12) h += 12
            rest.startsWith("am") || rest.startsWith("a") -> if (h == 12) h = 0
        }
        if (h !in 0..23 || m !in 0..59 || s !in 0..59) return null
        return h * 3600L + m * 60L + s
    }
}

/**
 * Decides where imported messages go in a chat. WhatsApp orders a chat by sort_id, so each
 * message gets a sort_id between the existing messages around its time. Messages already in
 * the chat, messages newer than the chat's last message and messages with no free sort_id
 * between their neighbours are left out.
 */
object ImportPlanner {
    class Existing(val sortId: Long, val timestamp: Long, val text: String?)
    class Planned(val message: ImportMessage, val sortId: Long)
    class Plan(
        val planned: List<Planned>,
        val duplicates: Int,
        val newer: Int,
        val noRoom: Int
    )

    fun plan(existing: List<Existing>, incoming: List<ImportMessage>): Plan {
        require(existing.isNotEmpty()) { "the chat has no messages" }
        val sorted = existing.sortedBy { it.sortId }
        // Running maximum keeps the lookup monotonic even when a few timestamps are out of order.
        val maxTs = LongArray(sorted.size)
        var running = Long.MIN_VALUE
        for ((i, e) in sorted.withIndex()) {
            running = maxOf(running, e.timestamp)
            maxTs[i] = running
        }
        val known = HashSet<String>()
        for (e in sorted) e.text?.let { known.add(key(e.timestamp, it)) }

        var duplicates = 0
        var newer = 0
        val buckets = sortedMapOf<Int, MutableList<ImportMessage>>()
        for (m in incoming.sortedBy { it.timestamp }) {
            if (!known.add(key(m.timestamp, m.text))) {
                duplicates++
                continue
            }
            val b = upperBound(maxTs, m.timestamp)
            if (b == sorted.size) {
                newer++
                continue
            }
            buckets.getOrPut(b) { ArrayList() }.add(m)
        }

        var noRoom = 0
        val planned = ArrayList<Planned>()
        for ((b, list) in buckets) {
            val k = list.size.toLong()
            val high = sorted[b].sortId
            if (b == 0) {
                if (high - k <= Long.MIN_VALUE + 1) {
                    noRoom += list.size
                    continue
                }
                list.forEachIndexed { i, m -> planned.add(Planned(m, high - k + i)) }
                continue
            }
            val low = sorted[b - 1].sortId
            val gap = high - low - 1
            if (gap < k) {
                noRoom += list.size
                continue
            }
            val step = maxOf(1L, gap / (k + 1))
            list.forEachIndexed { i, m -> planned.add(Planned(m, low + step * (i + 1))) }
        }
        return Plan(planned, duplicates, newer, noRoom)
    }

    /** Exports keep minutes only, so a message matches when the minute and text are equal. */
    private fun key(timestamp: Long, text: String) = "${timestamp / 60_000L}|${text.trim()}"

    /** Index of the first value greater than [value]. */
    private fun upperBound(values: LongArray, value: Long): Int {
        var lo = 0
        var hi = values.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (values[mid] <= value) lo = mid + 1 else hi = mid
        }
        return lo
    }
}
