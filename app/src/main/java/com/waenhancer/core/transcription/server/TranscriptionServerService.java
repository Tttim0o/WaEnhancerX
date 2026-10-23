package com.waenhancer.core.transcription.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.waenhancer.config.PreferenceStores;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Keeps the WAEX app alive while the local transcription API runs; the ongoing notification
 * shows where it listens. Started and stopped only from the settings screen.
 */
public final class TranscriptionServerService extends Service {

    public static final String PREF_MODE = "local_server_mode";
    public static final String PREF_PORT = "local_server_port";
    public static final String PREF_KEY = "local_server_key";
    /** Keep serving other apps even while WAEX itself does not use on-device models. */
    public static final String PREF_ALWAYS = "local_server_always";
    private static final String CHANNEL_ID = "waex_transcription_server";
    private static final int NOTIFICATION_ID = 0x5A1E5E01;

    /**
     * Applies the saved settings: starts, restarts or stops the service. Call from the UI after
     * any change to the server or to the transcription provider.
     */
    public static void apply(@NonNull Context context) {
        Intent intent = new Intent(context, TranscriptionServerService.class);
        if (!shouldRun(context)) {
            context.stopService(intent);
            LocalApiServer.stop();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /**
     * Runs when a mode is chosen and either WAEX transcribes with on-device models or the user
     * asked to keep it running for other apps regardless.
     */
    public static boolean shouldRun(@NonNull Context context) {
        if (LocalApiServer.MODE_OFF.equals(mode(context))) return false;
        Object always = PreferenceStores.read(context, PREF_ALWAYS);
        if (Boolean.TRUE.equals(always)) return true;
        return "Local".equals(PreferenceStores.read(context, "transcription_provider"));
    }

    /**
     * Uses root to exempt WAEX from battery optimization, so Android does not stop the server
     * while the phone is idle. Returns a short result for the screen. Blocking; not on the UI
     * thread. The package name comes from the system, never from user input.
     */
    @NonNull
    public static String exemptFromBatteryOptimization(@NonNull Context context) {
        String pkg = context.getPackageName();
        if (!pkg.matches("[A-Za-z0-9._]+")) return "Unexpected package name";
        String output = com.waenhancer.xposed.utils.RootCommandRunner.run(
                "cmd deviceidle whitelist +" + pkg + " || dumpsys deviceidle whitelist +" + pkg, 15_000);
        if (output == null) return "Root command failed (is root granted to WAEX?)";
        String result = output.trim().isEmpty() ? "Done" : output.trim();
        com.waenhancer.core.transcription.TranscriptionLog.add(context, "app",
                "Battery optimization exemption via root: " + result);
        return result;
    }

    @NonNull
    public static String mode(@NonNull Context context) {
        Object value = PreferenceStores.read(context, PREF_MODE);
        return value instanceof String ? (String) value : LocalApiServer.MODE_OFF;
    }

    public static int port(@NonNull Context context) {
        Object value = PreferenceStores.read(context, PREF_PORT);
        return value instanceof Integer ? (Integer) value : LocalApiServer.DEFAULT_PORT;
    }

    /** The API key, created on first use. */
    @NonNull
    public static String key(@NonNull Context context) {
        Object value = PreferenceStores.read(context, PREF_KEY);
        if (value instanceof String && !((String) value).isEmpty()) return (String) value;
        return newKey(context);
    }

    @NonNull
    public static String newKey(@NonNull Context context) {
        byte[] random = new byte[24];
        new SecureRandom().nextBytes(random);
        StringBuilder key = new StringBuilder("waex-");
        for (byte b : random) key.append(String.format(Locale.ROOT, "%02x", b));
        PreferenceStores.privateStore(context).edit().putString(PREF_KEY, key.toString()).apply();
        return key.toString();
    }

    /** Base URLs other apps can use, e.g. http://192.168.1.20:8765/v1. */
    @NonNull
    public static List<String> baseUrls(@NonNull String mode, int port) {
        List<String> urls = new ArrayList<>();
        urls.add("http://127.0.0.1:" + port + "/v1");
        if (!LocalApiServer.MODE_NETWORK.equals(mode)) return urls;
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) continue;
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        urls.add("http://" + address.getHostAddress() + ":" + port + "/v1");
                    }
                }
            }
        } catch (Throwable ignored) {}
        return urls;
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String mode = mode(this);
        int port = port(this);
        startInForeground(mode, port);
        if (!shouldRun(this)
                || (!LocalApiServer.start(this, mode, port, key(this)))) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @SuppressWarnings("deprecation") // Builder(Context) for API 24-25.
    private void startInForeground(String mode, int port) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null
                && manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    "WAEX transcription server", NotificationManager.IMPORTANCE_LOW));
        }
        List<String> urls = baseUrls(mode, port);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Transcription server running")
                .setContentText(urls.get(urls.size() - 1))
                .setStyle(new Notification.BigTextStyle().bigText(String.join("\n", urls)))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public void onDestroy() {
        LocalApiServer.stop();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
