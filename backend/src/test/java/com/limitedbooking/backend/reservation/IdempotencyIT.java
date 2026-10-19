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

@org.springframework.context.annotation.Import(IdempotencyIT.TimeConfig.class)
@DirtiesContext
@TestExecutionListeners(listeners = IdempotencyIT.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyIT {
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
    @org.springframework.boot.test.context.TestConfiguration
    static class TimeConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        Clock fixedClock() { return Clock.fixed(Instant.parse("2026-10-19T00:00:00.123456789Z"), ZoneOffset.UTC); }
    }
    void replay(HttpResponse<String> first, HttpResponse<String> second) {
        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(second.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
        assertThat(JSON.readTree(second.body())).isEqualTo(JSON.readTree(first.body()));
    }
    @Test void replaysLastItemWithStoredMicroseconds() throws Exception {
        UUID id = resource(1);
        var first = post("replay", payload(id));
        var second = post("replay", payload(id));
        replay(first, second);
        assertThat(JSON.readTree(first.body()).get("createdAt").asString()).isEqualTo("2026-10-19T00:00:00.123456Z");
        Instant stored = jdbc.queryForObject("SELECT created_at FROM reservations", (rs,n) -> rs.getObject(1,OffsetDateTime.class).toInstant());
        assertThat(stored).isEqualTo(Instant.parse("2026-10-19T00:00:00.123456Z"));
        inventory(id,0,1);
    }
    @Test void conflictsBeforeCheckingOtherResourceAvailabilityOrExistence() throws Exception {
        UUID a=resource(1), b=resource(2), empty=resource(0);
        assertThat(post("shared",payload(a)).statusCode()).isEqualTo(201);
        for (UUID other : List.of(b, empty, UUID.randomUUID())) {
            var r=post("shared",payload(other));
            assertThat(r.statusCode()).isEqualTo(409);
            assertThat(JSON.readTree(r.body()).get("code").asString()).isEqualTo("IDEMPOTENCY_CONFLICT");
        }
        inventory(a,0,1);inventory(b,2,0);inventory(empty,0,0);
    }
    @Test void isolatesUsersAndCaseSensitiveKeys() throws Exception {
        UUID id=resource(4);
        assertThat(post("Key",payload(id)).statusCode()).isEqualTo(201);
        assertThat(post("key",payload(id)).statusCode()).isEqualTo(201);
        assertThat(post("other",payload(id)).statusCode()).isEqualTo(201);
        var b=send("Key",payload(id),"application/json",HttpClientTestSupport.basicAuth("USER_B",TestData.USER_B_PASSWORD));
        assertThat(b.statusCode()).isEqualTo(201);
        inventory(id,0,4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE user_id=?",Integer.class,TestData.USER_B_ID)).isEqualTo(1);
    }
    @Test void comparesUuidValuesAndIgnoresJsonWhitespace() throws Exception {
        UUID id=UUID.fromString("aabbccdd-1234-5678-9abc-aabbccddeeff");
        data.resource(id,"표기",1,1,Instant.now());
        var first=post("uuid",payload(id));
        replay(first,post("uuid","{ \n \"resourceId\" : \""+id.toString().toUpperCase(Locale.ROOT)+"\" }"));
        inventory(id,0,1);
    }
    @Test void cancelledRowReplaysConfirmedWithoutReactivation() throws Exception {
        UUID id=resource(1), reservationId=UUID.randomUUID();
        Instant created=Instant.parse("2026-10-18T00:00:00.123456Z"), cancelled=created.plusSeconds(10);
        data.reservation(new StoredReservation(reservationId,id,TestData.USER_A_ID,"cancelled",ReservationStatus.CANCELLED,created,cancelled));
        var r=post("cancelled",payload(id));
        assertThat(r.statusCode()).isEqualTo(201);
        assertThat(r.headers().firstValue("Idempotent-Replayed")).contains("true");
        var body=JSON.readTree(r.body());
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.get("id").asString()).isEqualTo(reservationId.toString());
        assertThat(body.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(created);
        var row=jdbc.queryForMap("SELECT status,cancelled_at FROM reservations WHERE id=?",reservationId);
        assertThat(row.get("status")).isEqualTo("CANCELLED");
        assertThat(((java.sql.Timestamp)row.get("cancelled_at")).toInstant()).isEqualTo(cancelled);
        inventory(id,1,1);
    }
}
