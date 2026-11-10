package com.waenhancer.web

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Where the web server keeps its secrets (hashes only). Backed by private app storage. */
interface WebStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/**
 * Sign-in for the web interface.
 *
 * A new browser asks for a pairing code. The code is shown in a notification on the phone,
 * works once, for two minutes, and only for the browser that asked. If a password is set it
 * is needed as well. A signed-in browser holds a random session token; only its SHA-256 hash
 * is stored. Scripts use API tokens, stored the same way.
 */
class WebAuth(
    private val store: WebStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: SecureRandom = SecureRandom()
) {
    class Session(val id: String, val created: Long, val lastSeen: Long, val label: String)
    class ApiToken(val id: String, val created: Long, val name: String)

    sealed interface CodeResult {
        /** [code] goes to the phone's notification, [pairId] back to the browser. */
        class Issued(val code: String, val pairId: String, val validSeconds: Int) : CodeResult
        /** Another pairing is in progress or the server is locked after wrong codes. */
        class Wait(val seconds: Int) : CodeResult
    }

    sealed interface ConfirmResult {
        class Ok(val token: String) : ConfirmResult
        object WrongCode : ConfirmResult
        object WrongPassword : ConfirmResult
        object Expired : ConfirmResult
        class Locked(val seconds: Int) : ConfirmResult
    }

    private class Pending(val code: String, val pairId: String, val expires: Long, var attempts: Int)

    private val lock = Any()
    private var pending: Pending? = null
    private var failures = 0
    private var failureWindowStart = 0L
    private var lockedUntil = 0L

    // ─── Pairing ────────────────────────────────────────────────────────

    fun requestCode(): CodeResult = synchronized(lock) {
        val now = clock()
        if (now < lockedUntil) return CodeResult.Wait(secondsUntil(lockedUntil, now))
        val current = pending
        if (current != null && now < current.expires) return CodeResult.Wait(secondsUntil(current.expires, now))
        val code = String.format("%06d", random.nextInt(1_000_000))
        val pairId = randomToken(18)
        pending = Pending(code, pairId, now + CODE_VALID_MS, 0)
        CodeResult.Issued(code, pairId, (CODE_VALID_MS / 1000).toInt())
    }

    /** The code a browser is waiting for right now and the seconds it stays valid, for the phone's own screen. */
    fun pendingCode(): Pair<String, Int>? = synchronized(lock) {
        val now = clock()
        val current = pending ?: return null
        if (now >= current.expires) null else current.code to secondsUntil(current.expires, now)
    }

    fun confirm(pairId: String?, code: String?, password: String?, label: String): ConfirmResult = synchronized(lock) {
        val now = clock()
        if (now < lockedUntil) return ConfirmResult.Locked(secondsUntil(lockedUntil, now))
        val current = pending
        if (current == null || now >= current.expires || pairId == null || !same(current.pairId, pairId)) {
            return ConfirmResult.Expired
        }
        val digits = code.orEmpty().filter { it in '0'..'9' }
        if (!same(current.code, digits)) {
            current.attempts++
            if (current.attempts >= MAX_CODE_ATTEMPTS) pending = null
            noteFailure(now)
            return ConfirmResult.WrongCode
        }
        if (passwordSet() && !checkPassword(password.orEmpty())) {
            // The code stays usable so a typo in the password does not need a new code.
            current.attempts++
            if (current.attempts >= MAX_CODE_ATTEMPTS) pending = null
            noteFailure(now)
            return ConfirmResult.WrongPassword
        }
        pending = null
        failures = 0
        ConfirmResult.Ok(newSession(label, now))
    }

    private fun noteFailure(now: Long) {
        if (now - failureWindowStart > FAILURE_WINDOW_MS) {
            failureWindowStart = now
            failures = 0
        }
        failures++
        if (failures >= MAX_FAILURES) {
            lockedUntil = now + LOCK_MS
            failures = 0
            pending = null
        }
    }

    private fun secondsUntil(until: Long, now: Long): Int = ((until - now + 999) / 1000).toInt()

    // ─── Sessions ───────────────────────────────────────────────────────

    private class StoredSession(val hash: String, val created: Long, var lastSeen: Long, val label: String)

    private fun loadSessions(): MutableList<StoredSession> {
        val now = clock()
        val list = ArrayList<StoredSession>()
        for (line in store.get(KEY_SESSIONS).orEmpty().split('\n')) {
            val f = line.split('\t')
            if (f.size < 4) continue
            val created = f[1].toLongOrNull() ?: continue
            val lastSeen = f[2].toLongOrNull() ?: continue
            if (now - lastSeen > SESSION_IDLE_MS) continue
            list.add(StoredSession(f[0], created, lastSeen, f[3]))
        }
        return list
    }

    private fun saveSessions(list: List<StoredSession>) {
        store.put(KEY_SESSIONS, list.joinToString("\n") { "${it.hash}\t${it.created}\t${it.lastSeen}\t${it.label}" })
    }

    private fun newSession(label: String, now: Long): String {
        val token = randomToken(32)
        val list = loadSessions()
        list.add(StoredSession(sha256(token), now, now, clean(label)))
        while (list.size > MAX_SESSIONS) list.removeAt(0)
        saveSessions(list)
        return token
    }

    /** The session for a cookie value, or null. Keeps the "last seen" time roughly current. */
    fun session(token: String?): Session? = synchronized(lock) {
        if (token.isNullOrEmpty() || token.length > 128) return null
        val hash = sha256(token)
        val list = loadSessions()
        val found = list.firstOrNull { same(it.hash, hash) } ?: return null
        val now = clock()
        if (now - found.lastSeen > TOUCH_MS) {
            found.lastSeen = now
            saveSessions(list)
        }
        Session(found.hash.take(12), found.created, found.lastSeen, found.label)
    }

    fun sessions(): List<Session> = synchronized(lock) {
        loadSessions().map { Session(it.hash.take(12), it.created, it.lastSeen, it.label) }
    }

    fun revokeSession(id: String): Boolean = synchronized(lock) {
        val list = loadSessions()
        val removed = list.removeAll { it.hash.take(12) == id }
        if (removed) saveSessions(list)
        removed
    }

    fun revokeAllSessions() = synchronized(lock) { store.put(KEY_SESSIONS, null) }

    // ─── Password ───────────────────────────────────────────────────────

    fun passwordSet(): Boolean = !store.get(KEY_PASSWORD).isNullOrEmpty()

    /** Sets the password, or removes it with null. Returns false when it is too short or too long. */
    fun setPassword(password: String?): Boolean = synchronized(lock) {
        if (password == null) {
            store.put(KEY_PASSWORD, null)
            return true
        }
        if (password.length < MIN_PASSWORD || password.length > MAX_PASSWORD) return false
        val salt = ByteArray(16).also(random::nextBytes)
        val (algorithm, hash) = pbkdf2(password, salt, PBKDF2_ITERATIONS, null)
        store.put(KEY_PASSWORD, "$algorithm:$PBKDF2_ITERATIONS:${hex(salt)}:${hex(hash)}")
        true
    }

    fun checkPassword(password: String): Boolean {
        val stored = store.get(KEY_PASSWORD) ?: return false
        val f = stored.split(':')
        if (f.size != 4 || password.length > MAX_PASSWORD) return false
        return try {
            val iterations = f[1].toInt()
            val salt = unhex(f[2])
            val expected = unhex(f[3])
            MessageDigest.isEqual(expected, pbkdf2(password, salt, iterations, f[0]).second)
        } catch (_: Exception) {
            false
        }
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int, algorithm: String?): Pair<String, ByteArray> {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        // PBKDF2WithHmacSHA256 exists from Android 8; older phones use the SHA-1 variant.
        for (name in if (algorithm != null) listOf(algorithm) else listOf("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA1")) {
            try {
                return name to SecretKeyFactory.getInstance(name).generateSecret(spec).encoded
            } catch (_: java.security.NoSuchAlgorithmException) {
            }
        }
        error("PBKDF2 unavailable")
    }

    // ─── API tokens ─────────────────────────────────────────────────────

    private class StoredToken(val hash: String, val created: Long, val name: String)

    private fun loadTokens(): MutableList<StoredToken> {
        val list = ArrayList<StoredToken>()
        for (line in store.get(KEY_TOKENS).orEmpty().split('\n')) {
            val f = line.split('\t')
            if (f.size < 3) continue
            list.add(StoredToken(f[0], f[1].toLongOrNull() ?: continue, f[2]))
        }
        return list
    }

    private fun saveTokens(list: List<StoredToken>) {
        store.put(KEY_TOKENS, list.joinToString("\n") { "${it.hash}\t${it.created}\t${it.name}" })
    }

    /** Creates a token and returns it; it cannot be shown again. Null when there are too many. */
    fun createToken(name: String): String? = synchronized(lock) {
        val list = loadTokens()
        if (list.size >= MAX_TOKENS) return null
        val token = TOKEN_PREFIX + randomToken(32)
        list.add(StoredToken(sha256(token), clock(), clean(name).ifEmpty { "API" }))
        saveTokens(list)
        token
    }

    fun tokens(): List<ApiToken> = synchronized(lock) {
        loadTokens().map { ApiToken(it.hash.take(12), it.created, it.name) }
    }

    fun deleteToken(id: String): Boolean = synchronized(lock) {
        val list = loadTokens()
        val removed = list.removeAll { it.hash.take(12) == id }
        if (removed) saveTokens(list)
        removed
    }

    fun tokenValid(token: String?): Boolean = synchronized(lock) {
        if (token == null || !token.startsWith(TOKEN_PREFIX) || token.length > 128) return false
        val hash = sha256(token)
        loadTokens().any { same(it.hash, hash) }
    }

    // ─── Helpers ────────────────────────────────────────────────────────

    private fun randomToken(bytes: Int): String = hex(ByteArray(bytes).also(random::nextBytes))

    private fun sha256(value: String): String =
        hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    private fun hex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(HEX[(b.toInt() shr 4) and 0xf]).append(HEX[b.toInt() and 0xf])
        }
        return sb.toString()
    }

    private fun unhex(text: String): ByteArray {
        require(text.length % 2 == 0)
        return ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private fun same(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    /** Labels are stored tab separated, one per line. */
    private fun clean(label: String): String =
        label.filter { it >= ' ' && it != '\u007f' }.trim().take(60)

    companion object {
        const val COOKIE = "waex_session"
        const val TOKEN_PREFIX = "waex_"
        const val MIN_PASSWORD = 6
        const val MAX_PASSWORD = 128
        const val CODE_VALID_MS = 120_000L
        const val MAX_CODE_ATTEMPTS = 5
        const val MAX_FAILURES = 8
        const val FAILURE_WINDOW_MS = 10 * 60_000L
        const val LOCK_MS = 5 * 60_000L
        const val SESSION_IDLE_MS = 30L * 24 * 3600 * 1000
        const val TOUCH_MS = 10 * 60_000L
        const val MAX_SESSIONS = 10
        const val MAX_TOKENS = 10
        const val PBKDF2_ITERATIONS = 120_000
        private const val HEX = "0123456789abcdef"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_PASSWORD = "password"
        private const val KEY_TOKENS = "tokens"
    }
}
