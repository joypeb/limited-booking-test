package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@TestExecutionListeners(listeners = ReservationCreateIT.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationCreateIT {
    static final ContainerTestSupport containers = ContainerTestSupport.postgresOnly();
    static final JsonMapper JSON = JsonMapper.builder().build();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        containers.registerLocalAuthAndDatabaseProperties(p);
        p.add("spring.datasource.hikari.maximum-pool-size", () -> 10);
        p.add("spring.datasource.hikari.connection-timeout", () -> 3000);
    }
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder() { return 2999; }
        @Override public void afterTestClass(TestContext c) { containers.close(); }
    }
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    JdbcTemplate jdbc;
    TestData data;
    HttpClient client;
    @BeforeEach void setup() {
        data = new TestData(dataSource); data.reset(); jdbc = new JdbcTemplate(dataSource);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var pool = (com.zaxxer.hikari.HikariDataSource) dataSource;
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
    }
    @AfterEach void cleanup() throws Exception { HttpClientTestSupport.close(client); }
    UUID resource(int capacity) {
        UUID id = UUID.randomUUID(); data.resource(id, "예약 대상", capacity, capacity, Instant.now()); return id;
    }
    HttpResponse<String> post(String key, String body) throws Exception {
        return send(key, body, "application/json", HttpClientTestSupport.basicAuth("USER_A", TestData.USER_A_PASSWORD));
    }
    HttpResponse<String> send(String key, String body, String type, String auth) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/reservations"))
                .timeout(Duration.ofSeconds(9)).header("Content-Type", type);
        if (key != null) b.header("Idempotency-Key", key);
        if (auth != null) b.header("Authorization", auth);
        return HttpClientTestSupport.send(client, b.POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }
    String payload(UUID id) { return "{\"resourceId\":\"" + id + "\"}"; }
    void inventory(UUID id, int available, int count) {
        assertThat(jdbc.queryForObject("SELECT available FROM resources WHERE id=?", Integer.class, id)).isEqualTo(available);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE resource_id=?", Integer.class, id)).isEqualTo(count);
        new DatabaseAssertions(dataSource).assertInventoryConserved(id);
    }
    @Test void createsFirstReservation() throws Exception {
        UUID resourceId = resource(1);
        var r = post("first-key", payload(resourceId));
        assertThat(r.statusCode()).isEqualTo(201);
        assertThat(r.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(r.headers().firstValue("Idempotent-Replayed")).contains("false");
        JsonNode body = JSON.readTree(r.body());
        assertThat(body.size()).isEqualTo(4);
        UUID id = UUID.fromString(body.get("id").asString());
        assertThat(r.headers().firstValue("Location")).contains("/api/reservations/" + id);
        assertThat(body.get("resourceId").asString()).isEqualTo(resourceId.toString());
        assertThat(body.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(body.get("createdAt").asString()).endsWith("Z");
        assertThat(jdbc.queryForObject("SELECT user_id FROM reservations WHERE id=?", UUID.class, id)).isEqualTo(TestData.USER_A_ID);
        Instant stored = jdbc.queryForObject(
                "SELECT created_at FROM reservations WHERE id=?", (rs,n) -> rs.getObject(1, OffsetDateTime.class).toInstant(), id);
        assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(stored);
        inventory(resourceId, 0, 1);
    }
    void problem(HttpResponse<String> r, int status, String code) {
        assertThat(r.statusCode()).isEqualTo(status);
        assertThat(r.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        JsonNode p = JSON.readTree(r.body());
        for (String field : List.of("type", "title", "detail", "instance", "code"))
            assertThat(p.get(field).asString()).isNotBlank();
        assertThat(p.get("status").asInt()).isEqualTo(status);
        assertThat(p.get("code").asString()).isEqualTo(code);
        assertThat(p.get("instance").asString()).isEqualTo("/api/reservations");
        assertSafe(r.body());
    }
    static void assertSafe(String body) {
        assertThat(body).doesNotContain(TestData.ADMIN_PASSWORD, TestData.USER_A_PASSWORD, TestData.USER_B_PASSWORD,
                "PSQLException", "java.lang.", "stackTrace", "INSERT INTO", "UPDATE resources", "SELECT ");
    }
    @Test void soldOutDoesNotKeepKeyAndAllowsAnotherResource() throws Exception {
        UUID empty = resource(0), other = resource(1);
        problem(post("retry-key", payload(empty)), 409, "SOLD_OUT");
        inventory(empty, 0, 0); inventory(other, 1, 0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE user_id=? AND idempotency_key=?",
                Integer.class, TestData.USER_A_ID, "retry-key")).isZero();
        var retry = post("retry-key", payload(other));
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("false");
        inventory(empty, 0, 0); inventory(other, 0, 1);
    }
    @Test void missingResourceDoesNotKeepKey() throws Exception {
        problem(post("missing", payload(UUID.randomUUID())), 404, "NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations", Integer.class)).isZero();
    }
    @Test void differentKeysAllowAdditionalReservations() throws Exception {
        UUID id = resource(2);
        assertThat(post("key-1", payload(id)).statusCode()).isEqualTo(201);
        assertThat(post("key-2", payload(id)).statusCode()).isEqualTo(201);
        inventory(id, 0, 2);
    }

}
