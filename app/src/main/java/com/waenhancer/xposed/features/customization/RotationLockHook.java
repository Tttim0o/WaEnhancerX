package com.waenhancer.xposed.features.customization;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import androidx.annotation.NonNull;
import com.waenhancer.xposed.core.BaseFeature;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Keeps WhatsApp from rotating while the system rotation lock is on.
 *
 * <p>The "sensor" orientations rotate with the device even when auto-rotate is off, which
 * looks as if auto-rotate switched itself on. Each is mapped to its "user" equivalent, which
 * rotates only when the user allows it. For the first minute after start, a real change of the
 * system setting is also logged, to tell the two causes apart.</p>
 */
public final class RotationLockHook extends BaseFeature {
    private static final String TAG = "[WAEX][Rotation]";
    static final String PREF_KEY = "respect_rotation_lock";
    private static final long WATCH_SYSTEM_SETTING_MS = 60_000;

    public RotationLockHook(@NonNull Context context, @NonNull ClassLoader classLoader,
                            @NonNull SharedPreferences prefs) {
        super(context, classLoader, prefs);
    }

    @NonNull @Override public String getName() { return "Respect Rotation Lock"; }

    @Override
    public void hook() {
        if (!isEnabled(PREF_KEY, true)) return;
        XposedHelpers.findAndHookMethod(Activity.class, "setRequestedOrientation", int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!(param.args[0] instanceof Integer)) return;
                int requested = (Integer) param.args[0];
                int respected = respectingRotationLock(requested);
                if (respected != requested) {
                    param.args[0] = respected;
                    log(param.thisObject, requested, respected);
                }
            }
        });
        // Orientations declared in WhatsApp's manifest never pass through the call above.
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", android.os.Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    Activity activity = (Activity) param.thisObject;
                    int declared = activity.getRequestedOrientation();
                    if (respectingRotationLock(declared) != declared) activity.setRequestedOrientation(declared);
                } catch (Throwable ignored) {
                    // Orientation is cosmetic: never break an Activity's creation over it.
                }
            }
        });
        watchSystemSetting();
    }

    /** Sensor-driven orientations that ignore the rotation lock, mapped to ones that obey it. */
    static int respectingRotationLock(int orientation) {
        switch (orientation) {
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR:
                return ActivityInfo.SCREEN_ORIENTATION_USER;
            case ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR:
                return ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                return ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
            case ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT:
                return ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
            default:
                return orientation;
        }
    }

    private static void log(Object activity, int requested, int respected) {
        XposedBridge.log(TAG + " " + activity.getClass().getSimpleName() + " asked for orientation "
                + requested + "; using " + respected + " so the rotation lock is respected");
    }

    /** Logs if the system auto-rotate setting itself changes shortly after WhatsApp starts. */
    private void watchSystemSetting() {
        try {
            long started = SystemClock.elapsedRealtime();
            int initial = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, -1);
            Handler main = new Handler(Looper.getMainLooper());
            ContentObserver observer = new ContentObserver(main) {
                @Override
                public void onChange(boolean selfChange) {
                    int now = Settings.System.getInt(context.getContentResolver(),
                            Settings.System.ACCELEROMETER_ROTATION, -1);
                    XposedBridge.log(TAG + " System auto-rotate changed " + initial + " -> " + now + " "
                            + (SystemClock.elapsedRealtime() - started) + " ms after WhatsApp started");
                }
            };
            context.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, observer);
            main.postDelayed(() -> {
                try {
                    context.getContentResolver().unregisterContentObserver(observer);
                } catch (Throwable ignored) {}
            }, WATCH_SYSTEM_SETTING_MS);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " Rotation setting watch unavailable: " + t.getClass().getSimpleName());
        }
    }
}
