package com.waenhancer.core.transcription;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Speech-to-text requests for voice notes. Plain Java (no Android types) so the request
 * building and response parsing can be unit-tested on the JVM.
 *
 * <p>Supported providers: {@code Groq} and {@code Custom} (OpenAI-compatible
 * {@code /audio/transcriptions}), and {@code AssemblyAI} (upload, create, poll).</p>
 */
public final class TranscriptionClient {

    public static final String PROVIDER_GROQ = "Groq";
    public static final String PROVIDER_ASSEMBLY = "AssemblyAI";
    public static final String PROVIDER_CUSTOM = "Custom";

    /** Kept below the ~1 MB Binder transaction limit the audio crosses on its way here. */
    public static final int MAX_AUDIO_BYTES = 800 * 1024;

    static final String GROQ_ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions";
    static final String GROQ_DEFAULT_MODEL = "whisper-large-v3-turbo";
    static final String CUSTOM_DEFAULT_MODEL = "whisper-1";
    static final String ASSEMBLY_BASE = "https://api.assemblyai.com/v2";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final long ASSEMBLY_POLL_MS = 1_500;
    private static final long ASSEMBLY_MAX_WAIT_MS = 180_000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    /** Which service to call and with what credentials. */
    public static final class Config {
        public final String provider;
        public final String url;
        public final String apiKey;
        public final String model;

        public Config(String provider, String url, String apiKey, String model) {
            this.provider = provider == null ? PROVIDER_GROQ : provider.trim();
            this.url = url == null ? "" : url.trim();
            this.apiKey = apiKey == null ? "" : apiKey.trim();
            this.model = model == null ? "" : model.trim();
        }
    }

    /** A failure the user should see, with a message that never contains the API key. */
    public static final class TranscriptionException extends Exception {
        private static final long serialVersionUID = 1L;

        public TranscriptionException(String message) { super(message); }
    }

    /** Minimal HTTP seam so tests can run without a network. */
    interface Transport {
        Response send(String method, String url, Map<String, String> headers, byte[] body)
                throws IOException;
    }

    static final class Response {
        final int code;
        final String body;

        Response(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    private final Transport transport;

    public TranscriptionClient() { this(TranscriptionClient::httpSend); }

    TranscriptionClient(Transport transport) { this.transport = transport; }

    /** Returns the transcript text, which may be empty for silent audio. */
    public String transcribe(Config config, byte[] audio, String fileName)
            throws TranscriptionException, IOException {
        if (audio == null || audio.length == 0) throw new TranscriptionException("Empty audio file");
        if (audio.length > MAX_AUDIO_BYTES) throw new TranscriptionException("Voice note is too long");
        if (config.apiKey.isEmpty()) {
            throw new TranscriptionException("No API key set for " + config.provider);
        }
        switch (config.provider) {
            case PROVIDER_ASSEMBLY:
                return transcribeAssembly(config, audio);
            case PROVIDER_CUSTOM:
                if (config.url.isEmpty()) throw new TranscriptionException("No custom endpoint URL set");
                return transcribeOpenAi(resolveOpenAiEndpoint(config.url), config.apiKey,
                        config.model.isEmpty() ? CUSTOM_DEFAULT_MODEL : config.model, audio, fileName);
            case PROVIDER_GROQ:
            default:
                return transcribeOpenAi(GROQ_ENDPOINT, config.apiKey, GROQ_DEFAULT_MODEL, audio, fileName);
        }
    }

    /**
     * Accepts either a base URL ({@code https://host/v1}) or the full endpoint
     * ({@code https://host/v1/audio/transcriptions}).
     */
    static String resolveOpenAiEndpoint(String url) throws TranscriptionException {
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            throw new TranscriptionException("Endpoint URL must start with https://");
        }
        if (lower.endsWith("/audio/transcriptions")) return trimmed;
        return trimmed + "/audio/transcriptions";
    }

    private String transcribeOpenAi(String endpoint, String apiKey, String model, byte[] audio,
                                    String fileName) throws TranscriptionException, IOException {
        String boundary = "----waex" + UUID.randomUUID().toString().replace("-", "");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("model", model);
        fields.put("response_format", "json");
        byte[] body = multipart(boundary, fields, "file", uploadName(fileName), "audio/ogg", audio);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        Response response = transport.send("POST", endpoint, headers, body);
        Object json = parseJsonOrNull(response.body);
        if (response.code / 100 != 2) throw new TranscriptionException(describeError(response, json));
        Object text = json instanceof Map ? ((Map<?, ?>) json).get("text") : null;
        if (!(text instanceof String)) {
            // Some servers answer response_format=json with plain text anyway.
            if (json == null && response.body != null && !response.body.trim().startsWith("{")) {
                return response.body.trim();
            }
            throw new TranscriptionException("Unexpected response from transcription server");
        }
        return ((String) text).trim();
    }

    private String transcribeAssembly(Config config, byte[] audio)
            throws TranscriptionException, IOException {
        Map<String, String> auth = new LinkedHashMap<>();
        auth.put("Authorization", config.apiKey);

        Map<String, String> uploadHeaders = new LinkedHashMap<>(auth);
        uploadHeaders.put("Content-Type", "application/octet-stream");
        Response upload = transport.send("POST", ASSEMBLY_BASE + "/upload", uploadHeaders, audio);
        Object uploadJson = parseJsonOrNull(upload.body);
        if (upload.code / 100 != 2) throw new TranscriptionException(describeError(upload, uploadJson));
        String uploadUrl = stringField(uploadJson, "upload_url");
        if (uploadUrl == null) throw new TranscriptionException("AssemblyAI upload failed");

        Map<String, String> jsonHeaders = new LinkedHashMap<>(auth);
        jsonHeaders.put("Content-Type", "application/json");
        String request = "{\"audio_url\":" + jsonQuote(uploadUrl) + ",\"language_detection\":true}";
        Response created = transport.send("POST", ASSEMBLY_BASE + "/transcript", jsonHeaders,
                request.getBytes(StandardCharsets.UTF_8));
        Object createdJson = parseJsonOrNull(created.body);
        if (created.code / 100 != 2) throw new TranscriptionException(describeError(created, createdJson));
        String id = stringField(createdJson, "id");
        if (id == null || !id.matches("[A-Za-z0-9_-]+")) {
            throw new TranscriptionException("AssemblyAI did not return a transcript id");
        }

        long deadline = System.currentTimeMillis() + ASSEMBLY_MAX_WAIT_MS;
        while (true) {
            Response poll = transport.send("GET", ASSEMBLY_BASE + "/transcript/" + id, auth, null);
            Object pollJson = parseJsonOrNull(poll.body);
            if (poll.code / 100 != 2) throw new TranscriptionException(describeError(poll, pollJson));
            String status = stringField(pollJson, "status");
            if ("completed".equals(status)) {
                String text = stringField(pollJson, "text");
                return text == null ? "" : text.trim();
            }
            if ("error".equals(status)) {
                String error = stringField(pollJson, "error");
                throw new TranscriptionException("AssemblyAI: " + (error == null ? "failed" : shorten(error)));
            }
            if (System.currentTimeMillis() > deadline) {
                throw new TranscriptionException("AssemblyAI took too long");
            }
            try {
                Thread.sleep(ASSEMBLY_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TranscriptionException("Transcription cancelled");
            }
        }
    }

    static String uploadName(String fileName) {
        // Groq validates the extension; an Ogg/Opus voice note is accepted as .ogg.
        String base = fileName == null ? "" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        if (base.isEmpty()) base = "voice-note";
        return base + ".ogg";
    }

    static byte[] multipart(String boundary, Map<String, String> fields, String fileField,
                            String fileName, String contentType, byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 512);
        for (Map.Entry<String, String> field : fields.entrySet()) {
            write(out, "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + field.getKey() + "\"\r\n\r\n"
                    + field.getValue() + "\r\n");
        }
        write(out, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fileField + "\"; filename=\""
                + fileName + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n");
        out.write(data, 0, data.length);
        write(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void write(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
    }

    static String describeError(Response response, Object json) {
        String detail = null;
        if (json instanceof Map) {
            Object error = ((Map<?, ?>) json).get("error");
            if (error instanceof Map) detail = stringField(error, "message");
            else if (error instanceof String) detail = (String) error;
            if (detail == null) detail = stringField(json, "message");
        }
        String prefix;
        switch (response.code) {
            case 401:
            case 403:
                prefix = "API key rejected";
                break;
            case 404:
                prefix = "Endpoint not found (check the URL)";
                break;
            case 413:
                prefix = "Voice note too large for the server";
                break;
            case 429:
                prefix = "Rate limit reached";
                break;
            default:
                prefix = "Server error " + response.code;
        }
        return detail == null || detail.isEmpty() ? prefix : prefix + ": " + shorten(detail);
    }

    private static String shorten(String text) {
        String single = text.replace('\n', ' ').replace('\r', ' ').trim();
        return single.length() <= 160 ? single : single.substring(0, 160) + "…";
    }

    private static String stringField(Object json, String key) {
        if (!(json instanceof Map)) return null;
        Object value = ((Map<?, ?>) json).get(key);
        return value instanceof String ? (String) value : null;
    }

    static String jsonQuote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        return out.append('"').toString();
    }

    static Object parseJsonOrNull(String text) {
        if (text == null) return null;
        try {
            return new MiniJson(text).parseDocument();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Response httpSend(String method, String url, Map<String, String> headers,
                                     byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body);
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            return new Response(code, stream == null ? "" : readLimited(stream));
        } finally {
            connection.disconnect();
        }
    }

    private static String readLimited(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > MAX_RESPONSE_BYTES) throw new IOException("Response too large");
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Small strict JSON reader: objects become maps, arrays lists, numbers doubles. */
    static final class MiniJson {
        private final String s;
        private int i;

        MiniJson(String s) { this.s = s; }

        Object parseDocument() {
            Object value = value();
            skipWs();
            if (i != s.length()) throw new IllegalArgumentException("Trailing data");
            return value;
        }

        private Object value() {
            skipWs();
            if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
            char c = s.charAt(i);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }

        private Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            i++;
            skipWs();
            if (peek('}')) { i++; return map; }
            while (true) {
                skipWs();
                String key = string();
                skipWs();
                expect(':');
                map.put(key, value());
                skipWs();
                if (peek(',')) { i++; continue; }
                expect('}');
                return map;
            }
        }

        private List<Object> array() {
            List<Object> list = new java.util.ArrayList<>();
            i++;
            skipWs();
            if (peek(']')) { i++; return list; }
            while (true) {
                list.add(value());
                skipWs();
                if (peek(',')) { i++; continue; }
                expect(']');
                return list;
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                if (i >= s.length()) throw new IllegalArgumentException("Unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                if (i >= s.length()) throw new IllegalArgumentException("Bad escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    case '/': out.append('/'); break;
                    case 'b': out.append('\b'); break;
                    case 'f': out.append('\f'); break;
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case 'u':
                        if (i + 4 > s.length()) throw new IllegalArgumentException("Bad unicode escape");
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: throw new IllegalArgumentException("Bad escape");
                }
            }
        }

        private Double number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("Unexpected character");
            return Double.parseDouble(s.substring(start, i));
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private boolean peek(char c) { return i < s.length() && s.charAt(i) == c; }

        private void expect(char c) {
            if (!peek(c)) throw new IllegalArgumentException("Expected " + c);
            i++;
        }
    }
}
