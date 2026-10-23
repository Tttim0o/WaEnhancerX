package com.waenhancer.hooking;

import android.app.Activity;

/** Free-only stand-in: a feature that drops per-screen state when its screen is destroyed. */
public interface ScreenScopedFeature {
    void onScreenDisposed(Activity activity);
}
