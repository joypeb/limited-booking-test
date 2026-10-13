package com.limitedbooking.backend.common;

import com.limitedbooking.backend.support.HttpClientTestSupport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

class HttpClientCleanupIT {
    @Test void unfinishedResponseDoesNotPreventBoundedClientCleanup() throws Exception {
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var serverExecutor = Executors.newSingleThreadExecutor();
        var closer = Executors.newSingleThreadExecutor();
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        java.io.InputStream body = null;
        server.setExecutor(serverExecutor);
        server.createContext("/unfinished", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().write(73);
                exchange.getResponseBody().flush();
                try { release.await(30, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        try {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/unfinished"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            body = response.body();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(body.read()).isEqualTo(73);
            var completed = closer.submit(() -> { HttpClientTestSupport.close(client); return true; });
            // 소유하지 않은 응답 본문을 기다리는 close()는 이 assertion을 실패시킨다.
            assertThatCode(() -> completed.get(12, TimeUnit.SECONDS)).doesNotThrowAnyException();
            assertThat(client.isTerminated()).isTrue();
        } finally {
            client.shutdownNow();
            if (body != null) body.close();
            release.countDown();
            server.stop(0);
            closer.shutdownNow();
            serverExecutor.shutdownNow();
            assertThat(client.awaitTermination(Duration.ofSeconds(10))).isTrue();
            assertThat(closer.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertThat(serverExecutor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
