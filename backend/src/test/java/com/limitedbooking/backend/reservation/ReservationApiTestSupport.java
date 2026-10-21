package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.support.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

abstract class ReservationApiTestSupport {
    static final JsonMapper JSON = JsonMapper.builder().build();
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    JdbcTemplate jdbc;
    TestData data;
    HttpClient client;

    protected void beforeDatabaseReset() { }

    @BeforeEach void setup() {
        beforeDatabaseReset();
        data = new TestData(dataSource);
        data.reset();
        jdbc = new JdbcTemplate(dataSource);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var pool = (com.zaxxer.hikari.HikariDataSource) dataSource;
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
    }
    @AfterEach void cleanup() throws Exception { if (client != null) HttpClientTestSupport.close(client); }

    UUID resource(int capacity) {
        UUID id = UUID.randomUUID();
        data.resource(id, "예약 대상", capacity, capacity, Instant.now());
        return id;
    }
    HttpResponse<String> request(String method, String path, String user, String body, String key) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(9));
        if (user != null) {
            String password = switch (user) {
                case "ADMIN" -> TestData.ADMIN_PASSWORD;
                case "USER_B" -> TestData.USER_B_PASSWORD;
                default -> TestData.USER_A_PASSWORD;
            };
            builder.header("Authorization", HttpClientTestSupport.basicAuth(user, password));
        }
        if (key != null) builder.header("Idempotency-Key", key);
        if (body != null) builder.header("Content-Type", "application/json");
        return HttpClientTestSupport.send(client, builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build());
    }
    HttpResponse<String> create(UUID resourceId, String user, String key) throws Exception {
        return request("POST", "/api/reservations", user, "{\"resourceId\":\"" + resourceId + "\"}", key);
    }
    UUID confirmed(UUID resourceId) throws Exception {
        var response = create(resourceId, "USER_A", "original");
        assertThat(response.statusCode()).isEqualTo(201);
        return UUID.fromString(JSON.readTree(response.body()).get("id").asString());
    }
    String path(UUID id) { return "/api/reservations/" + id; }
    HttpResponse<String> get(UUID id) throws Exception { return request("GET", path(id), "USER_A", null, null); }
    HttpResponse<String> cancel(UUID id) throws Exception {
        return request("POST", path(id) + "/cancel", "USER_A", null, null);
    }
    void problem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        JsonNode body = JSON.readTree(response.body());
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asString()).isEqualTo(code);
        for (String field : List.of("type", "title", "detail", "instance"))
            assertThat(body.get(field).asString()).isNotBlank();
        ReservationCreateIT.assertSafe(response.body());
    }
    void inventory(UUID resourceId, int available, int total, int active) {
        assertThat(jdbc.queryForObject("SELECT available FROM resources WHERE id=?", Integer.class, resourceId)).isEqualTo(available);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE resource_id=?", Integer.class, resourceId)).isEqualTo(total);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE resource_id=? AND status='CONFIRMED'", Integer.class, resourceId)).isEqualTo(active);
        new DatabaseAssertions(dataSource).assertInventoryConserved(resourceId);
    }
}
