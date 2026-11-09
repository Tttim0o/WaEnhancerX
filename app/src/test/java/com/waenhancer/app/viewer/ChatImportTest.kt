package com.waenhancer.app.viewer

import com.waenhancer.xposed.features.conversation.ChatExportWriter
import java.io.StringWriter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatImportTest {

    @Before
    fun utc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.ENGLISH)
    }

    private fun existing(vararg rows: Triple<Long, Long, String?>) =
        rows.map { ImportPlanner.Existing(it.first, it.second, it.third) }

    @Test
    fun placesMessagesBetweenNeighboursAndSkipsDuplicates() {
        val chat = existing(
            Triple(100L, 1_000_000L, "a"),
            Triple(200L, 2_000_000L, "b"),
            Triple(300L, 3_000_000L, "c")
        )
        val plan = ImportPlanner.plan(chat, listOf(
            ImportMessage(500_000L, false, "older"),
            ImportMessage(1_500_000L, true, "middle 1"),
            ImportMessage(1_600_000L, false, "middle 2"),
            ImportMessage(2_000_000L, false, "b"),
            ImportMessage(9_000_000L, false, "newer")
        ))
        assertEquals(1, plan.duplicates)
        assertEquals(1, plan.newer)
        assertEquals(0, plan.noRoom)
        val ids = plan.planned.associate { it.message.text to it.sortId }
        assertTrue(ids.getValue("older") < 100L)
        assertTrue(ids.getValue("middle 1") in 101L..199L)
        assertTrue(ids.getValue("middle 2") in 101L..199L)
        assertTrue(ids.getValue("middle 1") < ids.getValue("middle 2"))
    }

    @Test
    fun noRoomWhenNeighboursAreAdjacent() {
        val chat = existing(Triple(10L, 1_000L, null), Triple(11L, 5_000_000L, null))
        val plan = ImportPlanner.plan(chat, listOf(ImportMessage(2_000_000L, false, "x")))
        assertEquals(1, plan.noRoom)
        assertEquals(0, plan.planned.size)
    }

    @Test
    fun readsWaexHtmlExport() {
        val out = StringWriter()
        val writer = ChatExportWriter(out, true, "Ana", false, Locale.ROOT)
        writer.begin(0L)
        writer.add(ChatExportWriter.Message(60_000L, true, "You", ChatExportWriter.TYPE_TEXT,
            "Hi <b> & \"you\"\nline 2", null, null, null, null))
        writer.add(ChatExportWriter.Message(120_000L, false, "Ana", ChatExportWriter.TYPE_IMAGE,
            null, null, "IMG.jpg", null, null))
        writer.add(ChatExportWriter.Message(180_000L, false, "Ana", ChatExportWriter.TYPE_TEXT,
            "Hello", "quoted", null, null, "❤️"))
        writer.end()
        val messages = ImportSource.fromWaexHtml(out.toString())
        assertEquals(2, messages.size)
        assertEquals("Hi <b> & \"you\"\nline 2", messages[0].text)
        assertTrue(messages[0].fromMe)
        assertEquals(180_000L, messages[1].timestamp)
        assertEquals("Hello", messages[1].text)
    }

    @Test
    fun resolvesWhatsAppTextDates() {
        val chat = ChatTranscriptParser.parse("""
            12/31/23, 9:41 PM - Ana: one
            1/1/24, 12:05 AM - Me: two
            1/1/24, 10:05 AM - Ana: <Media omitted>
        """.trimIndent())
        val messages = ImportSource.fromTranscript(chat, "Me")
        assertEquals(2, messages.size)
        val expected = Calendar.getInstance().apply { clear(); set(2023, 11, 31, 21, 41, 0) }.timeInMillis
        assertEquals(expected, messages[0].timestamp)
        assertTrue(messages[1].fromMe)
        val midnight = Calendar.getInstance().apply { clear(); set(2024, 0, 1, 0, 5, 0) }.timeInMillis
        assertEquals(midnight, messages[1].timestamp)
    }

    @Test
    fun detectsDateOrder() {
        assertEquals(ImportSource.Order.DMY, ImportSource.detectOrder(listOf("31.12.23", "1.1.24")))
        assertEquals(ImportSource.Order.MDY, ImportSource.detectOrder(listOf("12/31/23")))
        assertEquals(ImportSource.Order.YMD, ImportSource.detectOrder(listOf("2023-12-31")))
        assertEquals(21 * 3600L + 41 * 60L, ImportSource.parseTime("9:41 p. m."))
    }
}
