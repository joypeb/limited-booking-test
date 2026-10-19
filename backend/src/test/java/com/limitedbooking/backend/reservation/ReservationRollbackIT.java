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
@TestExecutionListeners(listeners = ReservationRollbackIT.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationRollbackIT {
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
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean ReservationRepository reservations;
    @Autowired ReservationWriter writer;
    @Autowired ReservationService service;
    final java.util.concurrent.atomic.AtomicBoolean failInsert = new java.util.concurrent.atomic.AtomicBoolean();
    final java.util.concurrent.atomic.AtomicInteger observedAvailable = new java.util.concurrent.atomic.AtomicInteger(-1);
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
    @Test void rollsBackActualDecrementBeforeInsertAndAllowsSameKey() throws Exception {
        UUID id=resource(1);
        org.mockito.Mockito.doAnswer(call -> {
            observedAvailable.set(jdbc.queryForObject("SELECT available FROM resources WHERE id=?",Integer.class,id));
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            if (failInsert.getAndSet(false)) throw new IllegalStateException("테스트 INSERT 직전 장애");
            return call.callRealMethod();
        }).when(reservations).insert(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        failInsert.set(true);
        try {
            var failed=post("recover",payload(id));
            assertThat(failed.statusCode()).isEqualTo(500);
            ReservationCreateIT.assertSafe(failed.body());
            assertThat(observedAvailable.get()).isZero();
            inventory(id,1,0);
            assertThat(reservations.findByUserAndKey(TestData.USER_A_ID,"recover")).isEmpty();
            var retry=post("recover",payload(id));
            assertThat(retry.statusCode()).isEqualTo(201);
            assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("false");
            inventory(id,0,1);
        } finally { failInsert.set(false); org.mockito.Mockito.reset(reservations); }
    }
    @Test void actualUniqueViolationRollsBackDecrementAndHasExactMetadata() {
        UUID id=resource(2), oldId=UUID.randomUUID();
        // 준비 데이터는 요청 전에 커밋한다. confirmed1에 맞춰 available1로 준비한다.
        jdbc.update("UPDATE resources SET available=1 WHERE id=?",id);
        data.reservation(new StoredReservation(oldId,id,TestData.USER_A_ID,"duplicate",ReservationStatus.CONFIRMED,Instant.EPOCH,null));
        var seen=new java.util.concurrent.atomic.AtomicInteger(-1);
        org.mockito.Mockito.doAnswer(call -> {
            seen.set(jdbc.queryForObject("SELECT available FROM resources WHERE id=?",Integer.class,id));
            return call.callRealMethod();
        }).when(reservations).insert(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        try {
            assertThat(org.springframework.aop.support.AopUtils.isAopProxy(writer)).isTrue();
            Throwable failure=org.assertj.core.api.Assertions.catchThrowable(() -> writer.create(TestData.USER_A_ID,id,IdempotencyKey.parse("duplicate")));
            assertThat(failure).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
            assertThat(com.limitedbooking.backend.common.UniqueViolationClassifier.isReservationUserKeyViolation(failure)).isTrue();
            Throwable cause=failure;
            while (!(cause instanceof org.postgresql.util.PSQLException)) cause=cause.getCause();
            var pg=(org.postgresql.util.PSQLException)cause;
            assertThat(pg.getSQLState()).isEqualTo("23505");
            assertThat(pg.getServerErrorMessage().getConstraint()).isEqualTo("uq_reservations_user_key");
            assertThat(seen.get()).isZero();
            inventory(id,1,1);
            assertThat(reservations.findByUserAndKey(TestData.USER_A_ID,"duplicate").orElseThrow().id()).isEqualTo(oldId);
        } finally { org.mockito.Mockito.reset(reservations); }
    }
    @Test void serviceRejectsAmbientTransactionWithoutChangingData() {
        UUID id=resource(1);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.execute(status ->
                service.create(TestData.USER_A_ID,id,IdempotencyKey.parse("ambient"))))
                .isInstanceOf(IllegalStateException.class);
        inventory(id,1,0);
        assertThat(reservations.findByUserAndKey(TestData.USER_A_ID,"ambient")).isEmpty();
    }
    void dependencyProblem(HttpResponse<String> r) {
        assertThat(r.statusCode()).isEqualTo(503);
        assertThat(r.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        var body=JSON.readTree(r.body());
        assertThat(body.get("status").asInt()).isEqualTo(503);
        assertThat(body.get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        for (String field: List.of("type","title","detail","instance")) assertThat(body.get(field).asString()).isNotBlank();
        assertThat(body.get("instance").asString()).isEqualTo("/api/reservations");
        ReservationCreateIT.assertSafe(r.body());
    }
    void noSuccessThenRecover(UUID id,String key) throws Exception {
        inventory(id,1,0);
        assertThat(reservations.findByUserAndKey(TestData.USER_A_ID,key)).isEmpty();
        var retry=post(key,payload(id));
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("false");
        inventory(id,0,1);
    }
    @Test void lockTimeoutIs503AndSameKeyRecovers() throws Exception {
        UUID id=resource(1);
        long started=System.nanoTime();
        try (var lock=dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try {
                try (var statement=lock.prepareStatement("SELECT id FROM resources WHERE id=? FOR UPDATE")) {
                    statement.setObject(1,id); try (var rows=statement.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                dependencyProblem(post("lock",payload(id)));
                assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(9));
            } finally { lock.rollback(); }
        }
        noSuccessThenRecover(id,"lock");
    }
    @Test void statementTimeoutRollsBackActualDecrementAndSameKeyRecovers() throws Exception {
        UUID id=resource(1);
        var seen=new java.util.concurrent.atomic.AtomicInteger(-1);
        org.mockito.Mockito.doAnswer(call -> {
            seen.set(jdbc.queryForObject("SELECT available FROM resources WHERE id=?",Integer.class,id));
            assertThat(jdbc.queryForObject("SHOW lock_timeout",String.class)).isEqualTo("2s");
            assertThat(jdbc.queryForObject("SHOW statement_timeout",String.class)).isEqualTo("5s");
            assertThat(jdbc.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("read committed");
            jdbc.execute("SELECT pg_sleep(6)");
            return call.callRealMethod();
        }).when(reservations).insert(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        long started=System.nanoTime();
        try {
            dependencyProblem(post("statement",payload(id)));
            assertThat(seen.get()).isZero();
            assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(9));
        } finally { org.mockito.Mockito.reset(reservations); }
        noSuccessThenRecover(id,"statement");
    }
    @Test void writerConnectionAcquisitionFailureIs503AndSameKeyRecovers() throws Exception {
        UUID id=resource(1);
        var held=new ArrayList<java.sql.Connection>();
        var drained=new java.util.concurrent.atomic.AtomicBoolean();
        var txStarted=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.Mockito.doAnswer(call -> {
            Object result=call.callRealMethod(); // 실제 Service 선조회는 정상 종료한 뒤 풀을 소진한다.
            if (drained.compareAndSet(false,true)) {
                txStarted.set(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
                for (int n=0;n<10;n++) held.add(dataSource.getConnection());
            }
            return result;
        }).when(reservations).findByUserAndKey(TestData.USER_A_ID,"pool");
        long started=System.nanoTime();
        try {
            dependencyProblem(post("pool",payload(id)));
            assertThat(held).hasSize(10);
            assertThat(txStarted.get()).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime()-started)).isLessThan(Duration.ofSeconds(9));
        } finally {
            for (var connection:held) connection.close();
            org.mockito.Mockito.reset(reservations);
        }
        noSuccessThenRecover(id,"pool");
    }

    @Test void unclassifiedSqlAndTransactionDefectsStay500AndRollback() throws Exception {
        UUID id=resource(1);
        List<RuntimeException> failures=List.of(
            new org.springframework.jdbc.UncategorizedSQLException("test","test",new java.sql.SQLException("내부 결함","23505")),
            new org.springframework.transaction.CannotCreateTransactionException("내부 결함",new IllegalStateException("설정 결함")));
        int n=0;
        for (RuntimeException failure:failures) {
            String key="unclassified-"+n++;
            org.mockito.Mockito.doThrow(failure).when(reservations).insert(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
            try {
                var response=post(key,payload(id));
                assertThat(response.statusCode()).isEqualTo(500);
                ReservationCreateIT.assertSafe(response.body());
                inventory(id,1,0);
                assertThat(reservations.findByUserAndKey(TestData.USER_A_ID,key)).isEmpty();
            } finally { org.mockito.Mockito.reset(reservations); }
        }
    }

}
