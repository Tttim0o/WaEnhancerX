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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
    /** Whether the page is open now; null until first decided. */
    @Volatile private var open: Boolean? = null
    /** What the server was last told; null means tell it again (it may have restarted). */
    @Volatile private var told: Boolean? = null
    @Volatile private var wifiCaps: NetworkCapabilities? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!enabled(this)) {
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        told = null
        watchNetwork()
        if (worker == null) {
            running = true
            worker = Thread({ hold() }, "waex-web-keepalive").apply { isDaemon = true; start() }
        } else {
            worker?.interrupt()
        }
        return START_STICKY
    }

    /** Wakes the worker whenever the Wi-Fi changes, so the page opens or closes right away. */
    private fun watchNetwork() {
        if (networkCallback != null) return
        val connectivity = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    wifiCaps = caps
                    worker?.interrupt()
                }
                override fun onLost(network: Network) {
                    wifiCaps = null
                    worker?.interrupt()
                }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    worker?.interrupt()
                }
                override fun onLost(network: Network) {
                    worker?.interrupt()
                }
            }
        }
        try {
            connectivity.registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), callback)
            networkCallback = callback
        } catch (_: Throwable) {
        }
    }

    /** Holds the connections and renews them when WhatsApp was restarted. */
    private fun hold() {
        val clients = HashMap<String, ContentProviderClient>()
        try {
            while (running) {
                val nowOpen = WebWifi.shouldBeOpen(this, wifiCaps)
                if (nowOpen != open) {
                    open = nowOpen
                    told = null
                    try { startInForeground() } catch (_: Throwable) {}
                }
                if (told != nowOpen && WebAdmin.setActive(this, nowOpen)) told = nowOpen
                if (!nowOpen) {
                    // Away from home: let WhatsApp sleep as usual.
                    clients.values.forEach(::release)
                    clients.clear()
                    try { Thread.sleep(60_000) } catch (_: InterruptedException) { if (!running) break }
                    continue
                }
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
                try { Thread.sleep(15_000) } catch (_: InterruptedException) { if (!running) break }
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
        val waiting = open == false
        val address = LanPolicy.localInterfaces().firstOrNull()?.let { "http://${LanPolicy.format(it.address)}:${port(this)}" }
        val notification = builder
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(if (waiting) UiText.get(R.string.waex_ui_web_waiting, "WAEX Web waits for a home Wi-Fi")
                else UiText.get(R.string.waex_ui_web_running, "WAEX Web is on"))
            .setContentText(if (waiting) UiText.get(R.string.waex_ui_web_waiting_desc, "The page opens by itself on one of your home networks.")
                else address ?: UiText.get(R.string.waex_ui_web_no_wifi, "Connect the phone to Wi-Fi to see the address."))
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
        networkCallback?.let { cb ->
            try { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } catch (_: Throwable) {}
        }
        networkCallback = null
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
        const val PREF_WIFI_ONLY = "web_wifi_only"
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
