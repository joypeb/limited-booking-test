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
    public static void close(HttpClient client) throws InterruptedException {
        client.shutdownNow();
        if (!client.awaitTermination(Duration.ofSeconds(10))) {
            throw new IllegalStateException("시험 HTTP 클라이언트 종료10초 초과");
        }
    }
}
