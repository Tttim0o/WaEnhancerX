package com.waenhancer.web

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

/**
 * "Only on home Wi-Fi" for WAEX Web: the list of home networks and the name of the current one.
 *
 * Android tells an app the Wi-Fi name only with the location permission (and, while the app is in
 * the background, with "allow all the time"). Without it the name is unknown and the page stays
 * closed, which is the safe side. The names stay in WAEX's own storage; WhatsApp only learns
 * whether the page is open.
 */
object WebWifi {
    private const val STORE = "waex_web_wifi"
    private const val KEY_LIST = "networks"
    private const val MAX_NETWORKS = 20

    /** The switch lives in the shared settings so the server inside WhatsApp starts closed. */
    fun wifiOnly(context: Context, pkg: String): Boolean =
        WebKeepAliveService.setting(context, pkg, WebKeepAliveService.PREF_WIFI_ONLY) == true

    fun networks(context: Context): List<String> =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(KEY_LIST, null)
            ?.split('\n')?.map(String::trim)?.filter(String::isNotEmpty)?.distinct().orEmpty()

    fun add(context: Context, name: String): List<String> {
        val clean = name.trim().removeSurrounding("\"").take(32)
        if (clean.isEmpty()) return networks(context)
        val list = (networks(context) + clean).distinct().take(MAX_NETWORKS)
        save(context, list)
        return list
    }

    fun remove(context: Context, name: String): List<String> {
        val list = networks(context).filter { it != name }
        save(context, list)
        return list
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIST, list.joinToString("\n")).apply()
    }

    fun hasLocation(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Needed from Android 10 on so the name is known while WAEX is not on screen. */
    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Name of the Wi-Fi the phone uses now, or null when not on Wi-Fi or Android hides it. */
    @Suppress("DEPRECATION")
    fun currentName(context: Context, capabilities: NetworkCapabilities? = null): String? {
        if (!hasLocation(context)) return null
        val fromCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (capabilities?.transportInfo as? WifiInfo)?.ssid else null
        val raw = fromCallback?.takeIf(::known) ?: try {
            val connectivity = context.getSystemService(ConnectivityManager::class.java)
            val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) null
            else context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
        } catch (_: Throwable) {
            null
        }
        return raw?.takeIf(::known)?.removeSurrounding("\"")
    }

    private fun known(ssid: String): Boolean =
        ssid.isNotBlank() && ssid != WifiManager.UNKNOWN_SSID && ssid != "<unknown ssid>"

    /** Whether the page should be open now. Always true when "only on home Wi-Fi" is off. */
    fun shouldBeOpen(context: Context, pkg: String, capabilities: NetworkCapabilities? = null): Boolean {
        if (!wifiOnly(context, pkg)) return true
        val name = currentName(context, capabilities) ?: return false
        return networks(context).any { it == name }
    }
}
