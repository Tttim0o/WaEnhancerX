package com.waenhancer.web

import android.content.Context
import android.os.Bundle
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * The WAEX app's side of WAEX Web. The server lives inside WhatsApp; when it starts it tells the
 * app its port and a one-time management key. With them the app asks the server, over the phone's
 * own loopback address, who is signed in and can sign browsers out.
 */
object WebAdmin {
    private const val STORE = "waex_web_admin"

    /** Called through the hook provider when the server inside WhatsApp has started. */
    @JvmStatic
    fun onServerStarted(context: Context, extras: Bundle?) {
        val port = extras?.getInt("port", 0) ?: 0
        val key = extras?.getString("admin_key").orEmpty()
        if (port !in 1024..65535 || !key.matches(Regex("[0-9a-f]{64}"))) return
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putInt("port", port).putString("key", key)
            .putString("package", extras?.getString("package").orEmpty()).apply()
        WebKeepAliveService.apply(context)
    }

    /** What the server reports, or null when WhatsApp is not running or the server is off. */
    fun state(context: Context): Map<String, Any?>? {
        val text = call(context, "GET", "/admin/state", null) ?: return null
        return WebJson.parseObject(text).takeIf { it.containsKey("sessions") }
    }

    /** Opens or closes the page; used for "only on home Wi-Fi". False when the server is away. */
    fun setActive(context: Context, on: Boolean): Boolean =
        call(context, "POST", "/admin/active", WebJson.obj("on" to on)) != null

    fun signOut(context: Context, id: String?): Boolean =
        call(context, "POST", "/admin/sessions", if (id == null) "{\"all\":true}" else WebJson.obj("id" to id)) != null

    fun deleteToken(context: Context, id: String): Boolean =
        call(context, "POST", "/admin/tokens", WebJson.obj("id" to id)) != null

    /** Sets the password, or removes it with null. False when the server refused (too short) or is away. */
    fun setPassword(context: Context, password: String?): Boolean {
        val answer = call(context, "POST", "/admin/password", WebJson.obj("new" to password)) ?: return false
        return WebJson.parseObject(answer)["ok"] == true
    }

    /** One small request to the server on this phone. Blocking; never call on the main thread. */
    private fun call(context: Context, method: String, path: String, body: String?): String? {
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val port = store.getInt("port", 0)
        val key = store.getString("key", null) ?: return null
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
