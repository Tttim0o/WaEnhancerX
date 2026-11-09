package com.waenhancer.licensing.customprops;

import android.content.SharedPreferences;
import java.util.Map;

/** Free-only stand-in: implemented privately; applies no custom props here. */
public final class CustomAbPropsRuntime {
    private CustomAbPropsRuntime() {}

    public static boolean loadInto(SharedPreferences prefs, Map<Integer, Boolean> booleans,
                                   Map<Integer, Integer> integers, Map<Integer, Float> floats,
                                   Map<Integer, String> strings) {
        return false;
    }

    public static boolean shouldLogObservation(int propertyId) {
        return false;
    }
}
