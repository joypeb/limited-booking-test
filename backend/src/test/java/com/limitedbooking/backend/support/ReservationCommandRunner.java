package com.limitedbooking.backend.support;

import com.limitedbooking.backend.reservation.CreatedReservationView;
import com.limitedbooking.backend.reservation.IdempotencyKey;
import com.limitedbooking.backend.reservation.ReservationService;
import com.limitedbooking.backend.reservation.ReservationStatus;
import com.limitedbooking.backend.reservation.ReservationView;
import com.limitedbooking.backend.reservation.ReservationWriter;
import com.limitedbooking.backend.resource.ResourceService;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 테스트 전용 실행기. 실제 제품 API·DB를 호출해 명령을 실행한다. */
public final class ReservationCommandRunner {
    private final ReservationService reservations;
    private final ResourceService resources;
    private final JdbcTemplate jdbc;
    private final Map<ReservationCommand.Target, UUID> resourceIds = new EnumMap<>(ReservationCommand.Target.class);
    private final List<UUID> reservationIdsByLogicalId = new ArrayList<>();
    private final List<UUID> createRefs = new ArrayList<>();
    private final Map<UUID, CreatedReservationView> originals = new HashMap<>();
    private final Map<UUID, Instant> cancellationTimes = new HashMap<>();

    public ReservationCommandRunner(
            ReservationService reservations, ResourceService resources, DataSource dataSource) {
        this.reservations = reservations;
        this.resources = resources;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public void prepare(int capacityR1, int capacityR2) {
        resourceIds.clear();
        reservationIdsByLogicalId.clear();
        createRefs.clear();
        originals.clear();
        cancellationTimes.clear();
        resourceIds.put(ReservationCommand.Target.R1, resources.create("속성 대상 R1", capacityR1).id());
        resourceIds.put(ReservationCommand.Target.R2, resources.create("속성 대상 R2", capacityR2).id());
        resourceIds.put(ReservationCommand.Target.MISSING, UUID.randomUUID());
    }

    public CommandOutcome run(ReservationCommand command) {
        CommandOutcome outcome;
        try {
            outcome = switch (command) {
                case ReservationCommand.Create create -> create(create);
                case ReservationCommand.Get get -> get(get);
                case ReservationCommand.Cancel cancel -> cancel(cancel);
            };
        } catch (ReservationWriter.SoldOutException failure) {
            outcome = CommandOutcome.soldOut();
        } catch (ReservationService.IdempotencyConflictException failure) {
            outcome = CommandOutcome.conflict();
        } catch (ResourceService.ResourceNotFoundException failure) {
            outcome = CommandOutcome.resourceNotFound();
        } catch (ReservationService.ReservationNotFoundException failure) {
            outcome = CommandOutcome.reservationNotFound();
        }
        if (command instanceof ReservationCommand.Create) {
            createRefs.add(outcome.logicalId() == null ? null : reservationIdsByLogicalId.get(outcome.logicalId()));
        }
        return outcome;
    }

    private CommandOutcome create(ReservationCommand.Create command) {
        var result = reservations.create(userId(command.user()), resourceIds.get(command.target()),
                IdempotencyKey.parse(command.key().name()));
        CreatedReservationView body = result.body();
        assertThat(body.id()).as("생성 응답 id").isNotNull();
        assertThat(body.createdAt()).as("생성 시각").isNotNull();
        assertThat(body.status()).as("생성·재현 응답 상태").isEqualTo(ReservationStatus.CONFIRMED);
        ReservationCommand.Target actualTarget = target(body.resourceId());
        int id = reservationIdsByLogicalId.indexOf(body.id());
        if (id >= 0) {
            assertThat(result.replayed()).as("기존 예약 재현 표시").isTrue();
            assertThat(body).as("최초 생성 응답의 id·대상·상태·시각 재현").isEqualTo(originals.get(body.id()));
            return CommandOutcome.replayed(id, actualTarget);
        }
        assertThat(result.replayed()).as("새 예약 재현 표시").isFalse();
        id = reservationIdsByLogicalId.size();
        reservationIdsByLogicalId.add(body.id());
        originals.put(body.id(), body);
        return CommandOutcome.created(id, actualTarget);
    }

    private CommandOutcome get(ReservationCommand.Get command) {
        UUID requestedId = resolve(command.ref());
        ReservationView view = reservations.get(userId(command.user()), requestedId);
        int id = assertOriginal(view, requestedId);
        if (view.status() == ReservationStatus.CONFIRMED) {
            assertThat(view.cancelledAt()).as("확정 예약 취소 시각").isNull();
        } else {
            assertThat(cancellationTimes).as("조회 전 최초 취소 관측").containsKey(requestedId);
            assertThat(view.cancelledAt()).as("현재 예약 최초 취소 시각")
                    .isEqualTo(cancellationTimes.get(requestedId));
        }
        return CommandOutcome.viewed(id, CommandOutcome.ModelStatus.valueOf(view.status().name()));
    }

    private CommandOutcome cancel(ReservationCommand.Cancel command) {
        UUID requestedId = resolve(command.ref());
        ReservationView view = reservations.cancel(userId(command.user()), requestedId);
        int id = assertOriginal(view, requestedId);
        assertThat(view.status()).as("취소 응답 상태").isEqualTo(ReservationStatus.CANCELLED);
        assertThat(view.cancelledAt()).as("취소 응답 시각").isNotNull();
        Instant firstTime = cancellationTimes.putIfAbsent(requestedId, view.cancelledAt());
        if (firstTime != null) {
            assertThat(view.cancelledAt()).as("중복 취소의 최초 시각 재현").isEqualTo(firstTime);
        }
        return CommandOutcome.cancelled(id, firstTime == null);
    }

    private int assertOriginal(ReservationView view, UUID requestedId) {
        assertThat(view.id()).as("조회·취소 요청 id").isEqualTo(requestedId);
        CreatedReservationView original = originals.get(requestedId);
        assertThat(original).as("성공한 생성 명령의 원 응답").isNotNull();
        assertThat(view.resourceId()).as("예약 대상 불변").isEqualTo(original.resourceId());
        assertThat(view.createdAt()).as("최초 생성 시각 불변").isEqualTo(original.createdAt());
        return reservationIdsByLogicalId.indexOf(requestedId);
    }

    private UUID resolve(int ref) {
        if (ref >= 0 && ref < createRefs.size() && createRefs.get(ref) != null) {
            return createRefs.get(ref);
        }
        // 실제 서비스의 예약 없음 경로를 호출한다.
        return UUID.randomUUID();
    }

    private static UUID userId(ReservationCommand.User user) {
        return switch (user) {
            case A -> TestData.USER_A_ID;
            case B -> TestData.USER_B_ID;
        };
    }

    private ReservationCommand.Target target(UUID resourceId) {
        return resourceIds.entrySet().stream()
                .filter(entry -> entry.getValue().equals(resourceId))
                .map(Map.Entry::getKey).findFirst()
                .orElseThrow(() -> new AssertionError("알 수 없는 응답 대상 " + resourceId));
    }

    private record Inventory(UUID id, int capacity, int available, long confirmed) {}
    private record ReservationState(UUID id, UUID resourceId, UUID userId, String key, String status) {}
    private record SnapshotRow(Inventory inventory, long reservationCount, long duplicateGroups,
                               ReservationState reservation) {}

    public void assertFinalStateMatches(ReferenceReservationModel model) {
        var rows = jdbc.query("""
                WITH inventory AS (
                    SELECT r.id, r.capacity, r.available,
                           count(v.id) FILTER (WHERE v.status = 'CONFIRMED') AS confirmed
                    FROM resources r LEFT JOIN reservations v ON v.resource_id = r.id
                    GROUP BY r.id, r.capacity, r.available
                ), totals AS (
                    SELECT count(*) AS reservation_count FROM reservations
                ), duplicates AS (
                    SELECT count(*) AS duplicate_groups FROM (
                        SELECT user_id, idempotency_key FROM reservations
                        GROUP BY user_id, idempotency_key HAVING count(*) > 1
                    ) repeated_keys
                )
                SELECT i.id AS inventory_id, i.capacity, i.available, i.confirmed,
                       t.reservation_count, d.duplicate_groups,
                       v.id AS reservation_id, v.resource_id, v.user_id, v.idempotency_key, v.status
                FROM inventory i LEFT JOIN reservations v ON v.resource_id = i.id
                CROSS JOIN totals t CROSS JOIN duplicates d
                """, (rs, rowNumber) -> {
            UUID reservationId = rs.getObject("reservation_id", UUID.class);
            return new SnapshotRow(new Inventory(rs.getObject("inventory_id", UUID.class),
                    rs.getInt("capacity"), rs.getInt("available"), rs.getLong("confirmed")),
                    rs.getLong("reservation_count"), rs.getLong("duplicate_groups"),
                    reservationId == null ? null : new ReservationState(reservationId,
                            rs.getObject("resource_id", UUID.class), rs.getObject("user_id", UUID.class),
                            rs.getString("idempotency_key"), rs.getString("status")));
        });
        assertThat(rows).as("최종 DB 관측 행").isNotEmpty();
        // CROSS JOIN한 전체 집계는 모든 행에서 같으므로 한 번만 검사한다.
        assertThat(rows.getFirst().reservationCount()).as("전체 예약 행 수").isEqualTo(model.reservationCount());
        assertThat(rows.getFirst().duplicateGroups()).as("사용자·키 중복 그룹").isZero();
        Map<UUID, Inventory> inventories = new HashMap<>();
        Map<UUID, ReservationState> stored = new HashMap<>();
        for (SnapshotRow row : rows) {
            inventories.put(row.inventory().id(), row.inventory());
            if (row.reservation() != null) stored.put(row.reservation().id(), row.reservation());
        }
        assertThat(inventories.keySet()).as("저장 대상 두 개")
                .containsExactlyInAnyOrder(resourceIds.get(ReservationCommand.Target.R1),
                        resourceIds.get(ReservationCommand.Target.R2));
        for (var target : List.of(ReservationCommand.Target.R1, ReservationCommand.Target.R2)) {
            Inventory inventory = inventories.get(resourceIds.get(target));
            assertThat(inventory.capacity()).as("%s 최초 수량", target).isEqualTo(model.capacity(target));
            assertThat(inventory.available()).as("%s 잔여 수량", target).isEqualTo(model.available(target));
            assertThat(inventory.confirmed()).as("%s 확정 예약 수", target).isEqualTo(model.confirmed(target));
            assertThat(inventory.available()).isBetween(0, inventory.capacity());
            assertThat(inventory.available() + inventory.confirmed()).as("%s 수량 보존", target)
                    .isEqualTo(inventory.capacity());
        }
        assertThat(stored).as("관측한 전체 예약").hasSize(model.reservationCount());
        assertThat(reservationIdsByLogicalId).as("실행기의 논리 예약").hasSize(model.reservationCount());
        for (int id = 0; id < reservationIdsByLogicalId.size(); id++) {
            ReservationState reservation = stored.get(reservationIdsByLogicalId.get(id));
            assertThat(reservation).as("논리 예약 #%s DB 행", id).isNotNull();
            assertThat(reservation.status()).as("논리 예약 #%s 상태", id).isEqualTo(model.status(id).name());
            assertThat(reservation.resourceId()).as("논리 예약 #%s 대상", id)
                    .isEqualTo(resourceIds.get(model.target(id)));
            assertThat(reservation.userId()).as("논리 예약 #%s 소유자", id).isEqualTo(userId(model.owner(id)));
            assertThat(reservation.key()).as("논리 예약 #%s 키", id).isEqualTo(model.key(id).name());
        }
    }
}
