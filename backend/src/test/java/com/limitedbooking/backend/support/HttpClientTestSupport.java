package com.limitedbooking.backend.support;

import java.net.http.HttpClient;
import java.time.Duration;

/** 테스트가 소유하는 HTTP 클라이언트의 종료 책임. */
public final class HttpClientTestSupport {
    private HttpClientTestSupport() { }
    public static void close(HttpClient client) throws InterruptedException {
        client.shutdownNow();
        if (!client.awaitTermination(Duration.ofSeconds(10))) {
            throw new IllegalStateException("시험 HTTP 클라이언트 종료10초 초과");
        }
    }
}
