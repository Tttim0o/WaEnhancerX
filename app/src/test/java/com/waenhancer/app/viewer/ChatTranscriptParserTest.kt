package com.waenhancer.app.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatTranscriptParserTest {

    @Test
    fun androidExportWithContinuationAndSystemLine() {
        val chat = ChatTranscriptParser.parse("""
            12/31/23, 9:41 PM - Messages and calls are end-to-end encrypted.
            12/31/23, 9:42 PM - Ana: Hello: world
            second line
            1/1/24, 10:05 AM - Ben: Happy new year
        """.trimIndent())
        assertEquals(3, chat.messages.size)
        assertNull(chat.messages[0].sender)
        assertEquals("Ana", chat.messages[1].sender)
        assertEquals("Hello: world\nsecond line", chat.messages[1].text)
        assertEquals("9:42 PM", chat.messages[1].time)
        assertEquals("1/1/24", chat.messages[2].day)
        assertEquals(listOf("Ana", "Ben"), chat.senders.sorted())
    }

    @Test
    fun europeanAndIphoneLayouts() {
        val android = ChatTranscriptParser.parse("31.12.23, 21:41 - Ana: Ahoj")
        assertEquals("Ana", android.messages.single().sender)
        assertEquals("21:41", android.messages.single().time)

        val iphone = ChatTranscriptParser.parse("‎[31.12.23, 21:41:05] Ben: ‎<attached: 00000012-PHOTO.jpg>")
        assertEquals("Ben", iphone.messages.single().sender)
        assertEquals("<attached: 00000012-PHOTO.jpg>", iphone.messages.single().text)
    }

    @Test
    fun waexTextExport() {
        val chat = ChatTranscriptParser.parse("""
            Family
            Exported with WAEX, 01.01.2024 10:00

            --- Monday, 1 January 2024 ---
            [09:00] You: Morning
            [09:01] Mom: Hi
            and more
            [09:02] Mom added Dad
        """.trimIndent())
        assertEquals("Family", chat.title)
        assertEquals(3, chat.messages.size)
        assertEquals("Monday, 1 January 2024", chat.messages[0].day)
        assertEquals("You", chat.messages[0].sender)
        assertEquals("Hi\nand more", chat.messages[1].text)
        assertNull(chat.messages[2].sender)
    }

    @Test
    fun unrelatedTextHasNoMessages() {
        assertEquals(0, ChatTranscriptParser.parse("just some notes\nnothing else").messages.size)
    }
}
