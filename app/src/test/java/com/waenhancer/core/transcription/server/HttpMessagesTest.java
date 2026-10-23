package com.waenhancer.core.transcription.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.Test;

public class HttpMessagesTest {

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    public void readsRequestLineHeadersAndBody() throws Exception {
        byte[] raw = bytes("POST /v1/audio/transcriptions?x=1 HTTP/1.1\r\nHost: a\r\nContent-Length: 5\r\n"
                + "Authorization: Bearer k\r\n\r\nhello");
        HttpMessages.Request request = HttpMessages.readRequest(new ByteArrayInputStream(raw));
        assertEquals("POST", request.method);
        assertEquals("/v1/audio/transcriptions", request.path);
        assertEquals("Bearer k", request.headers.get("authorization"));
        assertEquals("hello", new String(request.body, StandardCharsets.UTF_8));
    }

    @Test
    public void rejectsOversizedAndChunkedAndShortRequests() throws Exception {
        expectStatus("POST / HTTP/1.1\r\nContent-Length: 999999999\r\n\r\n", 413);
        expectStatus("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n", 411);
        expectStatus("POST / HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc", 400);
        expectStatus("garbage\r\n\r\n", 400);
        StringBuilder huge = new StringBuilder("GET / HTTP/1.1\r\n");
        while (huge.length() < HttpMessages.MAX_HEADER_BYTES + 10) huge.append("X-A: 1234567890\r\n");
        expectStatus(huge.toString(), 431);
    }

    private static void expectStatus(String raw, int status) throws Exception {
        try {
            HttpMessages.readRequest(new ByteArrayInputStream(bytes(raw)));
            fail("expected " + status);
        } catch (HttpMessages.BadRequest bad) {
            assertEquals(status, bad.status);
        }
    }

    @Test
    public void parsesMultipartWithBinaryFileAndFields() throws Exception {
        String boundary = "----waexTest";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] audio = {0, 13, 10, 45, 45, (byte) 255, 1};
        body.write(bytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nsmall\r\n"));
        body.write(bytes("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.ogg\"\r\n"
                + "Content-Type: audio/ogg\r\n\r\n"));
        body.write(audio);
        body.write(bytes("\r\n--" + boundary + "--\r\n"));
        Map<String, HttpMessages.Part> parts = HttpMessages.parseMultipart(
                "multipart/form-data; boundary=\"" + boundary + "\"", body.toByteArray());
        assertEquals("small", parts.get("model").text());
        assertEquals("a.ogg", parts.get("file").fileName);
        assertArrayEquals(audio, parts.get("file").data);
    }

    @Test
    public void multipartWithoutBoundaryIsRejected() {
        try {
            HttpMessages.parseMultipart("application/json", new byte[0]);
            fail();
        } catch (HttpMessages.BadRequest expected) {
            assertEquals(400, expected.status);
        }
    }

    @Test
    public void apiKeyMustMatchExactly() {
        assertTrue(LocalApiServer.authorized("Bearer waex-abc", "waex-abc"));
        assertFalse(LocalApiServer.authorized("Bearer waex-abd", "waex-abc"));
        assertFalse(LocalApiServer.authorized("waex-abc", "waex-abc"));
        assertFalse(LocalApiServer.authorized(null, "waex-abc"));
        assertFalse(LocalApiServer.authorized("Bearer ", ""));
    }
}
