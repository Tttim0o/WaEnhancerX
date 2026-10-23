package com.waenhancer.core.transcription.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal HTTP/1.1 request reading and multipart/form-data parsing for the local API server.
 * Plain Java with hard size limits, so it can be unit-tested and cannot be made to buffer
 * unbounded input.
 */
final class HttpMessages {

    static final int MAX_HEADER_BYTES = 16 * 1024;
    static final int MAX_BODY_BYTES = 25 * 1024 * 1024;

    static final class Request {
        final String method;
        final String path;
        /** Header names lower-cased. */
        final Map<String, String> headers;
        final byte[] body;

        Request(String method, String path, Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }

    /** A request the server answers with this status instead of processing it. */
    static final class BadRequest extends IOException {
        private static final long serialVersionUID = 1L;
        final int status;

        BadRequest(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private HttpMessages() {}

    static Request readRequest(InputStream in) throws IOException {
        byte[] head = readHead(in);
        String[] lines = new String(head, StandardCharsets.ISO_8859_1).split("\r\n");
        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 3 || !requestLine[2].startsWith("HTTP/1.")) {
            throw new BadRequest(400, "Malformed request line");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            headers.put(lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT), lines[i].substring(colon + 1).trim());
        }
        if (headers.containsKey("transfer-encoding")) throw new BadRequest(411, "Send Content-Length, not chunked");
        int length = 0;
        String declared = headers.get("content-length");
        if (declared != null) {
            try {
                length = Integer.parseInt(declared);
            } catch (NumberFormatException e) {
                throw new BadRequest(400, "Bad Content-Length");
            }
            if (length < 0) throw new BadRequest(400, "Bad Content-Length");
            if (length > MAX_BODY_BYTES) throw new BadRequest(413, "Body larger than 25 MB");
        }
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(body, offset, length - offset);
            if (read < 0) throw new BadRequest(400, "Body shorter than Content-Length");
            offset += read;
        }
        String target = requestLine[1];
        int query = target.indexOf('?');
        return new Request(requestLine[0].toUpperCase(Locale.ROOT), query >= 0 ? target.substring(0, query) : target,
                headers, body);
    }

    /** Bytes up to and excluding the blank line that ends the headers. */
    private static byte[] readHead(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int matched = 0; // Progress through "\r\n\r\n".
        while (true) {
            int b = in.read();
            if (b < 0) throw new BadRequest(400, "Connection closed before headers ended");
            out.write(b);
            if (out.size() > MAX_HEADER_BYTES) throw new BadRequest(431, "Headers too large");
            matched = (b == '\r' && (matched == 0 || matched == 2)) || (b == '\n' && (matched == 1 || matched == 3))
                    ? matched + 1 : (b == '\r' ? 1 : 0);
            if (matched == 4) {
                byte[] all = out.toByteArray();
                byte[] head = new byte[all.length - 4];
                System.arraycopy(all, 0, head, 0, head.length);
                return head;
            }
        }
    }

    /** One multipart/form-data part. */
    static final class Part {
        final String name;
        final String fileName;
        final byte[] data;

        Part(String name, String fileName, byte[] data) {
            this.name = name;
            this.fileName = fileName;
            this.data = data;
        }

        String text() {
            return new String(data, StandardCharsets.UTF_8).trim();
        }
    }

    /** Parts by field name; a later part with the same name replaces an earlier one. */
    static Map<String, Part> parseMultipart(String contentType, byte[] body) throws BadRequest {
        String boundary = boundaryOf(contentType);
        if (boundary == null) throw new BadRequest(400, "Expected multipart/form-data with a boundary");
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        Map<String, Part> parts = new LinkedHashMap<>();
        int position = indexOf(body, delimiter, 0);
        if (position < 0) throw new BadRequest(400, "Multipart boundary not found");
        while (true) {
            int afterDelimiter = position + delimiter.length;
            if (afterDelimiter + 2 > body.length) break;
            if (body[afterDelimiter] == '-' && body[afterDelimiter + 1] == '-') break; // Closing delimiter.
            int headStart = afterDelimiter + 2; // Skip CRLF.
            int headEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1), headStart);
            if (headEnd < 0) throw new BadRequest(400, "Malformed multipart part");
            String head = new String(body, headStart, headEnd - headStart, StandardCharsets.UTF_8);
            int dataStart = headEnd + 4;
            int next = indexOf(body, ("\r\n--" + boundary).getBytes(StandardCharsets.ISO_8859_1), dataStart);
            if (next < 0) throw new BadRequest(400, "Unterminated multipart part");
            byte[] data = new byte[next - dataStart];
            System.arraycopy(body, dataStart, data, 0, data.length);
            String name = dispositionParam(head, "name");
            if (name != null) parts.put(name, new Part(name, dispositionParam(head, "filename"), data));
            position = next + 2; // Points at "--boundary".
        }
        return parts;
    }

    static String boundaryOf(String contentType) {
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("multipart/form-data")) return null;
        for (String param : contentType.split(";")) {
            String trimmed = param.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("boundary=")) {
                String value = trimmed.substring("boundary=".length());
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return value.isEmpty() || value.length() > 200 ? null : value;
            }
        }
        return null;
    }

    private static String dispositionParam(String partHead, String param) {
        for (String line : partHead.split("\r\n")) {
            if (!line.toLowerCase(Locale.ROOT).startsWith("content-disposition:")) continue;
            for (String piece : line.split(";")) {
                String trimmed = piece.trim();
                if (trimmed.toLowerCase(Locale.ROOT).startsWith(param + "=")) {
                    String value = trimmed.substring(param.length() + 1);
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        }
        return null;
    }

    static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
