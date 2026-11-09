package com.waenhancer.xposed.features.conversation

import java.io.StringWriter
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatExportWriterTest {

    @Before
    fun utc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    private fun message(
        text: String?,
        type: Int = ChatExportWriter.TYPE_TEXT,
        fromMe: Boolean = false,
        mediaName: String? = null,
        mediaLink: String? = null
    ) = ChatExportWriter.Message(1_700_000_000_000L, fromMe, "Jana", type, text, null, mediaName, mediaLink, null)

    @Test
    fun htmlEscapesMessageText() {
        val out = StringWriter()
        val writer = ChatExportWriter(out, html = true, title = "A & B", isGroup = true, locale = Locale.ROOT)
        writer.begin(0)
        writer.add(message("<script>alert(1)</script>"))
        writer.end()
        val html = out.toString()
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("<title>A &amp; B</title>"))
        assertTrue(html.contains("<div class=\"s\">Jana</div>"))
        assertEquals(1, writer.count)
    }

    @Test
    fun textLineHasTimeSenderAndMediaMarker() {
        val out = StringWriter()
        val writer = ChatExportWriter(out, html = false, title = "Jana", isGroup = false, locale = Locale.ROOT)
        writer.add(message("look", ChatExportWriter.TYPE_IMAGE, mediaName = "IMG-1.jpg"))
        writer.end()
        assertTrue(out.toString().contains("[22:13] Jana: [image: IMG-1.jpg] look"))
    }

    @Test
    fun mediaLinksArePercentEncoded() {
        assertEquals("media/My%20file%23.pdf", ChatExportWriter.escAttr("media/My file#.pdf"))
    }

    @Test
    fun voiceNotesAreLabelled() {
        assertEquals("voice message", ChatExportWriter.mediaLabel(message(null, ChatExportWriter.TYPE_VOICE)))
        assertEquals(null, ChatExportWriter.mediaLabel(message("hi")))
    }

    @Test
    fun indexLinksEveryChat() {
        val out = StringWriter()
        ChatExportWriter.writeIndex(out, true, "All chats", listOf(
            ChatExportWriter.IndexEntry("Ana & Ben", "001_Ana_Ben.html", 12, 0L),
            ChatExportWriter.IndexEntry("Team", "002_Team.html", 3, 0L)
        ), 0L, Locale.ROOT)
        val html = out.toString()
        assertTrue(html.contains("href=\"001_Ana_Ben.html\""))
        assertTrue(html.contains("Ana &amp; Ben"))
        assertTrue(html.contains("href=\"002_Team.html\""))

        val txt = StringWriter()
        ChatExportWriter.writeIndex(txt, false, "All chats", listOf(
            ChatExportWriter.IndexEntry("Team", "002_Team.txt", 3, 0L)), 0L, Locale.ROOT)
        assertTrue(txt.toString().contains("002_Team.txt  ·  Team  ·  3"))
    }
}
