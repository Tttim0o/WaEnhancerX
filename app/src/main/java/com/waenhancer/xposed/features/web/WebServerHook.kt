package com.waenhancer.xposed.features.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import com.waenhancer.app.R
import com.waenhancer.utils.UiText
import com.waenhancer.web.LanPolicy
import com.waenhancer.web.WebAuth
import com.waenhancer.web.WebHttp
import com.waenhancer.web.WebJson
import com.waenhancer.web.WebRequest
import com.waenhancer.web.WebResponse
import com.waenhancer.web.WebStore
import com.waenhancer.xposed.MainHook
import com.waenhancer.xposed.core.BaseFeature
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import de.robv.android.xposed.XposedHelpers

/**
 * WAEX Web: a small web server inside WhatsApp's process that shows the chats in a browser on
 * the same Wi-Fi and lets the signed-in person write messages. Off unless switched on.
 *
 * Safety rules, all enforced here or in [LanPolicy] / [WebAuth]:
 *  - only private IPv4 peers on the phone's own Wi-Fi/Ethernet subnet are answered;
 *  - the Host header must be the phone's IP (no DNS rebinding);
 *  - a new browser needs the pairing code from a notification, plus the password when one is set;
 *  - changes need our own page's header and origin (no cross-site requests);
 *  - locked chats are never shown; message text, codes and tokens are never logged.
 * The connection is plain HTTP: people on the same network who can capture traffic can read it.
 */
class WebServerHook(context: Context, classLoader: ClassLoader, prefs: SharedPreferences) :
    BaseFeature(context, classLoader, prefs) {

    override val name: String
        get() = "WAEX Web"

    override val category: String
        get() = "Automation"

    private lateinit var app: Context
    private lateinit var data: WebData
    private lateinit var auth: WebAuth
    private val assets = ConcurrentHashMap<String, ByteArray>()
    private val waiting = AtomicInteger(0)
    private val sendTimes = ArrayDeque<Long>()
    @Volatile private var interfaces: List<LanPolicy.Iface> = emptyList()
    @Volatile private var interfacesAt = 0L
    @Volatile private var port = DEFAULT_PORT
    /** With "only on home Wi-Fi", the WAEX app opens the page once the phone is on such a network. */
    @Volatile private var active = true
    private lateinit var accounts: WebAccounts
    @Volatile private var merge = false
    /** Readers for accounts that are not in use, by account id. */
    private val otherData = java.util.concurrent.ConcurrentHashMap<String, WebData>()

    /** The reader for an account id from the browser; the account in use when none is given. */
    private fun dataFor(request: WebRequest): WebData? {
        val id = request.query["account"]?.takeIf { it.isNotEmpty() } ?: return data
        val account = accounts.byId(id) ?: return null
        if (account.active) return data
        return otherData.getOrPut(account.id) { WebData(app, account) }
    }

    override fun hook() {
        if (!isEnabled(PREF_KEY, false)) return
        app = context.applicationContext ?: context
        active = !isEnabled(PREF_WIFI_ONLY, false)
        data = WebData(app)
        accounts = WebAccounts(app)
        // Merging the inboxes of several accounts is for WhatsApp only, not WhatsApp Business.
        merge = app.packageName == "com.whatsapp" && isEnabled(PREF_MERGE, false)
        auth = WebAuth(FileStore(File(app.applicationInfo.dataDir, "waex_web_state.json"),
            app.getSharedPreferences("waex_web", Context.MODE_PRIVATE)))
        port = portFor(app.packageName)

        WebSender.prepare(context, classLoader)
        Thread({ serve() }, "waex-web").apply { isDaemon = true }.start()
    }

    /**
     * Sign-ins, tokens and the password, kept in one small file in WhatsApp's main data folder.
     * WhatsApp's own settings files belong to the account in use, so a browser would be signed out
     * every time the phone switches accounts; this file stays the same for all of them.
     * Older versions kept the same values in [legacy]; they are moved over once.
     */
    private class FileStore(private val file: File, legacy: android.content.SharedPreferences) : WebStore {
        private val values = LinkedHashMap<String, String>()

        init {
            val loaded = try {
                if (file.isFile) org.json.JSONObject(file.readText()) else null
            } catch (_: Throwable) {
                null
            }
            if (loaded != null) {
                for (key in loaded.keys()) loaded.optString(key, null)?.let { values[key] = it }
            } else {
                for ((key, value) in legacy.all) if (value is String) values[key] = value
                save()
            }
        }

        @Synchronized override fun get(key: String): String? = values[key]

        @Synchronized override fun put(key: String, value: String?) {
            if (value == null) values.remove(key) else values[key] = value
            save()
        }

        private fun save() {
            try {
                val json = org.json.JSONObject()
                for ((key, value) in values) json.put(key, value)
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(json.toString())
                if (!tmp.renameTo(file)) { file.writeText(json.toString()); tmp.delete() }
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * WhatsApp and WhatsApp Business each run a server, so they need different ports. A port set
     * for one app ("Per Target App" settings) is used as is; a shared one is moved up by one for
     * WhatsApp Business.
     */
    private fun portFor(pkg: String): Int {
        val business = pkg == "com.whatsapp.w4b"
        fun read(key: String): Int? = if (!prefs.contains(key)) null else try {
            prefs.getInt(key, 0)
        } catch (_: Throwable) {
            prefs.getString(key, null)?.toIntOrNull()
        }
        val chosen = read("$pkg:$PREF_PORT") ?: read(PREF_PORT)?.let { if (business) it + 1 else it }
        val fallback = if (business) DEFAULT_PORT + 1 else DEFAULT_PORT
        return chosen?.takeIf { it in 1024..65535 } ?: fallback
    }

    // ─── Server loop ────────────────────────────────────────────────────

    private fun serve() {
        val workers = ThreadPoolExecutor(2, MAX_WORKERS, 30, TimeUnit.SECONDS, SynchronousQueue()) { r ->
            Thread(r, "waex-web-worker").apply { isDaemon = true }
        }
        var failures = 0
        while (true) {
            try {
                ServerSocket().use { server ->
                    server.reuseAddress = true
                    server.bind(InetSocketAddress(port), 32)
                    XposedBridge.log("$TAG Listening on port $port, sending ${if (WebSender.available) "available" else "unavailable"}")
                    failures = 0
                    announce()
                    while (true) {
                        val client = server.accept()
                        try {
                            workers.execute { handle(client) }
                        } catch (_: RejectedExecutionException) {
                            closeQuietly(client)
                        }
                    }
                }
            } catch (t: Throwable) {
                failures++
                if (failures == 1) XposedBridge.log("$TAG Server stopped: ${t.javaClass.simpleName}")
                // The port may be busy or the network gone; try again later, slowly.
                try { Thread.sleep(minOf(60_000L, 5_000L * failures)) } catch (_: InterruptedException) { return }
            }
        }
    }

    private fun closeQuietly(socket: Socket) {
        try { socket.close() } catch (_: Throwable) {}
    }

    private fun localInterfaces(): List<LanPolicy.Iface> {
        val now = System.currentTimeMillis()
        if (now - interfacesAt > 5_000) {
            interfaces = LanPolicy.localInterfaces()
            interfacesAt = now
        }
        return interfaces
    }

    private fun handle(socket: Socket) {
        try {
            socket.soTimeout = 20_000
            val peer = socket.inetAddress
            val local = socket.localAddress
            if (peer == null || local == null) return
            val onPhone = peer.isLoopbackAddress && local.isLoopbackAddress
            // Anything that is not on the phone itself or its own Wi-Fi is dropped without an answer.
            if (!onPhone && !LanPolicy.peerAllowed(peer, local, localInterfaces())) return
            val out = socket.getOutputStream()
            val request = try {
                WebHttp.read(socket.getInputStream()) ?: return
            } catch (e: WebHttp.BadRequest) {
                WebHttp.write(out, WebResponse.error(e.status, "bad_request"))
                return
            }
            if (onPhone && request.path.startsWith("/admin/")) {
                // The WAEX app on this phone manages the server through here, with its own key.
                val response = try { admin(request) } catch (_: Throwable) { WebResponse.error(500, "internal") }
                WebHttp.write(out, response)
                return
            }
            // Away from the home networks the page stays closed: no answer, as if nothing were there.
            if (!active) return
            val host = request.header("host")
            // A browser on the phone itself uses localhost; the exact name still stops DNS rebinding.
            val hostOk = if (onPhone) host in setOf("localhost:$port", "127.0.0.1:$port")
                else LanPolicy.hostAllowed(host, local, port)
            val response = if (!hostOk) {
                WebResponse.error(403, "open_by_ip_address")
            } else try {
                route(request, host!!, peer.hostAddress.orEmpty())
            } catch (e: WebHttp.BadRequest) {
                WebResponse.error(e.status, "bad_request")
            } catch (t: Throwable) {
                XposedBridge.log("$TAG Request failed: ${t.javaClass.simpleName}")
                WebResponse.error(500, "internal")
            }
            WebHttp.write(out, response, request.method == "HEAD")
        } catch (_: Throwable) {
            // Broken connections are normal (closed tabs, sleeping laptops).
        } finally {
            closeQuietly(socket)
        }
    }

    // ─── Management from the WAEX app ───────────────────────────────────

    private val adminKey: String = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        .joinToString("") { String.format("%02x", it.toInt() and 0xff) }

    /** Tells the WAEX app that the server runs, so it can keep WhatsApp awake and manage browsers. */
    private fun announce() {
        try {
            val extras = android.os.Bundle()
            extras.putInt("port", port)
            extras.putString("admin_key", adminKey)
            extras.putString("package", app.packageName)
            app.contentResolver.call(Uri.parse("content://com.waenhancer.hookprovider"), "web_started", null, extras)
        } catch (t: Throwable) {
            XposedBridge.log("$TAG WAEX app not reached: ${t.javaClass.simpleName}")
        }
    }

    private fun admin(request: WebRequest): WebResponse {
        val given = request.header("x-waex-admin").orEmpty()
        if (!java.security.MessageDigest.isEqual(given.toByteArray(), adminKey.toByteArray())) {
            return WebResponse.error(403, "admin_key")
        }
        val seg = request.segments
        if (seg.firstOrNull() != "admin") return WebResponse.error(404, "not_found")
        val post = request.method == "POST"
        val body = if (post) WebJson.parseObject(request.bodyText()) else emptyMap()
        return when (seg.getOrNull(1)) {
            "state" -> {
                val pending = auth.pendingCode()
                WebResponse.json(WebJson.obj(
                    "port" to port,
                    "active" to active,
                    "passwordSet" to auth.passwordSet(),
                    "canSend" to WebSender.available,
                    "canReact" to WebSender.canReact,
                    "lastError" to WebSender.lastError,
                    "code" to pending?.first,
                    "codeSeconds" to (pending?.second ?: 0),
                    "sessions" to auth.sessions().map {
                        linkedMapOf("id" to it.id, "label" to it.label, "created" to it.created, "lastSeen" to it.lastSeen)
                    },
                    "tokens" to auth.tokens().map { linkedMapOf("id" to it.id, "name" to it.name, "created" to it.created) }
                ))
            }
            "active" -> if (!post) WebResponse.error(405, "method") else {
                active = body["on"] == true
                WebResponse.json(WebJson.obj("ok" to true, "active" to active))
            }
            "sessions" -> if (!post) WebResponse.error(405, "method") else {
                if (body["all"] == true) auth.revokeAllSessions() else auth.revokeSession(body["id"] as? String ?: "")
                WebResponse.json("{\"ok\":true}")
            }
            "tokens" -> if (!post) WebResponse.error(405, "method") else {
                auth.deleteToken(body["id"] as? String ?: "")
                WebResponse.json("{\"ok\":true}")
            }
            "password" -> if (!post) WebResponse.error(405, "method") else {
                // The phone's owner is in charge here, so the old password is not asked for.
                if (auth.setPassword(body["new"] as? String)) WebResponse.json("{\"ok\":true}")
                else WebResponse.error(400, "password_length")
            }
            else -> WebResponse.error(404, "not_found")
        }
    }

    // ─── Routing ────────────────────────────────────────────────────────

    private class Caller(val session: WebAuth.Session?, val viaToken: Boolean)

    private fun route(request: WebRequest, host: String, peerIp: String): WebResponse {
        val seg = request.segments
        val post = request.method == "POST"
        if (post && !LanPolicy.originAllowed(request.header("origin"), host)) return WebResponse.error(403, "origin")

        if (seg.isEmpty() || seg[0] != "api") return if (post) WebResponse.error(405, "method") else asset(seg)

        val bearer = request.header("authorization")?.takeIf { it.startsWith("Bearer ") }?.substring(7)?.trim()
        val session = auth.session(request.cookie(WebAuth.COOKIE))
        val caller = when {
            bearer != null -> if (auth.tokenValid(bearer)) Caller(null, true) else return WebResponse.error(401, "bad_token")
            session != null -> Caller(session, false)
            else -> null
        }
        // Browsers cannot add this header across sites, so a foreign page cannot act for the user.
        if (post && caller?.viaToken != true && request.header("x-waex") != "1") return WebResponse.error(403, "header")

        val path = seg.drop(1)
        when (path.firstOrNull()) {
            "status" -> return WebResponse.json(WebJson.obj(
                "app" to "waex-web",
                "signedIn" to (caller != null),
                "passwordSet" to auth.passwordSet(),
                "canSend" to (caller != null && WebSender.available),
                "account" to (if (caller != null) accounts.active()?.id else null)
            ))
            "docs" -> return asset(listOf("api.txt"))
            "pair" -> return if (post) pair(request, path, peerIp) else WebResponse.error(405, "method")
        }
        if (caller == null) return WebResponse.error(401, "sign_in")

        return when (path.firstOrNull()) {
            "chats" -> chats(request, path, post)
            "search" -> WebResponse.json(data.searchJson(request.query["q"].orEmpty(),
                request.query["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50))
            "updates" -> updates(request)
            "media" -> media(request, path.getOrNull(1)?.toLongOrNull())
            "thumb" -> thumb(request, path.getOrNull(1)?.toLongOrNull())
            "avatar" -> avatar(request, path.getOrNull(1))
            "accounts" -> accountsRoute(request, path, post)
            "logout" -> if (!post) WebResponse.error(405, "method") else {
                caller.session?.let { auth.revokeSession(it.id) }
                WebResponse.json("{\"ok\":true}").header("Set-Cookie", cookie("", 0))
            }
            "account" -> if (caller.session == null) WebResponse.error(403, "browser_only") else
                account(request, path, post, caller.session)
            else -> WebResponse.error(404, "not_found")
        }
    }

    // ─── Static files ───────────────────────────────────────────────────

    private fun asset(seg: List<String>): WebResponse {
        val name = if (seg.isEmpty()) "index.html" else seg.singleOrNull() ?: return WebResponse.error(404, "not_found")
        val type = ASSETS[name] ?: return WebResponse.error(404, "not_found")
        val bytes = assets[name] ?: loadAsset(name)?.also { assets[name] = it }
            ?: return WebResponse.error(503, "module_files_unavailable")
        val response = WebResponse(200, type, bytes)
        if (name == "index.html") response.header("Content-Security-Policy", CSP)
        return response
    }

    private fun loadAsset(name: String): ByteArray? {
        val entry = "assets/waexweb/$name"
        try {
            MainHook.sModulePath?.let { path ->
                ZipFile(path).use { zip ->
                    zip.getEntry(entry)?.let { e -> return zip.getInputStream(e).use { it.readBytes() } }
                }
            }
        } catch (_: Throwable) {
        }
        return try {
            app.createPackageContext("com.waenhancer", 0).assets.open("waexweb/$name").use { it.readBytes() }
        } catch (_: Throwable) {
            null
        }
    }

    // ─── Signing in ─────────────────────────────────────────────────────

    private fun pair(request: WebRequest, path: List<String>, peerIp: String): WebResponse {
        val body = WebJson.parseObject(request.bodyText())
        return when (path.getOrNull(1)) {
            "request" -> when (val result = auth.requestCode()) {
                is WebAuth.CodeResult.Issued -> {
                    showCode(result.code, peerIp)
                    WebResponse.json(WebJson.obj("pairId" to result.pairId, "validSeconds" to result.validSeconds,
                        "passwordSet" to auth.passwordSet()))
                }
                is WebAuth.CodeResult.Wait -> WebResponse.json(WebJson.obj("error" to "wait", "seconds" to result.seconds), 429)
            }
            "confirm" -> {
                val label = browserLabel(request.header("user-agent")) + " · " + peerIp
                when (val result = auth.confirm(body["pairId"] as? String, body["code"] as? String,
                    body["password"] as? String, label)) {
                    is WebAuth.ConfirmResult.Ok -> {
                        notify(NOTIFY_CODE, UiText.get(R.string.waex_ui_web_signed_in_title, "WAEX Web: new browser signed in"),
                            label, 0)
                        WebResponse.json("{\"ok\":true}").header("Set-Cookie",
                            cookie(result.token, (WebAuth.SESSION_IDLE_MS / 1000).toInt()))
                    }
                    WebAuth.ConfirmResult.WrongCode -> WebResponse.error(403, "wrong_code")
                    WebAuth.ConfirmResult.WrongPassword -> WebResponse.error(403, "wrong_password")
                    WebAuth.ConfirmResult.Expired -> WebResponse.error(403, "expired")
                    is WebAuth.ConfirmResult.Locked ->
                        WebResponse.json(WebJson.obj("error" to "locked", "seconds" to result.seconds), 429)
                }
            }
            else -> WebResponse.error(404, "not_found")
        }
    }

    private fun cookie(value: String, maxAge: Int): String =
        "${WebAuth.COOKIE}=$value; Path=/; Max-Age=$maxAge; HttpOnly; SameSite=Strict"

    private fun browserLabel(userAgent: String?): String {
        val ua = userAgent.orEmpty()
        val browser = when {
            "Edg/" in ua -> "Edge"
            "OPR/" in ua -> "Opera"
            "Firefox/" in ua -> "Firefox"
            "Chrome/" in ua -> "Chrome"
            "Safari/" in ua -> "Safari"
            else -> "Browser"
        }
        val system = when {
            "Windows" in ua -> "Windows"
            "Android" in ua -> "Android"
            "iPhone" in ua || "iPad" in ua -> "iOS"
            "Mac OS" in ua -> "macOS"
            "Linux" in ua -> "Linux"
            else -> ""
        }
        return if (system.isEmpty()) browser else "$browser, $system"
    }

    private fun showCode(code: String, peerIp: String) {
        val spaced = code.substring(0, 3) + " " + code.substring(3)
        notify(NOTIFY_CODE,
            UiText.get(R.string.waex_ui_web_code_title, "WAEX Web pairing code: %s", spaced),
            UiText.get(R.string.waex_ui_web_code_text,
                "Asked for by %s. Valid for 2 minutes. If this was not you, ignore it.", peerIp),
            WebAuth.CODE_VALID_MS)
    }

    private fun notify(id: Int, title: String, text: String, timeoutMs: Long) {
        try {
            val manager = app.getSystemService(NotificationManager::class.java) ?: return
            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(CHANNEL) == null) {
                    manager.createNotificationChannel(NotificationChannel(CHANNEL, "WAEX Web",
                        NotificationManager.IMPORTANCE_HIGH))
                }
                Notification.Builder(app, CHANNEL).apply { if (timeoutMs > 0) setTimeoutAfter(timeoutMs) }
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(app).setPriority(Notification.PRIORITY_HIGH)
            }
            manager.notify(id, builder
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setShowWhen(true)
                // The code must not be readable on a locked screen.
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .build())
        } catch (t: Throwable) {
            XposedBridge.log("$TAG Notification failed: ${t.javaClass.simpleName}")
        }
    }

    private fun account(request: WebRequest, path: List<String>, post: Boolean, session: WebAuth.Session): WebResponse {
        val body = if (post) WebJson.parseObject(request.bodyText()) else emptyMap()
        when (path.getOrNull(1)) {
            null -> return WebResponse.json(WebJson.obj(
                "passwordSet" to auth.passwordSet(),
                "sessions" to auth.sessions().map {
                    linkedMapOf("id" to it.id, "label" to it.label, "created" to it.created,
                        "lastSeen" to it.lastSeen, "current" to (it.id == session.id))
                },
                "tokens" to auth.tokens().map { linkedMapOf("id" to it.id, "name" to it.name, "created" to it.created) }
            ))
        }
        if (!post) return WebResponse.error(405, "method")
        return when (path.getOrNull(1)) {
            "password" -> {
                if (auth.passwordSet() && !auth.checkPassword(body["current"] as? String ?: "")) {
                    return WebResponse.error(403, "wrong_password")
                }
                if (auth.setPassword(body["new"] as? String)) WebResponse.json("{\"ok\":true}")
                else WebResponse.error(400, "password_length")
            }
            "sessions" -> {
                if (body["all"] == true) {
                    auth.revokeAllSessions()
                    WebResponse.json("{\"ok\":true}").header("Set-Cookie", cookie("", 0))
                } else WebResponse.json(WebJson.obj("ok" to auth.revokeSession(body["id"] as? String ?: "")))
            }
            "tokens" -> when (path.getOrNull(2)) {
                null -> auth.createToken(body["name"] as? String ?: "")
                    ?.let { WebResponse.json(WebJson.obj("token" to it)) } ?: WebResponse.error(409, "too_many_tokens")
                "delete" -> WebResponse.json(WebJson.obj("ok" to auth.deleteToken(body["id"] as? String ?: "")))
                else -> WebResponse.error(404, "not_found")
            }
            else -> WebResponse.error(404, "not_found")
        }
    }

    // ─── Chats and messages ─────────────────────────────────────────────

    private fun chats(request: WebRequest, path: List<String>, post: Boolean): WebResponse {
        val jid = path.getOrNull(1) ?: return chatList(request)
        val source = dataFor(request) ?: return WebResponse.error(404, "account_not_found")
        val chat = source.chat(jid) ?: return WebResponse.error(404, "chat_not_found")
        // Another account's chats can be read; writing needs that account to be in use.
        if (post && source !== data) return WebResponse.error(409, "account_inactive")
        return when (path.getOrNull(2)) {
            "messages" -> WebResponse.json(source.messagesJson(chat,
                request.query["beforeSort"]?.toLongOrNull(), request.query["beforeId"]?.toLongOrNull(),
                request.query["after"]?.toLongOrNull(),
                request.query["limit"]?.toIntOrNull() ?: 60))
            "send" -> if (post) send(request, chat) else WebResponse.error(405, "method")
            "react" -> if (post) react(request, chat) else WebResponse.error(405, "method")
            "delete" -> if (post) deleteMessage(request, chat) else WebResponse.error(405, "method")
            "open" -> if (post) openOnPhone(request, chat) else WebResponse.error(405, "method")
            else -> WebResponse.error(404, "not_found")
        }
    }

    /** The chat list; with the merged inbox, the chats of every account, newest first. */
    private fun chatList(request: WebRequest): WebResponse {
        val query = request.query["q"]
        val all = if (merge) accounts.list().filter { !it.loggedOut } else emptyList()
        if (all.size < 2) return WebResponse.json(data.chatsJson(query))
        val items = ArrayList<Pair<Long, String>>()
        for (account in all) {
            val source = if (account.active) data else otherData.getOrPut(account.id) { WebData(app, account) }
            try { items.addAll(source.chatItems(query, account.id to account.name)) } catch (_: Throwable) {}
        }
        items.sortByDescending { it.first }
        return WebResponse.json(items.joinToString(",", "{\"chats\":[", "]}") { it.second })
    }

    /** GET: the accounts on this phone. POST /api/accounts/switch {id}: switch WhatsApp to one. */
    private fun accountsRoute(request: WebRequest, path: List<String>, post: Boolean): WebResponse {
        if (path.getOrNull(1) == "switch") {
            if (!post) return WebResponse.error(405, "method")
            val id = WebJson.parseObject(request.bodyText())["id"] as? String
            val account = accounts.byId(id) ?: return WebResponse.error(404, "account_not_found")
            if (account.active) return WebResponse.json("{\"ok\":true,\"already\":true}")
            if (account.loggedOut) return WebResponse.error(409, "account_logged_out")
            XposedBridge.log("$TAG account switch requested")
            return if (accounts.switchTo(account)) WebResponse.json("{\"ok\":true}")
            else WebResponse.error(503, "cannot_switch")
        }
        val list = accounts.list().map {
            linkedMapOf("id" to it.id, "name" to it.name, "phone" to it.phone, "active" to it.active, "loggedOut" to it.loggedOut)
        }
        return WebResponse.json(WebJson.obj("accounts" to list, "merged" to merge, "app" to app.packageName))
    }

    private fun send(request: WebRequest, chat: WebData.Chat): WebResponse {
        val body = WebJson.parseObject(request.bodyText())
        val text = (body["text"] as? String)?.trim().orEmpty()
        if (text.isEmpty() || text.length > MAX_TEXT) return sendRefused(400, "text_length")
        if (!WebSender.available) return sendRefused(503, "send_unavailable")
        val quoted = (body["replyTo"] as? Double)?.toLong()?.let {
            data.messageKey(chat, it) ?: return sendRefused(404, "message_not_found")
        }
        synchronized(sendTimes) {
            val now = System.currentTimeMillis()
            while (sendTimes.isNotEmpty() && now - sendTimes.first() > 60_000) sendTimes.removeFirst()
            if (sendTimes.size >= MAX_SENDS_PER_MINUTE) return sendRefused(429, "slow_down")
            sendTimes.addLast(now)
        }
        val before = data.maxMessageId()
        if (!WebSender.sendText(data.sendTarget(chat), text, quoted)) return sendError()
        // WhatsApp stores the message a moment later; wait briefly so the page can say it worked.
        var stored = false
        for (i in 0 until 20) {
            if (data.sentAfter(before, text)) { stored = true; break }
            try { Thread.sleep(100) } catch (_: InterruptedException) { break }
        }
        // Never the text or the chat: only whether WhatsApp stored the message.
        XposedBridge.log("$TAG send ${if (stored) "stored" else "not stored after 2 s"}")
        return WebResponse.json(WebJson.obj("ok" to true, "stored" to stored))
    }

    private fun sendRefused(status: Int, code: String): WebResponse {
        XposedBridge.log("$TAG send refused: $code")
        return WebResponse.error(status, code)
    }

    /** Says that sending failed and, as a short code, at which step. */
    private fun sendError(): WebResponse =
        WebResponse.json(WebJson.obj("error" to "send_failed", "step" to WebSender.lastError), 503)

    /** Body {id, emoji}: puts the reaction on a message; an empty emoji takes it away. */
    private fun react(request: WebRequest, chat: WebData.Chat): WebResponse {
        val body = WebJson.parseObject(request.bodyText())
        val id = (body["id"] as? Double)?.toLong() ?: return WebResponse.error(400, "id")
        val emoji = (body["emoji"] as? String).orEmpty()
        if (emoji.length > 16) return WebResponse.error(400, "emoji")
        if (!WebSender.canReact) return WebResponse.error(503, "react_unavailable")
        val key = data.messageKey(chat, id) ?: return WebResponse.error(404, "message_not_found")
        return if (WebSender.react(key, emoji)) WebResponse.json("{\"ok\":true}") else sendError()
    }

    /** Body {id, everyone}: deletes a message for this phone, or for everyone (own messages only). */
    private fun deleteMessage(request: WebRequest, chat: WebData.Chat): WebResponse {
        val body = WebJson.parseObject(request.bodyText())
        val id = (body["id"] as? Double)?.toLong() ?: return WebResponse.error(400, "id")
        val everyone = body["everyone"] == true
        if (!WebSender.canDelete) return WebResponse.error(503, "delete_unavailable")
        val key = data.messageKey(chat, id) ?: return WebResponse.error(404, "message_not_found")
        if (everyone && !key.fromMe) return WebResponse.error(403, "not_your_message")
        return if (WebSender.delete(key, everyone)) WebResponse.json("{\"ok\":true}") else sendError()
    }

    /**
     * Opens the chat on the phone: a one-to-one chat with the typed text filled in (WhatsApp's own
     * link), a group chat as it is. Android does not let WhatsApp open a screen from the background,
     * so the WAEX app opens it (or shows a notification to tap).
     */
    private fun openOnPhone(request: WebRequest, chat: WebData.Chat): WebResponse {
        val text = (WebJson.parseObject(request.bodyText())["text"] as? String).orEmpty().take(MAX_TEXT)
        val phone = chat.jid.takeIf { it.endsWith("@s.whatsapp.net") }?.substringBefore('@')?.filter(Char::isDigit)
        val intent = if (!phone.isNullOrEmpty()) {
            Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(text)}"))
                .setPackage(app.packageName)
        } else {
            val conversation = XposedHelpers.findClassIfExists("com.whatsapp.Conversation", app.classLoader)
                ?: return WebResponse.error(400, "not_a_phone_chat")
            Intent(app, conversation).putExtra("jid", data.sendTarget(chat))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (launchOnPhone(intent, data.nameOf(chat.jid, chat.subject))) WebResponse.json("{\"ok\":true}")
        else WebResponse.error(503, "cannot_open")
    }

    private fun launchOnPhone(intent: Intent, label: String): Boolean {
        try {
            val pending = android.app.PendingIntent.getActivity(app, 0x5745, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
            val extras = android.os.Bundle()
            extras.putParcelable("intent", pending)
            extras.putString("label", label)
            val answer = app.contentResolver.call(Uri.parse("content://com.waenhancer.hookprovider"), "web_launch", null, extras)
            if (answer?.getBoolean("success", false) == true) return true
        } catch (_: Throwable) {
        }
        return try {
            app.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Long poll: answers when the database changes, or after about 25 seconds. */
    private fun updates(request: WebRequest): WebResponse {
        val since = request.query["rev"]?.toLongOrNull()
        var rev = data.revision()
        if (since != null && since == rev && waiting.incrementAndGet() <= MAX_WAITING) {
            try {
                val deadline = System.currentTimeMillis() + 25_000
                while (rev == since && System.currentTimeMillis() < deadline) {
                    Thread.sleep(500)
                    rev = data.revision()
                }
            } catch (_: InterruptedException) {
            } finally {
                waiting.decrementAndGet()
            }
        } else if (since != null && since == rev) {
            waiting.decrementAndGet()
            try { Thread.sleep(2_000) } catch (_: InterruptedException) {}
        }
        return WebResponse.json(WebJson.obj("rev" to rev, "canSend" to WebSender.available))
    }

    // ─── Media ──────────────────────────────────────────────────────────

    private fun media(request: WebRequest, id: Long?): WebResponse {
        val source = dataFor(request) ?: return WebResponse.error(404, "not_found")
        val media = id?.let(source::media) ?: return WebResponse.error(404, "not_found")
        val size = media.file.length()
        val inline = INLINE_TYPES.any { media.mime.startsWith(it) }
        val type = if (inline) media.mime else "application/octet-stream"
        val range = WebHttp.parseRange(request.header("range"), size)
        val start = range?.first ?: 0L
        val count = if (range != null) range.last - range.first + 1 else size
        val response = WebResponse(if (range != null) 206 else 200, type, length = count, stream = { out ->
            copyRange(media.file, start, count, out)
        })
        response.header("Accept-Ranges", "bytes")
        response.header("Cache-Control", "private, max-age=3600")
        // Media comes from other people. It must never run as a page of this site.
        response.header("Content-Security-Policy", "sandbox; default-src 'none'")
        val safeName = media.name.replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_").take(80).ifEmpty { "file" }
        response.header("Content-Disposition",
            (if (inline) "inline" else "attachment") + "; filename*=UTF-8''" + Uri.encode(safeName))
        if (range != null) response.header("Content-Range", "bytes ${range.first}-${range.last}/$size")
        return response
    }

    private fun copyRange(file: File, start: Long, count: Long, out: java.io.OutputStream) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(64 * 1024)
            var left = count
            while (left > 0) {
                val n = raf.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) break
                out.write(buffer, 0, n)
                left -= n
            }
        }
    }

    private fun thumb(request: WebRequest, id: Long?): WebResponse {
        val source = dataFor(request) ?: return WebResponse.error(404, "not_found")
        val bytes = id?.let(source::thumbnail) ?: return WebResponse.error(404, "not_found")
        return WebResponse(200, "image/jpeg", bytes).header("Cache-Control", "private, max-age=86400")
    }

    private fun avatar(request: WebRequest, jid: String?): WebResponse {
        val source = dataFor(request) ?: return WebResponse.error(404, "not_found")
        val file = jid?.let(source::avatar) ?: return WebResponse.error(404, "not_found")
        return WebResponse(200, "image/jpeg", file.readBytes()).header("Cache-Control", "private, max-age=600")
    }

    companion object {
        private const val TAG = "[WAEX][Web]"
        const val PREF_KEY = "web_access"
        const val PREF_PORT = "web_access_port"
        const val PREF_WIFI_ONLY = "web_wifi_only"
        const val PREF_MERGE = "web_merge_accounts"
        const val DEFAULT_PORT = 8377
        private const val CHANNEL = "waex_web"
        private const val NOTIFY_CODE = 0x57454201
        private const val MAX_WORKERS = 24
        private const val MAX_WAITING = 8
        private const val MAX_TEXT = 8000
        private const val MAX_SENDS_PER_MINUTE = 30
        private val INLINE_TYPES = listOf("image/jpeg", "image/png", "image/webp", "image/gif", "video/", "audio/")
        private val ASSETS = mapOf(
            "index.html" to "text/html; charset=utf-8",
            "app.js" to "text/javascript; charset=utf-8",
            "app.css" to "text/css; charset=utf-8",
            "api.txt" to "text/plain; charset=utf-8"
        )
        private const val CSP = "default-src 'self'; img-src 'self' data: blob:; media-src 'self' blob:; " +
            "style-src 'self' 'unsafe-inline'; object-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'"
    }
}
