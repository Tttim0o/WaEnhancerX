package com.waenhancer.licensing.customprops;

/** Free-only stand-in: the preference access the custom props screens need. */
public interface CustomAbPropsStore {
    boolean getBoolean(String key, boolean defaultValue);
    void putBoolean(String key, boolean value);
    String getString(String key, String defaultValue);
    void putString(String key, String value);
}
