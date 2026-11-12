package com.waenhancer.web

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder

/** One parsed HTTP request. Header names are lower case. */
class WebRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    val headers: Map<String, String>,
    val body: ByteArray,
    /** A large upload, kept on disk instead of in [body]; deleted once the request is answered. */
    val file: java.io.File? = null
) {
    fun header(name: String): String? = headers[name.lowercase()]

    fun cookie(name: String): String? {
        val raw = header("cookie") ?: return null
        for (part in raw.split(';')) {
            val eq = part.indexOf('=')
            if (eq > 0 && part.substring(0, eq).trim() == name) return part.substring(eq + 1).trim()
        }
        return null
    }

    /** Path split on '/', each part percent-decoded. "/api/chats/a%40b" gives [api, chats, a@b]. */
    val segments: List<String> by lazy {
        path.split('/').filter { it.isNotEmpty() }.map { WebHttp.decode(it, plusIsSpace = false) }
    }

    fun bodyText(): String = String(body, Charsets.UTF_8)
}

class WebResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray = ByteArray(0),
    val headers: MutableMap<String, String> = LinkedHashMap(),
    /** Set for large bodies: [length] bytes are written by [stream] instead of [body]. */
    val length: Long = -1,
    val stream: ((OutputStream) -> Unit)? = null
) {
    fun header(name: String, value: String): WebResponse {
        headers[name] = value
        return this
    }

    companion object {
        fun json(json: String, status: Int = 200) =
            WebResponse(status, "application/json; charset=utf-8", json.toByteArray(Charsets.UTF_8))

        fun text(text: String, status: Int = 200, type: String = "text/plain; charset=utf-8") =
            WebResponse(status, type, text.toByteArray(Charsets.UTF_8))

        fun error(status: Int, code: String) = json("""{"error":${WebJson.quote(code)}}""", status)
    }
}

object WebHttp {
    const val MAX_HEADER_BYTES = 16 * 1024
    const val MAX_BODY_BYTES = 256 * 1024
    /** Photos and videos sent from the page; WhatsApp itself sends videos up to about this size. */
    const val MAX_UPLOAD_BYTES = 100 * 1024 * 1024
    private val UPLOAD_PATH = Regex("^/api/chats/[^/]+/media$")

    class BadRequest(val status: Int, message: String) : Exception(message)

    /** Reads one request; null when the peer closed the connection before sending anything. */
    fun read(input: InputStream, uploadDir: java.io.File? = null): WebRequest? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val requestLine = lines[0].split(' ')
        if (requestLine.size != 3 || !requestLine[2].startsWith("HTTP/1.")) throw BadRequest(400, "request line")
        val method = requestLine[0]
        if (method != "GET" && method != "POST" && method != "HEAD") throw BadRequest(405, "method")
        val target = requestLine[1]
        if (!target.startsWith("/")) throw BadRequest(400, "target")

        val headers = HashMap<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) continue
            val colon = line.indexOf(':')
            if (colon <= 0) throw BadRequest(400, "header")
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        if (headers.containsKey("transfer-encoding")) throw BadRequest(411, "length required")

        val q = target.indexOf('?')
        val path = if (q < 0) target else target.substring(0, q)
        if (path.contains("..") || path.contains('\u0000')) throw BadRequest(400, "path")
        val query = if (q < 0) emptyMap() else parseQuery(target.substring(q + 1))

        var body = ByteArray(0)
        val length = headers["content-length"]
        if (length != null) {
            val n = length.toIntOrNull() ?: throw BadRequest(400, "content-length")
            if (n < 0) throw BadRequest(400, "content-length")
            if (method == "POST" && uploadDir != null && UPLOAD_PATH.matches(path)) {
                if (n > MAX_UPLOAD_BYTES) throw BadRequest(413, "too large")
                return WebRequest(method, path, query, headers, body, saveUpload(input, n, uploadDir))
            }
            if (n > MAX_BODY_BYTES) throw BadRequest(413, "too large")
            body = ByteArray(n)
            var read = 0
            while (read < n) {
                val r = input.read(body, read, n - read)
                if (r < 0) throw BadRequest(400, "short body")
                read += r
            }
        } else if (method == "POST") {
            throw BadRequest(411, "length required")
        }
        return WebRequest(method, path, query, headers, body)
    }

    /** Copies exactly [n] bytes into a new file in [dir]; nothing is left behind on failure. */
    private fun saveUpload(input: InputStream, n: Int, dir: java.io.File): java.io.File {
        dir.mkdirs()
        val file = java.io.File.createTempFile("waex_web_", ".upload", dir)
        try {
            file.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var left = n
                while (left > 0) {
                    val r = input.read(buffer, 0, minOf(buffer.size, left))
                    if (r < 0) throw BadRequest(400, "short body")
                    out.write(buffer, 0, r)
                    left -= r
                }
            }
        } catch (t: Throwable) {
            file.delete()
            throw t
        }
        return file
    }

    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream(512)
        var matched = 0
        while (true) {
            val b = input.read()
            if (b < 0) {
                if (buffer.size() == 0) return null
                throw BadRequest(400, "short head")
            }
            buffer.write(b)
            matched = when {
                (matched == 0 || matched == 2) && b == '\r'.code -> matched + 1
                (matched == 1 || matched == 3) && b == '\n'.code -> matched + 1
                b == '\r'.code -> 1
                else -> 0
            }
            if (matched == 4) break
            if (buffer.size() > MAX_HEADER_BYTES) throw BadRequest(431, "head too large")
        }
        return String(buffer.toByteArray(), 0, buffer.size() - 4, Charsets.ISO_8859_1)
    }

    fun parseQuery(raw: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (pair in raw.split('&')) {
            if (pair.isEmpty()) continue
            val eq = pair.indexOf('=')
            val key = decode(if (eq < 0) pair else pair.substring(0, eq), true)
            result[key] = if (eq < 0) "" else decode(pair.substring(eq + 1), true)
        }
        return result
    }

    fun decode(value: String, plusIsSpace: Boolean): String = try {
        URLDecoder.decode(if (plusIsSpace) value else value.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        throw BadRequest(400, "encoding")
    }

    private val REASONS = mapOf(
        200 to "OK", 204 to "No Content", 206 to "Partial Content", 304 to "Not Modified",
        400 to "Bad Request", 401 to "Unauthorized", 403 to "Forbidden", 404 to "Not Found",
        405 to "Method Not Allowed", 409 to "Conflict", 411 to "Length Required",
        413 to "Payload Too Large", 416 to "Range Not Satisfiable", 429 to "Too Many Requests",
        431 to "Request Header Fields Too Large", 500 to "Internal Server Error",
        503 to "Service Unavailable"
    )

    /** Writes [response]; the body is left out for HEAD requests. */
    fun write(out: OutputStream, response: WebResponse, headOnly: Boolean = false) {
        val length = if (response.stream != null) response.length else response.body.size.toLong()
        val head = StringBuilder(256)
        head.append("HTTP/1.1 ").append(response.status).append(' ')
            .append(REASONS[response.status] ?: "Status").append("\r\n")
        head.append("Content-Type: ").append(response.contentType).append("\r\n")
        head.append("Content-Length: ").append(length).append("\r\n")
        head.append("Connection: close\r\n")
        head.append("X-Content-Type-Options: nosniff\r\n")
        head.append("X-Frame-Options: DENY\r\n")
        head.append("Referrer-Policy: no-referrer\r\n")
        if (!response.headers.containsKey("Cache-Control")) head.append("Cache-Control: no-store\r\n")
        for ((name, value) in response.headers) {
            // Header values come from our own code, but never let a line break through.
            head.append(name).append(": ").append(value.replace("\r", "").replace("\n", "")).append("\r\n")
        }
        head.append("\r\n")
        out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
        if (!headOnly) {
            if (response.stream != null) response.stream.invoke(out) else out.write(response.body)
        }
        out.flush()
    }

    /** A "bytes=a-b" range resolved against [size]; null when absent or unusable. */
    fun parseRange(header: String?, size: Long): LongRange? {
        if (header == null || !header.startsWith("bytes=") || header.contains(',') || size <= 0) return null
        val spec = header.substring(6).trim()
        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val first = spec.substring(0, dash).trim()
        val last = spec.substring(dash + 1).trim()
        return try {
            if (first.isEmpty()) {
                val suffix = last.toLong()
                if (suffix <= 0) null else (maxOf(0, size - suffix)..size - 1)
            } else {
                val start = first.toLong()
                val end = if (last.isEmpty()) size - 1 else minOf(last.toLong(), size - 1)
                if (start < 0 || start > end) null else (start..end)
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
