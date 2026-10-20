package com.waenhancer.xposed.features.media;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.waenhancer.core.transcription.TranscriptionClient;
import com.waenhancer.core.transcription.TranscriptionRequestHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hook-side access to the WAEX transcription provider, shared by the notification, chat-row
 * and dictation features. Blocking calls must run on {@link #worker()}, never the UI thread.
 */
final class VoiceTranscriptionBridge {

    private static final Uri PROVIDER = Uri.parse("content://com.waenhancer.hookprovider");
    private static final long READY_CACHE_MS = 60_000;
    private static final int REMEMBERED_TRANSCRIPTS = 256;

    private static Handler sWorker;
    private static long sReadyCheckedAt;
    private static volatile boolean sReady;

    /** Path + size -> transcript, so a note is sent to the provider once per process. */
    private static final Map<String, String> TRANSCRIPTS =
            new LinkedHashMap<String, String>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > REMEMBERED_TRANSCRIPTS;
                }
            };

    /** Outcome of one request: exactly one of {@code text} and {@code error} is set. */
    static final class Result {
        @Nullable final String text;
        @Nullable final String error;

        Result(@Nullable String text, @Nullable String error) {
            this.text = text;
            this.error = error;
        }
    }

    private VoiceTranscriptionBridge() {}

    /** One background thread for all transcription work in this process. */
    static synchronized Handler worker() {
        if (sWorker == null) {
            HandlerThread thread = new HandlerThread("WAEX-Transcribe", Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();
            sWorker = new Handler(thread.getLooper());
        }
        return sWorker;
    }

    static Handler main() {
        return new Handler(Looper.getMainLooper());
    }

    /**
     * Whether WAEX has a provider with a key; {@code null} when WAEX could not be asked.
     * Blocking: call on the worker.
     */
    @Nullable
    static synchronized Boolean ready(@NonNull Context context) {
        long now = System.currentTimeMillis();
        if (sReadyCheckedAt != 0 && now - sReadyCheckedAt < READY_CACHE_MS) return sReady;
        try {
            Bundle result = context.getContentResolver().call(
                    PROVIDER, TranscriptionRequestHandler.METHOD_READY, null, new Bundle());
            sReady = result != null && result.getBoolean(TranscriptionRequestHandler.RESULT_READY, false);
            sReadyCheckedAt = now; // Only a real answer is cached; WAEX may still be starting.
            return sReady;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Last known readiness without IPC or locking; safe on the UI thread. */
    static boolean lastKnownReady() {
        return sReady;
    }

    @Nullable
    static String cached(@NonNull File audio) {
        synchronized (TRANSCRIPTS) {
            return TRANSCRIPTS.get(cacheKey(audio));
        }
    }

    /** Blocking: call on the worker. */
    @NonNull
    static Result transcribe(@NonNull Context context, @NonNull File audio) {
        String key = cacheKey(audio);
        synchronized (TRANSCRIPTS) {
            String known = TRANSCRIPTS.get(key);
            if (known != null) return new Result(known, null);
        }
        if (audio.length() > TranscriptionClient.MAX_AUDIO_BYTES) {
            return new Result(null, "Voice note is too long to transcribe.");
        }
        Result result;
        try {
            result = transcribeBytes(context, readFile(audio), audio.getName());
        } catch (IOException e) {
            return new Result(null, "Could not read the voice note");
        }
        // An empty result may come from a note read before WhatsApp finished writing it; never
        // cache it, so the next tap tries again.
        if (result.text != null && !result.text.isEmpty()) {
            synchronized (TRANSCRIPTS) {
                TRANSCRIPTS.put(key, result.text);
            }
        }
        return result;
    }

    /** Blocking: call on the worker. An empty transcript means no speech was found. */
    @NonNull
    static Result transcribeBytes(@NonNull Context context, @NonNull byte[] audio, @NonNull String name) {
        try {
            Bundle extras = new Bundle();
            extras.putByteArray(TranscriptionRequestHandler.EXTRA_AUDIO, audio);
            extras.putString(TranscriptionRequestHandler.EXTRA_FILE_NAME, name);
            Bundle result = context.getContentResolver().call(
                    PROVIDER, TranscriptionRequestHandler.METHOD, null, extras);
            if (result == null) return new Result(null, "WAEX did not answer");
            String text = result.getString(TranscriptionRequestHandler.RESULT_TEXT);
            if (text != null) return new Result(text, null);
            String error = result.getString(TranscriptionRequestHandler.RESULT_ERROR);
            return new Result(null, error == null ? "Unknown error" : error);
        } catch (Throwable t) {
            return new Result(null, "WAEX unavailable (" + t.getClass().getSimpleName() + ")");
        }
    }

    private static String cacheKey(File audio) {
        // WhatsApp can fill a pre-sized file in place, so the size alone does not identify it.
        return audio.getAbsolutePath() + "#" + audio.length() + "#" + audio.lastModified();
    }

    /** "(23 KB, 4 s)": shown with empty results, to tell silence from a broken recording. */
    @NonNull
    static String describeAudio(@NonNull File audio) {
        long kb = Math.max(1, audio.length() / 1024);
        long seconds = -1;
        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
        try {
            retriever.setDataSource(audio.getAbsolutePath());
            String ms = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (ms != null) seconds = Math.round(Long.parseLong(ms) / 1000.0);
        } catch (Throwable ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {}
        }
        return seconds >= 0 ? "(" + kb + " KB, " + seconds + " s)" : "(" + kb + " KB)";
    }

    static byte[] readFile(File file) throws IOException {
        long length = file.length();
        if (length > TranscriptionClient.MAX_AUDIO_BYTES) throw new IOException("Too large");
        byte[] data = new byte[(int) length];
        try (FileInputStream in = new FileInputStream(file)) {
            int offset = 0;
            while (offset < data.length) {
                int read = in.read(data, offset, data.length - offset);
                if (read < 0) break;
                offset += read;
            }
            return offset == data.length ? data : Arrays.copyOf(data, offset);
        }
    }
}
