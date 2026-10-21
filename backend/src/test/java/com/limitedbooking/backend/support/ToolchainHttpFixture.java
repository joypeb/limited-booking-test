package com.limitedbooking.backend.support;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;

/** 독립 시험 서버. 제품 API에 음성 모드를 노출하지 않는다. */
public final class ToolchainHttpFixture implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor=Executors.newFixedThreadPool(1);
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public ToolchainHttpFixture(boolean missingId) throws Exception {
        this(missingId ? "{}" : "{\"id\":\"00000000-0000-0000-0000-000000000073\"}");
    }
    public ToolchainHttpFixture(String responseBody) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(executor);
        server.createContext("/toolchain/probe",exchange->{
            byte[] body=responseBody.getBytes(StandardCharsets.UTF_8);
            try(exchange) {
                exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(200,body.length);
                exchange.getResponseBody().write(body);
            }
        });
        server.start();
    }
    public record Exchange(HttpRequest request,HttpResponse<String> response) { }
    public Exchange get() throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/toolchain/probe"))
                .timeout(Duration.ofSeconds(10)).header("Accept","application/json").GET().build();
        return new Exchange(request,HttpClientTestSupport.send(client,request));
    }
    @Override public void close() throws Exception {
        server.stop(0);client.shutdownNow();executor.shutdownNow();
        boolean clientClosed=client.awaitTermination(Duration.ofSeconds(10));
        boolean executorClosed=executor.awaitTermination(10,TimeUnit.SECONDS);
        if (!clientClosed || !executorClosed) throw new IllegalStateException("시험 HTTP 자원 종료10초 초과");
    }
}
