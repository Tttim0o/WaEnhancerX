package com.waenhancer.licensing;

import android.content.Context;
import android.content.SharedPreferences;

/** Free-only stand-in: never reports an active license. */
public final class LicenseManager {
    private static final String UNAVAILABLE = "Licensing is not available in this local test build.";

    public interface LicenseCallback {
        void onSuccess(String planName, String expiresAtStr, String tgUsername);
        void onError(String message);
    }

    public interface UnlinkCallback {
        void onSuccess();
        void onError(String msg);
    }

    public interface DevicesCallback {
        void onSuccess(int linked, int limit, String json);
        void onError(String msg);
    }

    private LicenseManager() {}

    public static String getProStatus(Context context) { return "INACTIVE"; }

    public static String getProPlanName(Context context) { return ""; }

    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("waex_license_stub", Context.MODE_PRIVATE);
    }

    public static void silentCheck(Context context, Runnable onDone) {
        if (onDone != null) onDone.run();
    }

    public static boolean isValidLicensePattern(String key) { return false; }

    public static void verifyLicense(Context context, String key, LicenseCallback callback) {
        callback.onError(UNAVAILABLE);
    }

    public static void unlinkDevice(Context context, UnlinkCallback callback) {
        callback.onError(UNAVAILABLE);
    }

    public static void unlinkSpecificDevice(Context context, String deviceId, String devicePubKey,
                                            UnlinkCallback callback) {
        callback.onError(UNAVAILABLE);
    }

    public static void fetchConnectedDevices(Context context, DevicesCallback callback) {
        callback.onError(UNAVAILABLE);
    }
}
