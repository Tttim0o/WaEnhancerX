package com.waenhancer.licensing.customprops;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Free-only stand-in: stores the list the screens edit; nothing reads it at runtime here. */
public final class CustomAbProps {
    public static final String PREF_KEY = "custom_ab_props";
    public static final String ENABLED_KEY = "custom_ab_props_enabled";

    public enum Type { BOOLEAN, INTEGER, FLOAT, STRING, JSON, LIST }
    public enum ListItemType { STRING, INTEGER, FLOAT, BOOLEAN }

    public static final class Entry {
        public final int id;
        public final Type type;
        public final String value;
        public final String note;
        public final ListItemType listItemType;

        public Entry(int id, Type type, String value, String note, ListItemType listItemType) {
            this.id = id;
            this.type = type;
            this.value = value;
            this.note = note;
            this.listItemType = listItemType;
        }
    }

    private CustomAbProps() {}

    public static List<Entry> decode(String raw) {
        List<Entry> entries = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return entries;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                entries.add(new Entry(o.getInt("id"), Type.valueOf(o.getString("type")),
                        o.optString("value"), o.optString("note"),
                        ListItemType.valueOf(o.optString("listItemType", "STRING"))));
            }
        } catch (Exception ignored) {}
        return entries;
    }

    public static String encode(List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry e : entries) {
                array.put(new JSONObject().put("id", e.id).put("type", e.type.name())
                        .put("value", e.value).put("note", e.note)
                        .put("listItemType", e.listItemType.name()));
            }
        } catch (Exception ignored) {}
        return array.toString();
    }
}
