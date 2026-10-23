package com.waenhancer.xposed.features.media;

/** Android-independent policy for the one sensor we are allowed to override. */
final class ProximityEventPolicy {
    // android.hardware.Sensor.TYPE_PROXIMITY, stable across Android API levels.
    private static final int PROXIMITY_TYPE = 8;

    private ProximityEventPolicy() {}

    /** PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK and PARTIAL_WAKE_LOCK; level bits are the low 16. */
    static final int PROXIMITY_SCREEN_OFF_WAKE_LOCK = 32;
    static final int PARTIAL_WAKE_LOCK = 1;
    private static final int WAKE_LOCK_LEVEL_MASK = 0x0000ffff;

    /**
     * A proximity screen-off lock becomes a plain partial lock (no flags: ACQUIRE_CAUSES_WAKEUP
     * is not valid on it); every other lock is returned unchanged.
     */
    static int withoutProximityScreenOff(int levelAndFlags) {
        return (levelAndFlags & WAKE_LOCK_LEVEL_MASK) == PROXIMITY_SCREEN_OFF_WAKE_LOCK
                ? PARTIAL_WAKE_LOCK : levelAndFlags;
    }

    static boolean replaceNearWithFar(int sensorType, float maximumRange, float[] values) {
        if (sensorType != PROXIMITY_TYPE || values == null || values.length == 0
                || !Float.isFinite(maximumRange) || maximumRange <= 0f) return false;
        values[0] = maximumRange;
        return true;
    }
}
