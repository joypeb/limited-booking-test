package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.support.ContainerTestSupport;
import com.limitedbooking.backend.support.DatabaseAssertions;
import com.limitedbooking.backend.support.HttpClientTestSupport;
import com.limitedbooking.backend.support.PostgresFaults;
import com.limitedbooking.backend.support.RateLimitTestSupport;
import com.limitedbooking.backend.support.TestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@TestExecutionListeners(listeners = DependencyFailureIT.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DependencyFailureIT {
    static final ContainerTestSupport containers = new ContainerTestSupport();
    static final Duration LOCK_BOUND = Duration.ofSeconds(4);
    static final Duration CONNECTION_BOUND = Duration.ofSeconds(6);
    static final Duration PAUSE_BOUND = Duration.ofSeconds(12);
    static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);
    static final Duration COMMIT_BOUND = Duration.ofSeconds(5);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        containers.registerLocalAuthAndDatabaseProperties(p);
        p.add("spring.datasource.hikari.maximum-pool-size", () -> 10);
        p.add("spring.datasource.hikari.connection-timeout", () -> 3000);
        p.add("reservation.rate-limit.limit", () -> 10000);
        p.add("reservation.rate-limit.window", () -> "60000ms");
    }

    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder() { return 2999; }
        @Override public void afterTestClass(TestContext c) { containers.close(); }
    }

    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @Autowired StringRedisTemplate redis;
    @Autowired com.limitedbooking.backend.ratelimit.RateLimitProperties rateLimitPolicy;
    @Autowired org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisFactory;

    JdbcTemplate jdbc;
    TestData data;
    DatabaseAssertions database;
    HttpClient client;

    @BeforeEach void setup() {
        new RateLimitTestSupport(redis).reset();
        RateLimitTestSupport.assertSettings(rateLimitPolicy, redisFactory, 10000);
        data = new TestData(dataSource);
        data.reset();
        jdbc = new JdbcTemplate(dataSource);
        database = new DatabaseAssertions(dataSource);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var pool = (com.zaxxer.hikari.HikariDataSource) dataSource;
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
    }

    @AfterEach void cleanup() throws Exception {
        if (client != null) HttpClientTestSupport.close(client);
    }

    // 503 매핑 누락·부분 커밋·실패 키 보존·중복 수량 복구를 탐지한다.
    @Test void connectionLossRejectsWritesWithin6sAndSameKeyRecovers() throws Exception {
        UUID resourceId = resource(5);
        var confirmed = create(resourceId, "confirmed");
        assertCreated(confirmed, false);
        UUID reservationId = reservationId(confirmed);
        String before = database.businessRows();
        try (var faults = new PostgresFaults(containers.postgres)) {
            try {
                faults.blockAndTerminateConnections();
                assertDependency(timed("F1 생성 연결 중단", () -> create(resourceId, "outage")),
                        CONNECTION_BOUND, "/api/reservations");
                assertDependency(timed("F2 취소 연결 중단", () -> cancel(reservationId)),
                        CONNECTION_BOUND, cancelPath(reservationId));
            } finally {
                faults.restoreConnections();
            }
            assertThat(database.businessRows()).as("F1/F2 장애 전후 모든 업무 행").isEqualTo(before);
            assertCreated(create(resourceId, "outage"), false);
            inventory(resourceId, 3, 2, 2);
            var cancelled = cancel(reservationId);
            assertCancelled(cancelled);
            inventory(resourceId, 4, 2, 1);
            var repeated = cancel(reservationId);
            assertCancelled(repeated);
            assertThat(JSON.readTree(repeated.body())).isEqualTo(JSON.readTree(cancelled.body()));
            inventory(resourceId, 4, 2, 1);
        }
        database.assertInventoryConserved(resourceId);
    }

    // 소켓 읽기 상한 부재·서버 재개 뒤 지연 커밋·실패 키 보존을 탐지한다.
    @Test void pausedDatabaseRejectsCreateWithin12sWithoutPartialWrite() throws Exception {
        UUID resourceId = resource(5);
        String before = database.businessRows();
        try (var faults = new PostgresFaults(containers.postgres)) {
            try {
                faults.pause();
                assertDependency(timed("F4 DB 응답 정지", () -> create(resourceId, "paused")),
                        PAUSE_BOUND, "/api/reservations");
            } finally {
                faults.unpause();
            }
            assertThat(database.businessRows()).as("F4 서버 재개 뒤 업무 행").isEqualTo(before);
            var recovered = timed("F4 서버 재개 후 첫 생성", () -> create(resourceId, "paused"));
            assertThat(recovered.response().statusCode())
                    .as("F4 첫 복구 요청 경과=%s", recovered.elapsed()).isEqualTo(201);
            assertCreated(recovered.response(), false);
            inventory(resourceId, 4, 1, 1);
        }
        database.assertInventoryConserved(resourceId);
    }

    // 취소 lock_timeout 누락·실패한 취소의 부분 반영·복구 누락을 탐지한다.
    @Test void cancelLockTimeoutIs503Within4sAndRecovers() throws Exception {
        UUID resourceId = resource(5);
        var confirmed = create(resourceId, "confirmed");
        assertCreated(confirmed, false);
        UUID reservationId = reservationId(confirmed);
        String before = database.businessRows();
        try (var faults = new PostgresFaults(containers.postgres)) {
            try (var lock = faults.lockResourceRow(resourceId)) {
                assertDependency(timed("F5 취소 행 잠금", () -> cancel(reservationId)),
                        LOCK_BOUND, cancelPath(reservationId));
                assertThat(database.businessRows()).as("F5 잠금 실패 뒤 업무 행").isEqualTo(before);
                assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id=?", String.class, reservationId))
                        .isEqualTo("CONFIRMED");
                inventory(resourceId, 4, 1, 1);
            }
            assertCancelled(cancel(reservationId));
            inventory(resourceId, 5, 1, 0);
        }
        database.assertInventoryConserved(resourceId);
    }

    // 커밋 후 재요청의 중복 예약·재현 표시/최초 응답 값 훼손을 탐지한다.
    @Test void responseLostAfterCommitIsRecoveredBySameKey() throws Exception {
        UUID resourceId = resource(5);
        CommittedReservation committed;
        long started = System.nanoTime();
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 3000);
            socket.setSoTimeout((int) HTTP_TIMEOUT.toMillis());
            byte[] body = payload(resourceId).getBytes(StandardCharsets.UTF_8);
            String headers = "POST /api/reservations HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + port + "\r\n"
                    + "Authorization: " + userAuthorization() + "\r\n"
                    + "Idempotency-Key: lost\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().write(body);
            socket.getOutputStream().flush();
            // 소켓 응답은 읽지 않는다. 별도 DB 조회의 커밋 관측만 성공 근거다.
            committed = awaitCommitted("lost");
        } finally {
            System.out.println("F6 응답 미수신·커밋 관측 경과=" + Duration.ofNanos(System.nanoTime() - started));
        }
        var replay = timed("F6 응답 유실 후 재요청", () -> create(resourceId, "lost"));
        assertCreated(replay.response(), true);
        var response = JSON.readTree(replay.response().body());
        assertThat(UUID.fromString(response.get("id").asString())).isEqualTo(committed.id());
        assertThat(Instant.parse(response.get("createdAt").asString())).isEqualTo(committed.createdAt());
        assertThat(response.get("resourceId").asString()).isEqualTo(resourceId.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE user_id=? AND idempotency_key=?",
                Long.class, TestData.USER_A_ID, "lost")).isEqualTo(1);
        inventory(resourceId, 4, 1, 1);
        database.assertInventoryConserved(resourceId);
    }

    private CommittedReservation awaitCommitted(String key) throws InterruptedException {
        long deadline = System.nanoTime() + COMMIT_BOUND.toNanos();
        while (System.nanoTime() < deadline) {
            var rows = jdbc.query("SELECT id, created_at FROM reservations WHERE user_id=? AND idempotency_key=?",
                    (rs, row) -> new CommittedReservation(rs.getObject("id", UUID.class),
                            rs.getObject("created_at", OffsetDateTime.class).toInstant()), TestData.USER_A_ID, key);
            if (!rows.isEmpty()) {
                assertThat(rows).hasSize(1);
                return rows.getFirst();
            }
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) Thread.sleep(Duration.ofNanos(Math.min(Duration.ofMillis(100).toNanos(), remaining)));
        }
        throw new AssertionError("F6 5초 안에 DB 커밋을 확인하지 못함");
    }

    private UUID resource(int capacity) throws Exception {
        var response = request("/api/resources", "{\"name\":\"장애 검증 좌석\",\"capacity\":" + capacity + "}",
                null, HttpClientTestSupport.basicAuth(TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD));
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(JSON.readTree(response.body()).get("id").asString());
    }

    private HttpResponse<String> create(UUID resourceId, String key) throws Exception {
        return request("/api/reservations", payload(resourceId), key, userAuthorization());
    }

    private HttpResponse<String> cancel(UUID reservationId) throws Exception {
        return request(cancelPath(reservationId), null, null, userAuthorization());
    }

    private HttpResponse<String> request(String path, String body, String key, String authorization) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(HTTP_TIMEOUT).header("Authorization", authorization);
        if (key != null) builder.header("Idempotency-Key", key);
        if (body != null) builder.header("Content-Type", "application/json");
        // 공통 send의 10초 상한 대신 이 장애 시험의 명시적 20초 상한을 사용한다.
        return client.send(builder.POST(body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String userAuthorization() {
        return HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME, TestData.USER_A_PASSWORD);
    }

    private static String payload(UUID resourceId) { return "{\"resourceId\":\"" + resourceId + "\"}"; }
    private static String cancelPath(UUID reservationId) { return "/api/reservations/" + reservationId + "/cancel"; }
    private static UUID reservationId(HttpResponse<String> response) {
        return UUID.fromString(JSON.readTree(response.body()).get("id").asString());
    }

    private void assertCreated(HttpResponse<String> response, boolean replayed) {
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Idempotent-Replayed")).contains(Boolean.toString(replayed));
        assertThat(response.headers().firstValue("Location")).contains("/api/reservations/" + reservationId(response));
        assertThat(JSON.readTree(response.body()).get("status").asString()).isEqualTo("CONFIRMED");
    }

    private void assertCancelled(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(200);
        var body = JSON.readTree(response.body());
        assertThat(body.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(body.get("cancelledAt").asString()).isNotBlank();
    }

    private void assertDependency(TimedResponse timed, Duration bound, String path) {
        assertThat(timed.elapsed()).as("%s 응답 경과", path).isLessThan(bound);
        var response = timed.response();
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        var body = JSON.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(503);
        assertThat(body.get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        for (String field : List.of("type", "title", "detail", "instance")) {
            assertThat(body.get(field).asString()).isNotBlank();
        }
        assertThat(body.get("instance").asString()).isEqualTo(path);
        ReservationCreateIT.assertSafe(response.body());
        response.headers().map().values().forEach(values -> values.forEach(ReservationCreateIT::assertSafe));
    }

    private void inventory(UUID resourceId, int available, int count, int confirmed) {
        var snapshot = database.snapshot(List.of(resourceId)).get(resourceId);
        assertThat(snapshot.available()).isEqualTo(available);
        assertThat(snapshot.totalCount()).isEqualTo(count);
        assertThat(snapshot.confirmedCount()).isEqualTo(confirmed);
    }

    private TimedResponse timed(String label, HttpCall call) throws Exception {
        long started = System.nanoTime();
        try {
            var response = call.send();
            return new TimedResponse(response, Duration.ofNanos(System.nanoTime() - started));
        } finally {
            System.out.println(label + " 경과=" + Duration.ofNanos(System.nanoTime() - started));
        }
    }

    @FunctionalInterface private interface HttpCall { HttpResponse<String> send() throws Exception; }
    private record TimedResponse(HttpResponse<String> response, Duration elapsed) {}
    private record CommittedReservation(UUID id, Instant createdAt) {}
}
