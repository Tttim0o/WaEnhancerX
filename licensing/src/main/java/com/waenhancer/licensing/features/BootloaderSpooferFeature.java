package com.waenhancer.licensing.features;

import android.content.Context;
import android.content.SharedPreferences;

/** Stand-in: the real implementation is private, so this build does not spoof anything. */
public final class BootloaderSpooferFeature {
    public static final String ENABLED = "bootloader_spoofer";
    public static final String CUSTOM_ENABLED = "bootloader_spoofer_custom";
    public static final String CUSTOM_XML = "bootloader_spoofer_custom_xml";
    public static final String DEFAULT_XML = "bootloader_spoofer_default_xml";
    public static final String LAST_UPDATED = "bootloader_spoofer_last_updated";
    public static final String LAST_ERROR = "bootloader_spoofer_last_error";

    private static final String UNAVAILABLE = "Not available in this local test build.";

    public interface SyncCallback {
        void onResult(boolean success, String detail);
    }

    public static final class VerificationReport {
        public String source = "Local test build";
        public String error = UNAVAILABLE;
        public int totalScore, runtimeScore, ecScore, rsaScore, integrityScore;
        public boolean recommended, featureEnabled, hookActive;
        public boolean ecPresent, ecChainValid, ecKeyMatches, ecExpired;
        public boolean rsaPresent, rsaChainValid, rsaKeyMatches, rsaExpired;
        public boolean basicIntegrity, deviceIntegrity, strongIntegritySupported;
    }

    private BootloaderSpooferFeature() {}

    public static void install(Context context, ClassLoader classLoader, SharedPreferences prefs) {}

    public static void syncDefaultKeyboxAsync(Context context) {}

    public static void syncDefaultKeyboxAsync(Context context, SyncCallback callback) {
        if (callback != null) callback.onResult(false, UNAVAILABLE);
    }

    public static Object parse(String xml) {
        throw new IllegalStateException(UNAVAILABLE);
    }

    public static VerificationReport verify(Context context, String xml, boolean managed) {
        return new VerificationReport();
    }
}
