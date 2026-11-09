package com.waenhancer.web

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/**
 * Decides who may talk to the web server. Only devices on the same Wi-Fi or Ethernet network
 * as the phone are served: the connection must arrive on a wlan/eth interface, from a private
 * IPv4 address inside that interface's subnet. Mobile data, VPN tunnels and IPv6 are refused.
 */
object LanPolicy {

    class Iface(val name: String, val address: ByteArray, val prefix: Int)

    fun usableInterface(name: String): Boolean = name.startsWith("wlan") || name.startsWith("eth")

    /** 10/8, 172.16/12 and 192.168/16. */
    fun isPrivateV4(a: ByteArray): Boolean {
        if (a.size != 4) return false
        val b0 = a[0].toInt() and 0xff
        val b1 = a[1].toInt() and 0xff
        return b0 == 10 || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168)
    }

    fun sameSubnet(a: ByteArray, b: ByteArray, prefix: Int): Boolean {
        if (a.size != 4 || b.size != 4) return false
        // An unknown or implausible prefix is treated as a home network (/24).
        val bits = if (prefix in 8..30) prefix else 24
        val mask = if (bits == 0) 0 else (-1 shl (32 - bits))
        return (toInt(a) and mask) == (toInt(b) and mask)
    }

    private fun toInt(a: ByteArray): Int =
        ((a[0].toInt() and 0xff) shl 24) or ((a[1].toInt() and 0xff) shl 16) or
            ((a[2].toInt() and 0xff) shl 8) or (a[3].toInt() and 0xff)

    /** [peer] is the remote end, [local] the phone's address the connection arrived on. */
    fun peerAllowed(peer: InetAddress, local: InetAddress, ifaces: List<Iface>): Boolean {
        if (peer !is Inet4Address || local !is Inet4Address) return false
        val p = peer.address
        val l = local.address
        if (!isPrivateV4(p) || !isPrivateV4(l)) return false
        val iface = ifaces.firstOrNull { usableInterface(it.name) && it.address.contentEquals(l) } ?: return false
        return sameSubnet(p, l, iface.prefix)
    }

    /**
     * The Host header must be the phone's own IP address. This stops DNS rebinding, where a
     * website points its own domain at the phone to read the answers.
     */
    fun hostAllowed(host: String?, local: InetAddress, port: Int): Boolean {
        if (host == null) return false
        val ip = local.hostAddress ?: return false
        return host == ip || host == "$ip:$port"
    }

    /** A request that changes something must come from our own page (or from a non-browser client). */
    fun originAllowed(origin: String?, host: String): Boolean =
        origin == null || origin == "http://$host"

    /** Active Wi-Fi and Ethernet interfaces with a private IPv4 address. */
    fun localInterfaces(): List<Iface> {
        val result = ArrayList<Iface>()
        try {
            val all = NetworkInterface.getNetworkInterfaces() ?: return result
            for (ni in all) {
                if (!ni.isUp || ni.isLoopback || !usableInterface(ni.name)) continue
                for (ia in ni.interfaceAddresses) {
                    val address = ia.address as? Inet4Address ?: continue
                    if (isPrivateV4(address.address)) {
                        result.add(Iface(ni.name, address.address, ia.networkPrefixLength.toInt()))
                    }
                }
            }
        } catch (_: Exception) {
        }
        return result
    }

    fun format(address: ByteArray): String = address.joinToString(".") { (it.toInt() and 0xff).toString() }
}
