package com.limitedbooking.backend.support;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** 테스트가 소유하는 HTTP 클라이언트의 종료 책임. */
public final class HttpClientTestSupport {
    private HttpClientTestSupport() { }
    public static HttpResponse<String> send(HttpClient client, HttpRequest request) throws Exception {
        var pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        try {
            return pending.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException failure) {
            pending.cancel(true);
            var timeout = new HttpTimeoutException("시험 HTTP 요청 전체10초 초과");
            timeout.initCause(failure);
            throw timeout;
        } catch (InterruptedException failure) {
            pending.cancel(true);
            throw failure;
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof Exception cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw new IllegalStateException("시험 HTTP 요청 실행 실패", failure.getCause());
        }
    }
    public static String basicAuth(String username, String password) {
        String credentials = username + ":" + password;
        return "Basic " + java.util.Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public record RawHttpResponse(int statusCode, java.util.Map<String, String> headers, String body) {}

    public static RawHttpResponse sendRawHttp(int port, String rawHeaders, byte[] body) throws Exception {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("127.0.0.1", port), 3000);
            socket.setSoTimeout(10000);
            var out = socket.getOutputStream();
            out.write(rawHeaders.getBytes(StandardCharsets.UTF_8));
            try {
                if (body != null && body.length > 0) {
                    out.write(body);
                }
                out.flush();
            } catch (java.io.IOException ignoredEarlyClose) {
                // 서버가 8KiB 상한 초과 등을 감지해 본문 수신 중 응답 후 연결을 먼저 닫을 수 있다.
            }
            var in = new java.io.BufferedInputStream(socket.getInputStream());
            String statusLine = readAsciiLine(in);
            if (statusLine == null || !statusLine.startsWith("HTTP/")) {
                throw new IllegalStateException("유효하지 않은 HTTP 상태 줄: " + statusLine);
            }
            String[] statusParts = statusLine.split(" ", 3);
            int statusCode = Integer.parseInt(statusParts[1]);
            var headers = new java.util.LinkedHashMap<String, String>();
            String line;
            while ((line = readAsciiLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    String name = line.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
                    String value = line.substring(colon + 1).trim();
                    headers.put(name, value);
                }
            }
            byte[] responseBytes = in.readAllBytes();
            String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
            if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
                responseBody = decodeChunkedBody(responseBytes);
            }
            return new RawHttpResponse(statusCode, headers, responseBody);
        }
    }

    private static String readAsciiLine(java.io.InputStream in) throws java.io.IOException {
        var buffer = new java.io.ByteArrayOutputStream();
        int prev = -1;
        int curr;
        while ((curr = in.read()) != -1) {
            if (prev == '\r' && curr == '\n') {
                byte[] bytes = buffer.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            buffer.write(curr);
            prev = curr;
        }
        if (buffer.size() == 0) {
            return null;
        }
        return buffer.toString(StandardCharsets.US_ASCII);
    }

    private static String decodeChunkedBody(byte[] raw) throws java.io.IOException {
        var in = new java.io.ByteArrayInputStream(raw);
        var out = new java.io.ByteArrayOutputStream();
        while (true) {
            String sizeLine = readAsciiLine(in);
            if (sizeLine == null) {
                break;
            }
            int semi = sizeLine.indexOf(';');
            String hex = (semi >= 0 ? sizeLine.substring(0, semi) : sizeLine).trim();
            if (hex.isEmpty()) {
                continue;
            }
            int chunkSize = Integer.parseInt(hex, 16);
            if (chunkSize == 0) {
                break;
            }
            byte[] chunk = in.readNBytes(chunkSize);
            out.write(chunk);
            readAsciiLine(in);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public static void close(HttpClient client) throws InterruptedException {
        client.shutdownNow();
        if (!client.awaitTermination(Duration.ofSeconds(10))) {
            throw new IllegalStateException("시험 HTTP 클라이언트 종료10초 초과");
        }
    }
}
