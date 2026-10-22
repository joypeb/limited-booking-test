package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.support.ContainerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext
@TestExecutionListeners(listeners = ReservationCancelIT.Cleanup.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationCancelIT extends ReservationApiTestSupport {
    static final ContainerTestSupport containers = new ContainerTestSupport();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        containers.registerLocalAuthAndDatabaseProperties(p);
        p.add("spring.datasource.hikari.maximum-pool-size", () -> 10);
        p.add("spring.datasource.hikari.connection-timeout", () -> 3000);
    }
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder() { return 2999; }
        @Override public void afterTestClass(TestContext c) { containers.close(); }
    }

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.limitedbooking.backend.resource.ResourceRepository resources;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    ReservationRepository reservations;
    @org.springframework.beans.factory.annotation.Autowired ReservationService service;

    @Test void cancellationRestoresOneUnitAndGetReturnsStoredTimestamp() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        var response = cancel(id);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        var body = JSON.readTree(response.body());
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.get("id").asString()).isEqualTo(id.toString());
        assertThat(body.get("resourceId").asString()).isEqualTo(resource.toString());
        assertThat(body.get("status").asString()).isEqualTo("CANCELLED");
        String cancelledAt = body.get("cancelledAt").asString();
        assertThat(cancelledAt).endsWith("Z");
        var stored = jdbc.queryForObject("SELECT cancelled_at FROM reservations WHERE id=?",
                (rs, n) -> rs.getObject(1, java.time.OffsetDateTime.class).toInstant(), id);
        assertThat(java.time.Instant.parse(cancelledAt)).isEqualTo(stored);
        assertThat(get(id).statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(get(id).body())).isEqualTo(body);
        inventory(resource, 1, 1, 0);
    }
    @Test void nonEmptyCancelBodyIsRejectedWithoutChanges() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        for (String body : new String[]{"{}", " ", "null", "x".repeat(8192)}) {
            problem(request("POST", path(id) + "/cancel", "USER_A", body, null), 400, "INVALID_REQUEST");
            inventory(resource, 0, 1, 1);
            var current = JSON.readTree(get(id).body());
            assertThat(current.get("status").asString()).isEqualTo("CONFIRMED");
            assertThat(current.get("cancelledAt").isNull()).isTrue();
        }
    }
    @Test void repeatedCancellationPreservesFirstTimestampAndInventory() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        var first = cancel(id);
        assertThat(first.statusCode()).isEqualTo(200);
        for (int attempt = 0; attempt < 2; attempt++) {
            var again = cancel(id);
            assertThat(again.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(again.body())).isEqualTo(JSON.readTree(first.body()));
            inventory(resource, 1, 1, 0);
        }
    }
    private void assertRolledBack(UUID resource, UUID id) throws Exception {
        inventory(resource, 0, 1, 1);
        var current = JSON.readTree(get(id).body());
        assertThat(current.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(current.get("cancelledAt").isNull()).isTrue();
    }
    private void failRecovery(Object failure, int status) throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        org.mockito.Mockito.doAnswer(call -> {
            // 실제 상태 UPDATE 이후, 동일 트랜잭션 안에서 실패를 주입한다.
            assertThat(jdbc.queryForObject("SELECT status FROM reservations WHERE id=?", String.class, id)).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("2s");
            assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("5s");
            assertThat(jdbc.queryForObject("SHOW transaction_isolation", String.class)).isEqualTo("read committed");
            if (failure instanceof RuntimeException exception) throw exception;
            return failure;
        }).when(resources).incrementIfBelowCapacity(resource);
        try {
            var response = cancel(id);
            assertThat(response.statusCode()).isEqualTo(status);
            ReservationCreateIT.assertSafe(response.body());
            assertThat(response.body()).doesNotContain("테스트 복구 장애");
            if (status == 503) problem(response, 503, "DEPENDENCY_UNAVAILABLE");
            assertRolledBack(resource, id);
        } finally {
            org.mockito.Mockito.reset(resources);
        }
        assertThat(cancel(id).statusCode()).isEqualTo(200);
        inventory(resource, 1, 1, 0);
    }
    @Test void recoveryExceptionRollsBackStatusAndAllowsRetry() throws Exception {
        failRecovery(new IllegalStateException("테스트 복구 장애"), 500);
    }
    @Test void zeroRecoveryIsInternalDefectAndRollsBack() throws Exception { failRecovery(0, 500); }
    @Test void recoveryTimeoutIs503AndRollsBack() throws Exception {
        failRecovery(new org.springframework.dao.QueryTimeoutException("테스트 복구 장애"), 503);
    }
    @Test void cancelRejectsAmbientTransactionBeforeAnyWrites() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                service.cancel(com.limitedbooking.backend.support.TestData.USER_A_ID, id)))
                .isInstanceOf(IllegalStateException.class);
        assertRolledBack(resource, id);
    }

    @Test void alreadyCancelledDoesNotWaitForResourceLock() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        var first = cancel(id);
        assertThat(first.statusCode()).isEqualTo(200);
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var lock = connection.prepareStatement("SELECT id FROM resources WHERE id=? FOR UPDATE")) {
                    lock.setObject(1, resource);
                    try (var rows = lock.executeQuery()) { assertThat(rows.next()).isTrue(); }
                }
                // 다른 연결이 대상 잠금을 가진 상태에서도 이미 취소된 요청은 즉시 기존 값을 반환한다.
                var again = cancel(id);
                assertThat(again.statusCode()).isEqualTo(200);
                assertThat(JSON.readTree(again.body())).isEqualTo(JSON.readTree(first.body()));
            } finally {
                connection.rollback();
            }
        }
        inventory(resource, 1, 1, 0);
    }
    @Test void staleConfirmedReadCannotRepeatStatusUpdateOrRecovery() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        var before = reservations.findByIdAndUser(id, com.limitedbooking.backend.support.TestData.USER_A_ID);
        var first = cancel(id);
        assertThat(first.statusCode()).isEqualTo(200);
        var firstRead = new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(call -> firstRead.getAndSet(false) ? before : call.callRealMethod())
                .when(reservations).findByIdAndUser(id, com.limitedbooking.backend.support.TestData.USER_A_ID);
        try {
            // 조회 이후 다른 취소가 커밋된 상황의 낡은 스냅샷만 주입한다. UPDATE는 실제 PostgreSQL이다.
            var again = cancel(id);
            assertThat(again.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(again.body())).isEqualTo(JSON.readTree(first.body()));
            inventory(resource, 1, 1, 0);
        } finally {
            org.mockito.Mockito.reset(reservations);
        }
    }

    @Test void originalKeyAfterCancellationReplaysCreationWhileGetStaysCancelled() throws Exception {
        UUID resource = resource(1);
        var first = create(resource, "USER_A", "original");
        assertThat(first.statusCode()).isEqualTo(201);
        UUID id = UUID.fromString(JSON.readTree(first.body()).get("id").asString());
        var cancelled = cancel(id);
        assertThat(cancelled.statusCode()).isEqualTo(200);
        var replay = create(resource, "USER_A", "original");
        assertThat(replay.statusCode()).isEqualTo(201);
        assertThat(replay.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(replay.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
        assertThat(JSON.readTree(replay.body())).isEqualTo(JSON.readTree(first.body()));
        assertThat(JSON.readTree(replay.body()).get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(JSON.readTree(get(id).body())).isEqualTo(JSON.readTree(cancelled.body()));
        inventory(resource, 1, 1, 0);
    }
    @Test void soldOutKeyCanReserveRestoredInventoryAsNewReservation() throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        problem(create(resource, "USER_B", "K2"), 409, "SOLD_OUT");
        inventory(resource, 0, 1, 1);
        assertThat(cancel(id).statusCode()).isEqualTo(200);
        var retry = create(resource, "USER_B", "K2");
        assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("false");
        UUID newId = UUID.fromString(JSON.readTree(retry.body()).get("id").asString());
        assertThat(newId).isNotEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT user_id FROM reservations WHERE id=?", UUID.class, newId))
                .isEqualTo(com.limitedbooking.backend.support.TestData.USER_B_ID);
        assertThat(JSON.readTree(get(id).body()).get("status").asString()).isEqualTo("CANCELLED");
        inventory(resource, 0, 2, 1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/reservations/{id}/%63ancel", "/api/%72eservations/{id}/cancel"
    })
    void encodedCancelPathsCannotBypassEmptyBodyContract(String template) throws Exception {
        UUID resource = resource(1), id = confirmed(resource);
        var response = request("POST", template.replace("{id}", id.toString()), "USER_A", "{}", null);
        var current = JSON.readTree(get(id).body());
        org.assertj.core.api.SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(response.statusCode()).as("비어 있지 않은 취소 본문").isEqualTo(400);
            soft.assertThat(current.get("status").asString()).as("거부 후 예약 상태").isEqualTo("CONFIRMED");
            soft.assertThat(current.get("cancelledAt").isNull()).as("거부 후 취소 시각").isTrue();
            soft.assertThat(jdbc.queryForObject("SELECT available FROM resources WHERE id=?", Integer.class, resource))
                    .as("거부 후 수량").isZero();
        });
        problem(response, 400, "INVALID_REQUEST");
        inventory(resource, 0, 1, 1);
        var valid = request("POST", template.replace("{id}", id.toString()), "USER_A", null, null);
        assertThat(valid.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(valid.body()).get("status").asString()).isEqualTo("CANCELLED");
        inventory(resource, 1, 1, 0);
    }

}
