package com.waenhancer.licensing.features;

import android.content.Context;
import android.content.SharedPreferences;

/** Free-only stand-in: implemented privately; installs nothing here. */
public final class PlayIntegrityBypassHook {
    private PlayIntegrityBypassHook() {}

    public static void install(Context context, ClassLoader classLoader, SharedPreferences prefs) {}
}
