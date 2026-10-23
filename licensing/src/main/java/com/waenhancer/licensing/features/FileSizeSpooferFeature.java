package com.waenhancer.licensing.features;

import android.content.Context;
import android.content.SharedPreferences;

/** Free-only stand-in: Pro feature, installs nothing. */
public final class FileSizeSpooferFeature {
    private FileSizeSpooferFeature() {}

    public static void install(Context context, ClassLoader classLoader, SharedPreferences prefs) {}
}
