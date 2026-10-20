package com.waenhancer.xposed.core;

import com.waenhancer.app.R;
import com.waenhancer.utils.UiText;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.waenhancer.xposed.core.components.WaexBottomSheet;
import com.waenhancer.xposed.utils.ActivityTracker;
import de.robv.android.xposed.XposedBridge;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Remembers the last host crash while the module is active and offers its details on the next
 * start. Without this, a crash inside WhatsApp leaves the user nothing to report.
 *
 * <p>The report stays in the host's private files directory and is only shown to the user,
 * who decides whether to copy it.</p>
 */
public final class CrashRecorder {
    private static final String FILE_NAME = "waex_last_crash.txt";
    private static final long MAX_REPORT_AGE_MS = 24L * 60 * 60 * 1000;
    private static final int MAX_REPORT_CHARS = 16_000;
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);

    private CrashRecorder() {}

    /** Wraps the host's handler (installed in its Application.onCreate, which ran before us). */
    public static void install(@NonNull Context context) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                // Every crash is kept: a hook can also break host code that then crashes on its
                // own, with no module frame in the trace.
                write(app, thread, error);
            } catch (Throwable ignored) {
                // Never interfere with the host's own crash handling.
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** Shows the last recorded crash once, on the next visible Activity. */
    public static void offerPendingReport(@NonNull Context context) {
        String report = takeReport(context);
        if (report == null) return;
        ActivityTracker.addListener(new ActivityTracker.ActivityListener() {
            @Override
            public void onActivityResumed(@NonNull Activity activity) {
                ActivityTracker.removeListener(this);
                activity.runOnUiThread(() -> show(activity, report));
            }
        });
    }

    static boolean involvesHooks(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            for (StackTraceElement frame : t.getStackTrace()) {
                String name = frame.getClassName();
                if (name.startsWith("com.waenhancer") || name.startsWith("de.robv.android.xposed")
                        || name.contains("LSPHooker") || name.contains("lsposed")) {
                    return true;
                }
            }
            if (t.getCause() == t) break;
        }
        return false;
    }

    private static void write(Context context, Thread thread, Throwable error) throws IOException {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        String text = "WaEnhancer X crash report\n"
                + "Time: " + new Date() + "\n"
                + "Package: " + context.getPackageName() + "\n"
                + "Thread: " + thread.getName() + "\n"
                + "Hook frames in trace: " + (involvesHooks(error) ? "yes" : "no") + "\n\n"
                + trace;
        if (text.length() > MAX_REPORT_CHARS) text = text.substring(0, MAX_REPORT_CHARS) + "\n…";
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), FILE_NAME))) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Nullable
    private static String takeReport(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (!file.isFile()) return null;
        try {
            if (System.currentTimeMillis() - file.lastModified() > MAX_REPORT_AGE_MS) return null;
            byte[] data = new byte[(int) Math.min(file.length(), MAX_REPORT_CHARS * 4L)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int offset = 0;
                int read;
                while (offset < data.length && (read = in.read(data, offset, data.length - offset)) > 0) {
                    offset += read;
                }
                return new String(data, 0, offset, StandardCharsets.UTF_8);
            }
        } catch (Throwable t) {
            return null;
        } finally {
            if (!file.delete()) file.deleteOnExit(); // Offered at most once.
        }
    }

    private static void show(Activity activity, String report) {
        try {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            new WaexBottomSheet(activity)
                    .asBottomSheet()
                    .setTitle(UiText.get(R.string.waex_ui_whatsapp_crashed_last_time_f08802c3,
                            "WhatsApp crashed last time"))
                    .setMessage(UiText.get(R.string.waex_ui_whatsapp_stopped_unexpectedly_while_wa_01fcf48f,
                            "WhatsApp stopped unexpectedly while WaEnhancer X was active. Copy the details and send them to whoever made this build."))
                    .setPositiveButton(UiText.get(R.string.waex_ui_copy_details_ec7ee282, "Copy details"),
                            (dialog, which) -> {
                                ClipboardManager clipboard = activity.getSystemService(ClipboardManager.class);
                                if (clipboard != null) {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("WAEX crash report", report));
                                }
                            })
                    .setNegativeButton(UiText.get(R.string.waex_ui_close_7d9eb7ac, "Close"),
                            (dialog, which) -> dialog.dismiss())
                    .show();
        } catch (Throwable t) {
            XposedBridge.log("[WAEX] Crash report sheet unavailable: " + t.getClass().getSimpleName());
        }
    }
}
