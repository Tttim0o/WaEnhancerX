package com.waenhancer.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress

class WebServerLogicTest {

    private fun ip(text: String): InetAddress =
        InetAddress.getByAddress(text.split('.').map { it.toInt().toByte() }.toByteArray())

    private fun wifi(address: String, prefix: Int = 24) =
        listOf(LanPolicy.Iface("wlan0", ip(address).address, prefix))

    private class MemoryStore : WebStore {
        val map = HashMap<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
    }

    private fun request(text: String) = WebHttp.read(ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1)))

    // ─── Who may connect ────────────────────────────────────────────────

    @Test
    fun onlyTheSameWifiIsAllowed() {
        val phone = ip("192.168.1.226")
        assertTrue(LanPolicy.peerAllowed(ip("192.168.1.20"), phone, wifi("192.168.1.226")))
        assertFalse("other subnet", LanPolicy.peerAllowed(ip("192.168.2.20"), phone, wifi("192.168.1.226")))
        assertFalse("public peer", LanPolicy.peerAllowed(ip("8.8.8.8"), phone, wifi("192.168.1.226")))
        assertFalse("loopback", LanPolicy.peerAllowed(ip("127.0.0.1"), ip("127.0.0.1"), wifi("192.168.1.226")))
    }

    @Test
    fun mobileDataAndVpnAreRefused() {
        val mobile = listOf(LanPolicy.Iface("rmnet_data0", ip("10.20.30.40").address, 8))
        assertFalse(LanPolicy.peerAllowed(ip("10.20.30.41"), ip("10.20.30.40"), mobile))
        val vpn = listOf(LanPolicy.Iface("tun0", ip("10.8.0.2").address, 24))
        assertFalse(LanPolicy.peerAllowed(ip("10.8.0.1"), ip("10.8.0.2"), vpn))
    }

    @Test
    fun hostMustBeThePhoneAddress() {
        val phone = ip("192.168.1.226")
        assertTrue(LanPolicy.hostAllowed("192.168.1.226:8377", phone, 8377))
        assertFalse("rebinding domain", LanPolicy.hostAllowed("evil.example:8377", phone, 8377))
        assertFalse(LanPolicy.hostAllowed(null, phone, 8377))
        assertTrue(LanPolicy.originAllowed("http://192.168.1.226:8377", "192.168.1.226:8377"))
        assertFalse(LanPolicy.originAllowed("http://evil.example", "192.168.1.226:8377"))
        assertTrue("scripts send no Origin", LanPolicy.originAllowed(null, "192.168.1.226:8377"))
    }

    // ─── Requests and answers ───────────────────────────────────────────

    @Test
    fun readsARequest() {
        val r = request("POST /api/chats/421900%40s.whatsapp.net/send?x=a+b HTTP/1.1\r\n" +
            "Host: 192.168.1.226:8377\r\nCookie: a=1; waex_session=abc\r\nContent-Length: 14\r\n\r\n{\"text\":\"hi\"}\n")!!
        assertEquals("POST", r.method)
        assertEquals(listOf("api", "chats", "421900@s.whatsapp.net", "send"), r.segments)
        assertEquals("a b", r.query["x"])
        assertEquals("abc", r.cookie("waex_session"))
        assertEquals("hi", WebJson.parseObject(r.bodyText())["text"])
    }

    @Test
    fun refusesBadRequests() {
        fun status(text: String): Int = try { request(text); 200 } catch (e: WebHttp.BadRequest) { e.status }
        assertEquals(405, status("DELETE / HTTP/1.1\r\n\r\n"))
        assertEquals(400, status("GET /../secret HTTP/1.1\r\n\r\n"))
        assertEquals(411, status("POST /api/x HTTP/1.1\r\n\r\n"))
        assertEquals(413, status("POST /api/x HTTP/1.1\r\nContent-Length: 99999999\r\n\r\n"))
        assertEquals(411, status("POST /api/x HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n"))
        assertNull(request(""))
    }

    @Test
    fun writesSafeHeaders() {
        val out = ByteArrayOutputStream()
        WebHttp.write(out, WebResponse.json("{}").header("X-Test", "a\r\nInjected: 1"))
        val text = out.toString("ISO-8859-1")
        assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(text.contains("X-Content-Type-Options: nosniff\r\n"))
        assertTrue(text.contains("Cache-Control: no-store\r\n"))
        assertFalse(text.contains("\r\nInjected: 1"))
        assertTrue(text.endsWith("\r\n\r\n{}"))
    }

    @Test
    fun parsesRanges() {
        assertEquals(0L..99L, WebHttp.parseRange("bytes=0-99", 1000))
        assertEquals(500L..999L, WebHttp.parseRange("bytes=500-", 1000))
        assertEquals(900L..999L, WebHttp.parseRange("bytes=-100", 1000))
        assertEquals(0L..999L, WebHttp.parseRange("bytes=0-5000", 1000))
        assertNull(WebHttp.parseRange("bytes=2000-", 1000))
        assertNull(WebHttp.parseRange("bytes=0-1,5-6", 1000))
        assertNull(WebHttp.parseRange(null, 1000))
    }

    @Test
    fun jsonRoundTrip() {
        val json = WebJson.obj("a" to "x\"y\n<b>", "n" to 5, "ok" to true, "none" to null,
            "list" to listOf(1, "two"), "map" to mapOf("k" to false))
        assertFalse("no raw < in JSON", json.contains('<'))
        val back = WebJson.parseObject(json)
        assertEquals("x\"y\n<b>", back["a"])
        assertEquals(5.0, back["n"])
        assertEquals(true, back["ok"])
        assertNull(back["none"])
        assertEquals(listOf(1.0, "two"), back["list"])
        assertEquals(mapOf("k" to false), back["map"])
        assertTrue(WebJson.parseObject("not json").isEmpty())
        assertTrue(WebJson.parseObject("[1,2]").isEmpty())
    }

    // ─── Signing in ─────────────────────────────────────────────────────

    @Test
    fun pairingCodeWorksOnce() {
        var now = 1_000_000L
        val auth = WebAuth(MemoryStore(), { now })
        val issued = auth.requestCode() as WebAuth.CodeResult.Issued
        assertEquals(6, issued.code.length)
        assertEquals(issued.code, auth.pendingCode()?.first)
        assertTrue("second request must wait", auth.requestCode() is WebAuth.CodeResult.Wait)
        val ok = auth.confirm(issued.pairId, issued.code, null, "Firefox") as WebAuth.ConfirmResult.Ok
        assertNotNull(auth.session(ok.token))
        assertNull("a used code is no longer shown", auth.pendingCode())
        assertEquals("Firefox", auth.sessions().single().label)
        assertTrue("code is used up", auth.confirm(issued.pairId, issued.code, null, "x") is WebAuth.ConfirmResult.Expired)
        assertNull(auth.session("something else"))
        now += WebAuth.SESSION_IDLE_MS + 1
        assertNull("idle sessions end", auth.session(ok.token))
    }

    @Test
    fun codeExpiresAndOtherBrowsersCannotUseIt() {
        var now = 1_000_000L
        val auth = WebAuth(MemoryStore(), { now })
        val issued = auth.requestCode() as WebAuth.CodeResult.Issued
        assertTrue(auth.confirm("someone-else", issued.code, null, "x") is WebAuth.ConfirmResult.Expired)
        now += WebAuth.CODE_VALID_MS
        assertTrue(auth.confirm(issued.pairId, issued.code, null, "x") is WebAuth.ConfirmResult.Expired)
    }

    @Test
    fun wrongCodesLockSigningIn() {
        var now = 1_000_000L
        val auth = WebAuth(MemoryStore(), { now })
        var locked = false
        for (round in 0 until 4) {
            val issued = auth.requestCode() as? WebAuth.CodeResult.Issued ?: break
            val wrong = if (issued.code == "000000") "111111" else "000000"
            for (i in 0 until WebAuth.MAX_CODE_ATTEMPTS) {
                when (auth.confirm(issued.pairId, wrong, null, "x")) {
                    is WebAuth.ConfirmResult.Locked -> locked = true
                    else -> {}
                }
            }
        }
        assertTrue(locked || auth.requestCode() is WebAuth.CodeResult.Wait)
        assertTrue(auth.requestCode() is WebAuth.CodeResult.Wait)
        now += WebAuth.LOCK_MS + 1
        assertTrue(auth.requestCode() is WebAuth.CodeResult.Issued)
    }

    @Test
    fun passwordIsNeededWhenSet() {
        val store = MemoryStore()
        val auth = WebAuth(store)
        assertFalse(auth.setPassword("short"))
        assertTrue(auth.setPassword("correct horse"))
        assertTrue(auth.passwordSet())
        assertFalse("stored as a hash", store.map.values.any { it.contains("correct horse") })
        assertTrue(auth.checkPassword("correct horse"))
        assertFalse(auth.checkPassword("wrong horse"))
        val issued = auth.requestCode() as WebAuth.CodeResult.Issued
        assertTrue(auth.confirm(issued.pairId, issued.code, "wrong", "x") is WebAuth.ConfirmResult.WrongPassword)
        assertTrue(auth.confirm(issued.pairId, issued.code, "correct horse", "x") is WebAuth.ConfirmResult.Ok)
        assertTrue(auth.setPassword(null))
        assertFalse(auth.passwordSet())
    }

    @Test
    fun sessionsAndTokensCanBeRevoked() {
        val store = MemoryStore()
        val auth = WebAuth(store)
        val issued = auth.requestCode() as WebAuth.CodeResult.Issued
        val session = (auth.confirm(issued.pairId, issued.code, null, "A\tB\nC") as WebAuth.ConfirmResult.Ok).token
        assertFalse("only hashes are stored", store.map.values.any { it.contains(session) })
        assertEquals("ABC", auth.sessions().single().label)
        assertTrue(auth.revokeSession(auth.sessions().single().id))
        assertNull(auth.session(session))

        val token = auth.createToken("script")!!
        assertTrue(token.startsWith(WebAuth.TOKEN_PREFIX))
        assertTrue(auth.tokenValid(token))
        assertFalse(auth.tokenValid(token + "x"))
        assertFalse(auth.tokenValid(session))
        assertFalse(store.map.values.any { it.contains(token) })
        assertTrue(auth.deleteToken(auth.tokens().single().id))
        assertFalse(auth.tokenValid(token))
        repeat(WebAuth.MAX_TOKENS) { assertNotNull(auth.createToken("t$it")) }
        assertNull(auth.createToken("one too many"))
    }
}
