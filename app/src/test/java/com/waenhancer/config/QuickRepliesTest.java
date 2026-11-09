package com.waenhancer.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QuickRepliesTest {

    @Test
    public void tokenAtStartOfText() {
        QuickReplies.Token token = QuickReplies.tokenAt("/adr", 4);
        assertEquals(0, token.start);
        assertEquals(4, token.end);
        assertEquals("adr", token.query);
    }

    @Test
    public void tokenAfterSpaceAndEmptyQuery() {
        QuickReplies.Token token = QuickReplies.tokenAt("hi /", 4);
        assertEquals(3, token.start);
        assertEquals("", token.query);
    }

    @Test
    public void noTokenInsideLinksOrDates() {
        assertNull(QuickReplies.tokenAt("https://x.sk/abc", 16));
        assertNull(QuickReplies.tokenAt("1/2", 3));
        assertNull(QuickReplies.tokenAt("/adr done", 9));
        assertNull(QuickReplies.tokenAt("", 0));
    }

    @Test
    public void shortcutsAreOneLowerCaseWord() {
        assertEquals("adresa", QuickReplies.normalizeShortcut(" /Adresa "));
        assertEquals("mojaadresa", QuickReplies.normalizeShortcut("moja adresa!"));
    }

    @Test
    public void prefixMatchesComeFirst() {
        List<QuickReplies.Item> items = Arrays.asList(
                new QuickReplies.Item("tel", "My number is 0900 adr"),
                new QuickReplies.Item("adr", "Hlavná 1, Bratislava"));
        List<QuickReplies.Item> found = QuickReplies.matches(items, "adr");
        assertEquals(2, found.size());
        assertEquals("adr", found.get(0).shortcut);
        assertEquals(2, QuickReplies.matches(items, "").size());
        assertEquals(0, QuickReplies.matches(items, "zzz").size());
    }
}
