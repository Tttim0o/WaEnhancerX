package com.waenhancer.core.transcription;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import com.waenhancer.config.PreferenceStores;

/**
 * Runs in the module process for the {@code transcribe_audio} provider call. API keys live in
 * the private store, so the hooked WhatsApp process sends audio here instead of calling the
 * transcription service itself.
 */
public final class TranscriptionRequestHandler {

    public static final String METHOD = "transcribe_audio";
    public static final String METHOD_READY = "transcription_ready";
    public static final String EXTRA_AUDIO = "audio";
    public static final String EXTRA_FILE_NAME = "file_name";
    public static final String RESULT_TEXT = "text";
    public static final String RESULT_ERROR = "error";
    public static final String RESULT_READY = "ready";

    /** One request at a time: voice notes arrive in bursts and servers rate-limit. */
    private static final Object LOCK = new Object();

    private TranscriptionRequestHandler() {}

    /** Answers {@link #METHOD_READY}: enabled, with the credentials its provider needs. */
    @NonNull
    public static Bundle ready(@NonNull Context context) {
        TranscriptionClient.Config config = configFor(context);
        boolean ready = readBoolean(context, "voice_transcription", true)
                && !config.apiKey.isEmpty()
                && (!TranscriptionClient.PROVIDER_CUSTOM.equals(config.provider) || !config.url.isEmpty());
        Bundle result = new Bundle();
        result.putBoolean(RESULT_READY, ready);
        return result;
    }

    @NonNull
    public static Bundle handle(@NonNull Context context, @NonNull Bundle extras) {
        Bundle result = new Bundle();
        if (!readBoolean(context, "voice_transcription", true)) {
            result.putString(RESULT_ERROR, "Audio transcription is turned off in WAEX");
            return result;
        }
        byte[] audio = extras.getByteArray(EXTRA_AUDIO);
        String fileName = extras.getString(EXTRA_FILE_NAME, "voice-note.opus");
        TranscriptionClient.Config config = configFor(context);
        try {
            String text;
            synchronized (LOCK) {
                text = new TranscriptionClient().transcribe(config, audio, fileName);
            }
            result.putString(RESULT_TEXT, text);
        } catch (TranscriptionClient.TranscriptionException e) {
            result.putString(RESULT_ERROR, e.getMessage());
        } catch (Exception e) {
            // Network failures: report the kind of failure, never URLs or headers.
            result.putString(RESULT_ERROR, "Network error (" + e.getClass().getSimpleName() + ")");
        }
        return result;
    }

    private static TranscriptionClient.Config configFor(Context context) {
        String provider = readString(context, "transcription_provider", TranscriptionClient.PROVIDER_GROQ);
        TranscriptionClient.Config config;
        switch (provider) {
            case TranscriptionClient.PROVIDER_ASSEMBLY:
                config = new TranscriptionClient.Config(provider, "",
                        firstNonEmpty(context, "assembly_key", "assemblyai_key"), "");
                break;
            case TranscriptionClient.PROVIDER_CUSTOM:
                config = new TranscriptionClient.Config(provider,
                        readString(context, "custom_transcription_url", ""),
                        readString(context, "custom_transcription_key", ""),
                        readString(context, "custom_transcription_model", ""));
                break;
            default:
                config = new TranscriptionClient.Config(TranscriptionClient.PROVIDER_GROQ, "",
                        firstNonEmpty(context, "groq_key", "groq_api_key"), "");
        }
        return config;
    }

    private static String firstNonEmpty(Context context, String key, String legacyKey) {
        String value = readString(context, key, "");
        return value.trim().isEmpty() ? readString(context, legacyKey, "") : value;
    }

    private static String readString(Context context, String key, String fallback) {
        Object value = PreferenceStores.read(context, key);
        return value instanceof String ? (String) value : fallback;
    }

    private static boolean readBoolean(Context context, String key, boolean fallback) {
        Object value = PreferenceStores.read(context, key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
