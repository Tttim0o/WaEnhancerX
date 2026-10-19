package com.waenhancer.licensing;

import android.content.Context;

/** Free-only stand-in: no Pro feature is ever entitled. */
public final class ProFeatureGate {
    public static final String AUDIO_TO_VOICE_STATUS = "audio_to_voice_status";
    public static final String MESSAGE_BOMBER = "message_bomber";
    public static final String STATUS_SPLITTER = "status_video_splitter";
    public static final String FILE_SIZE_SPOOFER = "file_size_spoofer";
    public static final String FILTER_ITEMS = "filter_items";
    public static final String UNLOCK_WA_PLUS = "unlock_wa_plus";

    public static final String AUDIO_TO_VOICE_STATUS_PREF = AUDIO_TO_VOICE_STATUS;
    public static final String MESSAGE_BOMBER_PREF = MESSAGE_BOMBER;
    public static final String STATUS_SPLITTER_PREF = STATUS_SPLITTER;
    public static final String FILE_SIZE_SPOOFER_PREF = FILE_SIZE_SPOOFER;
    public static final String UNLOCK_WA_PLUS_PREF = UNLOCK_WA_PLUS;

    public static final String AUDIO_TO_VOICE_STATUS_ENTITLEMENT = "pro_entitlement_" + AUDIO_TO_VOICE_STATUS;
    public static final String MESSAGE_BOMBER_ENTITLEMENT = "pro_entitlement_" + MESSAGE_BOMBER;
    public static final String STATUS_SPLITTER_ENTITLEMENT = "pro_entitlement_" + STATUS_SPLITTER;
    public static final String FILE_SIZE_SPOOFER_ENTITLEMENT = "pro_entitlement_" + FILE_SIZE_SPOOFER;
    public static final String FILTER_ITEMS_ENTITLEMENT = "pro_entitlement_" + FILTER_ITEMS;
    public static final String UNLOCK_WA_PLUS_ENTITLEMENT = "pro_entitlement_" + UNLOCK_WA_PLUS;

    private ProFeatureGate() {}

    public static boolean isEntitled(Context context, String feature) { return false; }
}
