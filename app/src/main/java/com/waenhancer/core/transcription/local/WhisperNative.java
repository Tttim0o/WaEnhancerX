package com.waenhancer.core.transcription.local;

import android.os.Build;
import androidx.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/**
 * whisper.cpp through JNI ({@code libwaexwhisper.so}). Only ever loaded in the WAEX app
 * process, never inside WhatsApp.
 */
public final class WhisperNative {

    private static volatile Boolean sSupported;
    private static volatile String sUnsupportedReason;

    private WhisperNative() {}

    static native long init(String modelPath);

    static native void free(long handle);

    @Nullable
    static native byte[] transcribe(long handle, float[] samples16kMono, String language, int threads);

    static native String systemInfo();

    /**
     * True when this phone can run the bundled library: 64-bit ARM with the dot-product and
     * fp16 instructions it was compiled for. Checked before loading it, because running it on
     * an older CPU would end in an illegal-instruction crash instead of an exception.
     */
    public static synchronized boolean isSupported() {
        if (sSupported != null) return sSupported;
        boolean arm64 = false;
        for (String abi : Build.SUPPORTED_ABIS) {
            if ("arm64-v8a".equals(abi)) arm64 = true;
        }
        if (!arm64) return unsupported("needs a 64-bit ARM phone");
        String features = cpuFeatures();
        if (features == null) return unsupported("CPU features unknown");
        if (!features.contains(" asimddp ") || !(features.contains(" asimdhp ") || features.contains(" fphp "))) {
            return unsupported("CPU is too old (no dot-product instructions)");
        }
        try {
            System.loadLibrary("waexwhisper");
        } catch (Throwable t) {
            return unsupported("native library missing");
        }
        sSupported = true;
        return true;
    }

    /** Why {@link #isSupported()} returned false, for the settings screen. */
    @Nullable
    public static String unsupportedReason() {
        isSupported();
        return sUnsupportedReason;
    }

    private static boolean unsupported(String reason) {
        sUnsupportedReason = reason;
        sSupported = false;
        return false;
    }

    /** The "Features" line of /proc/cpuinfo, padded with spaces so whole words can be matched. */
    @Nullable
    private static String cpuFeatures() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase(Locale.ROOT).startsWith("features")) {
                    int colon = line.indexOf(':');
                    return " " + line.substring(colon + 1).trim().toLowerCase(Locale.ROOT) + " ";
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
