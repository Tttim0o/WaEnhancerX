package com.waenhancer.core.transcription.server;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.waenhancer.core.transcription.TranscriptionClient;
import com.waenhancer.core.transcription.TranscriptionLog;
import com.waenhancer.core.transcription.local.LocalModelStore;
import com.waenhancer.core.transcription.local.LocalModels;
import com.waenhancer.core.transcription.local.LocalTranscriber;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * OpenAI-compatible speech-to-text API backed by the on-device whisper models, for other apps
 * on this phone ("device": loopback only) or on the local network ("network").
 *
 * <ul>
 *   <li>{@code POST /v1/audio/transcriptions}: multipart {@code file}, optional {@code model}
 *       (a local model id; otherwise the chosen/suggested one), {@code language},
 *       {@code response_format} = json | text | verbose_json</li>
 *   <li>{@code GET /v1/models}: downloaded models</li>
 *   <li>{@code GET /health}: no key needed</li>
 * </ul>
 * Everything except /health needs {@code Authorization: Bearer <key>}.
 */
public final class LocalApiServer {

    public static final String MODE_OFF = "off";
    public static final String MODE_DEVICE = "device";
    public static final String MODE_NETWORK = "network";
    public static final int DEFAULT_PORT = 8765;
    private static final int SOCKET_TIMEOUT_MS = 30_000;

    private static final Object LOCK = new Object();
    @Nullable private static ServerSocket sSocket;
    @Nullable private static ExecutorService sWorkers;
    @Nullable private static volatile String sError;
    private static volatile String sRunningMode = MODE_OFF;
    private static volatile int sRunningPort;

    private LocalApiServer() {}

    /** Starts (or restarts) listening; returns false and records why when the port is unusable. */
    public static boolean start(@NonNull Context context, @NonNull String mode, int port, @NonNull String key) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        synchronized (LOCK) {
            stop();
            if (MODE_OFF.equals(mode)) return true;
            if (port < 1024 || port > 65535) {
                sError = "Port must be between 1024 and 65535";
                return false;
            }
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(true);
                InetAddress bind = MODE_NETWORK.equals(mode) ? null : InetAddress.getLoopbackAddress();
                socket.bind(new InetSocketAddress(bind, port), 8);
                sSocket = socket;
                // Two requests at once; whisper itself runs one at a time inside LocalTranscriber.
                ExecutorService workers = new ThreadPoolExecutor(2, 2, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                        new java.util.concurrent.ArrayBlockingQueue<>(4));
                sWorkers = workers;
                sError = null;
                sRunningMode = mode;
                sRunningPort = port;
                Thread acceptor = new Thread(() -> acceptLoop(app, socket, workers, key), "WAEX-ApiServer");
                acceptor.setDaemon(true);
                acceptor.start();
                TranscriptionLog.add(app, "app", "API server listening on "
                        + (MODE_NETWORK.equals(mode) ? "the local network" : "this device only") + ", port " + port);
                return true;
            } catch (IOException e) {
                sError = "Could not open port " + port + " (" + e.getClass().getSimpleName() + ")";
                TranscriptionLog.add(app, "app", "API server: " + sError);
                return false;
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            if (sSocket != null) {
                try {
                    sSocket.close();
                } catch (IOException ignored) {}
            }
            if (sWorkers != null) sWorkers.shutdownNow();
            sSocket = null;
            sWorkers = null;
            sRunningMode = MODE_OFF;
        }
    }

    public static boolean isRunning() {
        ServerSocket socket = sSocket;
        return socket != null && !socket.isClosed();
    }

    public static String runningMode() { return sRunningMode; }

    public static int runningPort() { return sRunningPort; }

    @Nullable
    public static String lastError() { return sError; }

    private static void acceptLoop(Context context, ServerSocket socket, ExecutorService workers, String key) {
        while (!socket.isClosed()) {
            Socket client;
            try {
                client = socket.accept();
            } catch (IOException e) {
                return; // Closed by stop().
            }
            try {
                workers.execute(() -> serve(context, client, key));
            } catch (RejectedExecutionException busy) {
                try (Socket rejected = client) {
                    respond(rejected.getOutputStream(), 503, error("Server busy, try again shortly"));
                } catch (IOException ignored) {}
            }
        }
    }

    private static void serve(Context context, Socket client, String key) {
        try (Socket socket = client) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            HttpMessages.Request request;
            try {
                request = HttpMessages.readRequest(socket.getInputStream());
            } catch (HttpMessages.BadRequest bad) {
                respond(out, bad.status, error(bad.getMessage()));
                return;
            }
            String from = socket.getInetAddress() == null ? "?" : socket.getInetAddress().getHostAddress();
            if ("GET".equals(request.method) && ("/health".equals(request.path) || "/".equals(request.path))) {
                respond(out, 200, "{\"status\":\"ok\"}");
                return;
            }
            if (!authorized(request.headers.get("authorization"), key)) {
                TranscriptionLog.add(context, "app", "API server: rejected request without a valid key from " + from);
                respond(out, 401, error("Missing or wrong API key (Authorization: Bearer <key>)"));
                return;
            }
            if ("GET".equals(request.method) && "/v1/models".equals(request.path)) {
                respond(out, 200, modelList(context));
            } else if ("POST".equals(request.method) && "/v1/audio/transcriptions".equals(request.path)) {
                transcribe(context, request, out, from);
            } else {
                respond(out, 404, error("Unknown endpoint " + request.method + " " + request.path));
            }
        } catch (Throwable t) {
            // A broken client must never take the WAEX app down.
            TranscriptionLog.add(context, "app", "API server request failed: " + t.getClass().getSimpleName());
        }
    }

    private static void transcribe(Context context, HttpMessages.Request request, OutputStream out, String from)
            throws IOException {
        Map<String, HttpMessages.Part> parts;
        try {
            parts = HttpMessages.parseMultipart(request.headers.get("content-type"), request.body);
        } catch (HttpMessages.BadRequest bad) {
            respond(out, bad.status, error(bad.getMessage()));
            return;
        }
        HttpMessages.Part file = parts.get("file");
        if (file == null || file.data.length == 0) {
            respond(out, 400, error("Missing form field: file"));
            return;
        }
        String language = parts.containsKey("language") ? parts.get("language").text().toLowerCase(java.util.Locale.ROOT) : "";
        if (!language.matches("[a-z]{2,3}")) language = "";
        String format = parts.containsKey("response_format") ? parts.get("response_format").text() : "json";
        String requested = parts.containsKey("model") ? parts.get("model").text() : "";
        String modelId = resolveModel(context, requested);
        TranscriptionLog.add(context, "app", "API server: request from " + from + ", "
                + Math.max(1, file.data.length / 1024) + " KB, model " + modelId);
        String text;
        try {
            text = LocalTranscriber.transcribe(context, modelId, file.data,
                    file.fileName != null ? file.fileName : "audio", language);
        } catch (TranscriptionClient.TranscriptionException e) {
            respond(out, 500, error(e.getMessage()));
            return;
        }
        if ("text".equals(format)) {
            respond(out, 200, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
        } else if ("verbose_json".equals(format)) {
            respond(out, 200, "{\"text\":" + TranscriptionClient.jsonQuote(text) + ",\"language\":"
                    + TranscriptionClient.jsonQuote(language.isEmpty() ? "auto" : language) + "}");
        } else {
            respond(out, 200, "{\"text\":" + TranscriptionClient.jsonQuote(text) + "}");
        }
    }

    /** A requested local model id when it is downloaded; otherwise the chosen or suggested one. */
    private static String resolveModel(Context context, String requested) {
        LocalModels.Model model = LocalModels.byId(requested);
        if (model != null && LocalModelStore.isDownloaded(context, model)) return model.id;
        String chosen = com.waenhancer.config.PreferenceStores.read(context, "local_model") instanceof String
                ? (String) com.waenhancer.config.PreferenceStores.read(context, "local_model") : "";
        String language = com.waenhancer.config.PreferenceStores.read(context, "transcription_language") instanceof String
                ? (String) com.waenhancer.config.PreferenceStores.read(context, "transcription_language") : "";
        return LocalTranscriber.effectiveModelId(context, chosen, language);
    }

    private static String modelList(Context context) {
        StringBuilder out = new StringBuilder("{\"object\":\"list\",\"data\":[");
        boolean first = true;
        for (LocalModels.Model model : LocalModels.ALL) {
            if (!LocalModelStore.isDownloaded(context, model)) continue;
            if (!first) out.append(',');
            first = false;
            out.append("{\"id\":").append(TranscriptionClient.jsonQuote(model.id))
                    .append(",\"object\":\"model\",\"owned_by\":\"waex\"}");
        }
        return out.append("]}").toString();
    }

    static boolean authorized(@Nullable String header, String key) {
        if (header == null || key == null || key.isEmpty() || !header.startsWith("Bearer ")) return false;
        byte[] given = header.substring("Bearer ".length()).trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(given, key.getBytes(StandardCharsets.UTF_8));
    }

    private static String error(String message) {
        return "{\"error\":{\"message\":" + TranscriptionClient.jsonQuote(message == null ? "Error" : message) + "}}";
    }

    private static void respond(OutputStream out, int status, String json) throws IOException {
        respond(out, status, "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(OutputStream out, int status, String contentType, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + " " + reason(status) + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 411: return "Length Required";
            case 413: return "Payload Too Large";
            case 431: return "Request Header Fields Too Large";
            case 503: return "Service Unavailable";
            default: return "Internal Server Error";
        }
    }
}
