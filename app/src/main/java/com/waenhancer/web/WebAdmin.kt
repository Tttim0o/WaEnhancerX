package com.waenhancer.web

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.waenhancer.app.R
import com.waenhancer.utils.UiText
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The WAEX app's side of WAEX Web. Each WhatsApp app (WhatsApp, WhatsApp Business) runs its own
 * server; when one starts it tells the app its port and a one-time management key. With them the
 * app asks that server, over the phone's own loopback address, who is signed in and can sign
 * browsers out.
 */
object WebAdmin {
    private const val STORE = "waex_web_admin"
    const val WHATSAPP = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"
    val PACKAGES = listOf(WHATSAPP, BUSINESS)
    private const val LAUNCH_CHANNEL = "waex_web_switch"

    private fun suffix(pkg: String) = if (pkg == WHATSAPP) "" else "_$pkg"

    /** Called through the hook provider when the server inside a WhatsApp app has started. */
    @JvmStatic
    fun onServerStarted(context: Context, extras: Bundle?) {
        val port = extras?.getInt("port", 0) ?: 0
        val key = extras?.getString("admin_key").orEmpty()
        val pkg = extras?.getString("package").orEmpty().takeIf { it in PACKAGES } ?: return
        if (port !in 1024..65535 || !key.matches(Regex("[0-9a-f]{64}"))) return
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putInt("port" + suffix(pkg), port).putString("key" + suffix(pkg), key).apply()
        WebKeepAliveService.apply(context)
    }

    /**
     * Called by WhatsApp when the browser asked to switch accounts. Android does not let an app in
     * the background open a screen, so WhatsApp hands over the screen it wants to open. WAEX opens
     * it when it may draw over other apps, otherwise it shows a notification to tap.
     */
    @JvmStatic
    fun launch(context: Context, extras: Bundle?): Boolean {
        @Suppress("DEPRECATION")
        val target = extras?.getParcelable<PendingIntent>("intent") ?: return false
        val label = extras.getString("label").orEmpty().take(60)
        if (Settings.canDrawOverlays(context)) {
            try {
                val options = ActivityOptions.makeBasic()
                if (Build.VERSION.SDK_INT >= 34) {
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                }
                target.send(context, 0, null, null, null, null, options.toBundle())
                return true
            } catch (_: Throwable) {
                // Fall through to the notification.
            }
        }
        return try {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(LAUNCH_CHANNEL) == null) {
                manager.createNotificationChannel(NotificationChannel(LAUNCH_CHANNEL, "WAEX Web",
                    NotificationManager.IMPORTANCE_HIGH))
            }
            @Suppress("DEPRECATION")
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(context, LAUNCH_CHANNEL)
            else Notification.Builder(context)
            manager.notify(0x5A1E5E03, builder
                .setSmallIcon(android.R.drawable.ic_menu_rotate)
                .setContentTitle(UiText.get(R.string.waex_ui_web_switch_title, "Switch WhatsApp account"))
                .setContentText(UiText.get(R.string.waex_ui_web_switch_text, "Tap to switch to %s, as asked in WAEX Web.", label))
                .setContentIntent(target)
                .setAutoCancel(true)
                .build())
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** What the server reports, or null when that app is not running or its server is off. */
    fun state(context: Context, pkg: String = WHATSAPP): Map<String, Any?>? {
        val text = call(context, pkg, "GET", "/admin/state", null) ?: return null
        return WebJson.parseObject(text).takeIf { it.containsKey("sessions") }
    }

    /** Opens or closes the page; used for "only on home Wi-Fi". False when the server is away. */
    fun setActive(context: Context, pkg: String, on: Boolean): Boolean =
        call(context, pkg, "POST", "/admin/active", WebJson.obj("on" to on)) != null

    fun signOut(context: Context, pkg: String, id: String?): Boolean =
        call(context, pkg, "POST", "/admin/sessions", if (id == null) "{\"all\":true}" else WebJson.obj("id" to id)) != null

    fun deleteToken(context: Context, pkg: String, id: String): Boolean =
        call(context, pkg, "POST", "/admin/tokens", WebJson.obj("id" to id)) != null

    /** Sets the password, or removes it with null. False when the server refused (too short) or is away. */
    fun setPassword(context: Context, pkg: String, password: String?): Boolean {
        val answer = call(context, pkg, "POST", "/admin/password", WebJson.obj("new" to password)) ?: return false
        return WebJson.parseObject(answer)["ok"] == true
    }

    /** One small request to the server on this phone. Blocking; never call on the main thread. */
    private fun call(context: Context, pkg: String, method: String, path: String, body: String?): String? {
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val port = store.getInt("port" + suffix(pkg), 0)
        val key = store.getString("key" + suffix(pkg), null) ?: return null
        if (port !in 1024..65535) return null
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)), port), 1500)
                socket.soTimeout = 4000
                val bytes = (body ?: "").toByteArray(Charsets.UTF_8)
                val head = "$method $path HTTP/1.1\r\nHost: 127.0.0.1:$port\r\nX-WAEX-Admin: $key\r\n" +
                    "Content-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                socket.getOutputStream().apply { write(head.toByteArray(Charsets.ISO_8859_1)); write(bytes); flush() }
                val answer = socket.getInputStream().readBytes()
                val text = String(answer, Charsets.UTF_8)
                val split = text.indexOf("\r\n\r\n")
                if (split < 0 || !text.startsWith("HTTP/1.1 200")) null else text.substring(split + 4)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
