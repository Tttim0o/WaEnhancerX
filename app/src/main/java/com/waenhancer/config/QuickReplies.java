package com.waenhancer.config;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Saved replies the message field offers after "/". Stored as a JSON array in the
 * {@link #PREF_KEY} preference, which the WhatsApp hook reads and the WAEX app edits.
 */
public final class QuickReplies {
    public static final String PREF_KEY = "quick_replies";
    public static final String ENABLED_KEY = "quick_replies_enabled";
    /** Most suggestions shown at once. */
    public static final int MAX_SUGGESTIONS = 6;

    public static final class Item {
        @NonNull public final String shortcut;
        @NonNull public final String text;

        public Item(@NonNull String shortcut, @NonNull String text) {
            this.shortcut = normalizeShortcut(shortcut);
            this.text = text;
        }
    }

    /** The "/word" being typed just before the cursor. */
    public static final class Token {
        public final int start;
        public final int end;
        @NonNull public final String query;

        Token(int start, int end, @NonNull String query) {
            this.start = start;
            this.end = end;
            this.query = query;
        }
    }

    private QuickReplies() {}

    /** Lower case letters, digits, "_" and "-" only, so a shortcut is always one typed word. */
    @NonNull
    public static String normalizeShortcut(@Nullable String shortcut) {
        if (shortcut == null) return "";
        String s = shortcut.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("/")) s = s.substring(1);
        return s.replaceAll("[^\\p{L}\\p{N}_-]", "");
    }

    @NonNull
    public static List<Item> decode(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        List<Item> items = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                Item item = new Item(o.optString("s"), o.optString("t"));
                if (!item.shortcut.isEmpty() && !item.text.isEmpty()) items.add(item);
            }
        } catch (Exception ignored) {
            // A damaged value offers nothing instead of breaking the message field.
        }
        return items;
    }

    @NonNull
    public static String encode(@NonNull List<Item> items) {
        JSONArray array = new JSONArray();
        for (Item item : items) {
            try {
                array.put(new JSONObject().put("s", item.shortcut).put("t", item.text));
            } catch (Exception ignored) {}
        }
        return array.toString();
    }

    /**
     * Finds a "/word" that ends at the cursor and starts the text or follows whitespace, so
     * links and dates such as "1/2" never open suggestions.
     */
    @Nullable
    public static Token tokenAt(@NonNull CharSequence text, int cursor) {
        if (cursor < 1 || cursor > text.length()) return null;
        int i = cursor - 1;
        while (i >= 0 && !Character.isWhitespace(text.charAt(i)) && text.charAt(i) != '/') i--;
        if (i < 0 || text.charAt(i) != '/') return null;
        if (i > 0 && !Character.isWhitespace(text.charAt(i - 1))) return null;
        String query = text.subSequence(i + 1, cursor).toString();
        if (query.length() > 32) return null;
        return new Token(i, cursor, normalizeShortcut(query));
    }

    /** Shortcuts starting with the query first, then other shortcuts or texts containing it. */
    @NonNull
    public static List<Item> matches(@NonNull List<Item> items, @NonNull String query) {
        if (items.isEmpty()) return Collections.emptyList();
        String q = query.toLowerCase(Locale.ROOT);
        List<Item> prefix = new ArrayList<>();
        List<Item> other = new ArrayList<>();
        for (Item item : items) {
            if (item.shortcut.startsWith(q)) {
                prefix.add(item);
            } else if (!q.isEmpty() && (item.shortcut.contains(q)
                    || item.text.toLowerCase(Locale.ROOT).contains(q))) {
                other.add(item);
            }
        }
        prefix.addAll(other);
        return prefix.size() > MAX_SUGGESTIONS ? prefix.subList(0, MAX_SUGGESTIONS) : prefix;
    }
}
