package com.waenhancer.licensing.features;

import android.content.SharedPreferences;

/** Free-only stand-in: installs nothing and reports itself disabled. */
public final class NewChatFeature {
    private NewChatFeature() {}

    public static boolean install(ClassLoader classLoader, SharedPreferences prefs) { return false; }
}
