package com.waenhancer.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.waenhancer.app.R
import com.waenhancer.config.PreferenceStores
import com.waenhancer.utils.UiText

/**
 * Keeps WhatsApp awake while WAEX Web is on. Android freezes apps in the background, and a frozen
 * WhatsApp cannot answer the browser. A foreground service that holds a connection to one of
 * WhatsApp's own content providers makes Android treat WhatsApp as in use, and also starts it
 * when it is not running. Nothing is read through that connection.
 */
class WebKeepAliveService : Service() {

    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!enabled(this)) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        if (worker == null) {
            running = true
            worker = Thread({ hold() }, "waex-web-keepalive").apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    /** Holds the connections and renews them when WhatsApp was restarted. */
    private fun hold() {
        val clients = HashMap<String, ContentProviderClient>()
        try {
            while (running) {
                for (authority in AUTHORITIES) {
                    val current = clients[authority]
                    val alive = current != null && try {
                        current.getType(Uri.parse("content://$authority/"))
                        true
                    } catch (_: android.os.DeadObjectException) {
                        false
                    } catch (_: Throwable) {
                        // Any other answer still means WhatsApp is there.
                        true
                    }
                    if (alive) continue
                    current?.let(::release)
                    clients.remove(authority)
                    try {
                        contentResolver.acquireUnstableContentProviderClient(authority)?.let { clients[authority] = it }
                    } catch (_: Throwable) {
                    }
                }
                try { Thread.sleep(15_000) } catch (_: InterruptedException) { break }
            }
        } finally {
            clients.values.forEach(::release)
        }
    }

    private fun release(client: ContentProviderClient) {
        try {
            client.close()
        } catch (_: Throwable) {
        }
    }

    @Suppress("DEPRECATION") // Builder(Context) for API 24-25.
    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null &&
            manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "WAEX Web", NotificationManager.IMPORTANCE_LOW))
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID)
        else Notification.Builder(this)
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val address = LanPolicy.localInterfaces().firstOrNull()?.let { "http://${LanPolicy.format(it.address)}:${port(this)}" }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(UiText.get(R.string.waex_ui_web_running, "WAEX Web is on"))
            .setContentText(address ?: UiText.get(R.string.waex_ui_web_no_wifi, "Connect the phone to Wi-Fi to see the address."))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        worker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Starts the service again after the phone restarts or WAEX is updated. */
    class StartReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> apply(context)
            }
        }
    }

    companion object {
        const val PREF_KEY = "web_access"
        const val PREF_PORT = "web_access_port"
        const val DEFAULT_PORT = 8377
        private const val CHANNEL_ID = "waex_web_keepalive"
        private const val NOTIFICATION_ID = 0x5A1E5E02
        private val AUTHORITIES = listOf(
            "com.whatsapp.provider.sticker_whitelist_check",
            "com.whatsapp.w4b.provider.sticker_whitelist_check"
        )

        fun enabled(context: Context): Boolean = PreferenceStores.read(context, PREF_KEY) == true

        fun port(context: Context): Int =
            (PreferenceStores.read(context, PREF_PORT) as? Int)?.takeIf { it in 1024..65535 } ?: DEFAULT_PORT

        /** Starts or stops the service to match the setting. Safe to call from anywhere. */
        @JvmStatic
        fun apply(context: Context) {
            val intent = Intent(context, WebKeepAliveService::class.java)
            try {
                if (!enabled(context)) context.stopService(intent)
                else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (_: Throwable) {
                // Android may refuse a start from the background; the next start from the app works.
            }
        }
    }
}
