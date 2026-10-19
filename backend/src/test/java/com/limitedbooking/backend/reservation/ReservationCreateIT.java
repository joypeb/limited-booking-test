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

    @Test void authenticationFailuresDoNotChangeInventory() throws Exception {
        UUID id=resource(1);
        for (String auth : new String[]{null,HttpClientTestSupport.basicAuth("USER_A","wrong-password")}) {
            var r=send("auth",payload(id),"application/json",auth);
            problem(r,401,"UNAUTHENTICATED");
            assertThat(r.headers().firstValue("WWW-Authenticate").orElseThrow()).startsWith("Basic ");
            inventory(id,1,0);
        }
    }
    @Test void allAuthenticatedIdentitiesCanCreateAndForgedHeaderCannotChangeOwner() throws Exception {
        UUID id=resource(3);
        String[] users={"ADMIN","USER_A","USER_B"};
        String[] passwords={TestData.ADMIN_PASSWORD,TestData.USER_A_PASSWORD,TestData.USER_B_PASSWORD};
        UUID[] owners={TestData.ADMIN_ID,TestData.USER_A_ID,TestData.USER_B_ID};
        for (int n=0;n<3;n++) {
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/reservations"))
                .timeout(Duration.ofSeconds(9)).header("Content-Type","application/json")
                .header("Idempotency-Key","identity").header("X-User-Id",UUID.randomUUID().toString())
                .header("Authorization",HttpClientTestSupport.basicAuth(users[n],passwords[n]))
                .POST(HttpRequest.BodyPublishers.ofString(payload(id))).build();
            var r=HttpClientTestSupport.send(client,request);
            assertThat(r.statusCode()).isEqualTo(201);
            UUID reservation=UUID.fromString(JSON.readTree(r.body()).get("id").asString());
            assertThat(jdbc.queryForObject("SELECT user_id FROM reservations WHERE id=?",UUID.class,reservation)).isEqualTo(owners[n]);
            inventory(id,2-n,n+1);
        }
    }
    @Test void rejectsInvalidKeysWithoutNormalizingAndAcceptsLengthBoundaries() throws Exception {
        UUID id=resource(2);
        for (String key : new String[]{null,"","a".repeat(65),"a b","a\tb","a.b","a/b","é"}) {
            problem(post(key,payload(id)),400,"INVALID_REQUEST");
            inventory(id,2,0);
        }
        for (String key:List.of("x","A9_-".repeat(16))) {
            var r=post(key,payload(id));
            assertThat(r.statusCode()).isEqualTo(201);
            assertThat(r.headers().firstValue("Idempotent-Replayed")).contains("false");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE user_id=? AND idempotency_key=?",
                    Integer.class,TestData.USER_A_ID,key)).isEqualTo(1);
        }
        inventory(id,0,2);
    }
    @Test void rejectsInvalidBodiesAndDoesNotReserveValidKey() throws Exception {
        UUID id=resource(1);
        String valid=payload(id), fields=valid.substring(0,valid.length()-1);
        for (String body:List.of("", " ", "null", "[]", "{", "{}", "{\"resourceId\":null}",
                "{\"resourceId\":\"not-a-uuid\"}","{\"resourceId\":123}",
                fields+",\"resourceId\":\""+id+"\"}", fields+",\"quantity\":1}",
                fields+",\"userId\":\""+TestData.USER_B_ID+"\"}",fields+",\"extra\":true}", valid+" {}")) {
            problem(post("correct-body",body),400,"INVALID_REQUEST");
            inventory(id,1,0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE user_id=? AND idempotency_key=?",
                    Integer.class,TestData.USER_A_ID,"correct-body")).isZero();
        }
        var retry=post("correct-body",valid);
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("false");
        inventory(id,0,1);
    }

    @Test void exact8192BytesReachesControllerWhile8193IsRejectedBeforeAuth() throws Exception {
        UUID id=resource(1);
        String body=payload(id);String exact=body+" ".repeat(8192-body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertThat(exact.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(8192);
        problem(send("size",exact,"application/json",null),401,"UNAUTHENTICATED");
        inventory(id,1,0);
        String oversized=exact+" ";
        for (String auth:new String[]{null,HttpClientTestSupport.basicAuth("USER_A",TestData.USER_A_PASSWORD)}) {
            problem(send("size",oversized,"application/json",auth),413,"PAYLOAD_TOO_LARGE");
            inventory(id,1,0);
        }
        assertThat(post("size",exact).statusCode()).isEqualTo(201);
        inventory(id,0,1);
    }
    @Test void chunkedWithoutContentLengthCannotBypassActualReadLimit() throws Exception {
        UUID id=resource(1);
        byte[] oversized="x".repeat(8193).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/reservations"))
            .version(HttpClient.Version.HTTP_1_1).timeout(Duration.ofSeconds(9)).header("Content-Type","application/json")
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new java.io.ByteArrayInputStream(oversized))).build();
        problem(HttpClientTestSupport.send(client,request),413,"PAYLOAD_TOO_LARGE");
        String headers="POST /api/reservations HTTP/1.1\r\nHost: 127.0.0.1:"+port
                +"\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n";
        byte[] wire=("2001\r\n"+"x".repeat(8193)+"\r\n0\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var raw=HttpClientTestSupport.sendRawHttp(port,headers,wire);
        assertThat(raw.statusCode()).isEqualTo(413);
        assertThat(raw.headers().get("content-type")).startsWith("application/problem+json");
        var p=JSON.readTree(raw.body());
        assertThat(p.get("status").asInt()).isEqualTo(413);
        assertThat(p.get("code").asString()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(p.get("instance").asString()).isEqualTo("/api/reservations");
        assertSafe(raw.body());
        inventory(id,1,0);
    }
    @Test void mediaAndInputValidationRunBeforeSuccessfulKeyReplay() throws Exception {
        UUID id=resource(1);
        assertThat(post("existing",payload(id)).statusCode()).isEqualTo(201);
        for (String type:List.of("text/plain","application/xml","application/vnd.test+json"))
            problem(send("existing",payload(id),type,HttpClientTestSupport.basicAuth("USER_A",TestData.USER_A_PASSWORD)),415,"UNSUPPORTED_MEDIA_TYPE");
        problem(post("existing","{}"),400,"INVALID_REQUEST");
        problem(post("existing","{"),400,"INVALID_REQUEST");
        problem(post("invalid.key",payload(id)),400,"INVALID_REQUEST");
        var unauth=send("existing","{","text/plain",null);
        problem(unauth,401,"UNAUTHENTICATED");
        assertThat(unauth.headers().firstValue("WWW-Authenticate").orElseThrow()).startsWith("Basic ");
        inventory(id,0,1);
        var replay=post("existing",payload(id));
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        inventory(id,0,1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "AAAAAAAAAAAAAAAAAAAAAQ==", "AAAAAAAAAAAAAAAAAAAAAQ",
        " 00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000001 ",
        "1-1-1-1-1"
    })
    void rejectsNonUuidWireRepresentationsWithoutConsumingKey(String rawId) throws Exception {
        UUID id=UUID.fromString("00000000-0000-0000-0000-000000000001");
        data.resource(id,"UUID 원문 검증",1,1,Instant.EPOCH);
        problem(post("uuid-wire",JSON.writeValueAsString(Map.of("resourceId",rawId))),400,"INVALID_REQUEST");
        inventory(id,1,0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations",Integer.class)).isZero();
        var valid=post("uuid-wire",payload(id));
        assertThat(valid.statusCode()).isEqualTo(201);
        assertThat(valid.headers().firstValue("Idempotent-Replayed")).contains("false");
        inventory(id,0,1);
    }

}
