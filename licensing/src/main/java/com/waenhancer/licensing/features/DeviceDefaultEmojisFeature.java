package com.waenhancer.licensing.features;

import android.content.Context;
import android.content.SharedPreferences;

/** Free-only stand-in: implemented privately; installs nothing here. */
public final class DeviceDefaultEmojisFeature {
    /** A DexKit class lookup; the bridge is typed loosely so this module needs no DexKit. */
    public interface ClassQuery {
        Class<?> execute(Object bridge, ClassLoader loader) throws Throwable;
    }

    public interface ClassFinder {
        Class<?> find(String cacheKey, ClassQuery query) throws Throwable;
    }

    private DeviceDefaultEmojisFeature() {}

    public static void install(Context context, ClassLoader classLoader, SharedPreferences prefs,
                               ClassFinder finder) {}
}
