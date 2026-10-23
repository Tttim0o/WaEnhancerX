package com.waenhancer.licensing.features;

import android.content.SharedPreferences;
import android.view.View;

/** Free-only stand-in: Pro color/opacity/resize filters do nothing. */
public final class FilterItemsProFeature {
    private FilterItemsProFeature() {}

    public static void applyProFilter(View target, String behavior, int color, int opacity,
                                      float scale, SharedPreferences prefs) {}
}
