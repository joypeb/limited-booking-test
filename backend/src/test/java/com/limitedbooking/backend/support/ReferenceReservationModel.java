package com.limitedbooking.backend.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 테스트 전용 독립 모델. SQL·제품 구현을 호출하지 않는다. */
public final class ReferenceReservationModel {
    private final Map<ReservationCommand.Target, Integer> capacity = new HashMap<>();
    private final Map<ReservationCommand.Target, Integer> available = new HashMap<>();
    private final Map<UserKey, Integer> keyIndex = new HashMap<>();
    private final List<Logical> reservations = new ArrayList<>();
    // 실패한 Create도 null 항목으로 보존한다. 재현은 원 예약 id를 가리킨다.
    private final List<Integer> createRefs = new ArrayList<>();

    private record UserKey(ReservationCommand.User user, ReservationCommand.Key key) {}

    private record Logical(ReservationCommand.User owner, ReservationCommand.Target target,
                           ReservationCommand.Key key, CommandOutcome.ModelStatus status) {}

    public ReferenceReservationModel(int capacityR1, int capacityR2) {
        capacity.put(ReservationCommand.Target.R1, capacityR1);
        capacity.put(ReservationCommand.Target.R2, capacityR2);
        available.putAll(capacity);
    }

    public CommandOutcome apply(ReservationCommand command) {
        return switch (command) {
            case ReservationCommand.Create create -> {
                CommandOutcome outcome = create(create);
                createRefs.add(outcome.logicalId());
                yield outcome;
            }
            case ReservationCommand.Get get -> {
                Integer id = ownedRef(get.user(), get.ref());
                yield id == null ? CommandOutcome.reservationNotFound()
                        : CommandOutcome.viewed(id, status(id));
            }
            case ReservationCommand.Cancel cancel -> cancel(cancel);
        };
    }

    private CommandOutcome create(ReservationCommand.Create command) {
        var userKey = new UserKey(command.user(), command.key());
        Integer existingId = keyIndex.get(userKey);
        if (existingId != null) {
            return target(existingId) == command.target()
                    ? CommandOutcome.replayed(existingId, command.target())
                    : CommandOutcome.conflict();
        }
        if (command.target() == ReservationCommand.Target.MISSING) {
            return CommandOutcome.resourceNotFound();
        }
        int remaining = available.get(command.target());
        if (remaining == 0) {
            return CommandOutcome.soldOut();
        }
        int id = reservations.size();
        reservations.add(new Logical(command.user(), command.target(), command.key(),
                CommandOutcome.ModelStatus.CONFIRMED));
        keyIndex.put(userKey, id);
        available.put(command.target(), remaining - 1);
        return CommandOutcome.created(id, command.target());
    }

    private CommandOutcome cancel(ReservationCommand.Cancel command) {
        Integer id = ownedRef(command.user(), command.ref());
        if (id == null) {
            return CommandOutcome.reservationNotFound();
        }
        Logical reservation = reservations.get(id);
        if (reservation.status() == CommandOutcome.ModelStatus.CANCELLED) {
            return CommandOutcome.cancelled(id, false);
        }
        reservations.set(id, new Logical(reservation.owner(), reservation.target(), reservation.key(),
                CommandOutcome.ModelStatus.CANCELLED));
        available.put(reservation.target(), available.get(reservation.target()) + 1);
        return CommandOutcome.cancelled(id, true);
    }

    private Integer ownedRef(ReservationCommand.User user, int ref) {
        if (ref < 0 || ref >= createRefs.size()) {
            return null;
        }
        Integer id = createRefs.get(ref);
        return id != null && owner(id) == user ? id : null;
    }

    public int available(ReservationCommand.Target t) {
        return available.get(t);
    }

    public int confirmed(ReservationCommand.Target t) {
        int count = 0;
        for (Logical reservation : reservations) {
            if (reservation.target() == t && reservation.status() == CommandOutcome.ModelStatus.CONFIRMED) {
                count++;
            }
        }
        return count;
    }

    public int capacity(ReservationCommand.Target t) {
        return capacity.get(t);
    }

    public int reservationCount() {
        return reservations.size();
    }

    public CommandOutcome.ModelStatus status(int logicalId) {
        return reservations.get(logicalId).status();
    }

    public ReservationCommand.Target target(int logicalId) {
        return reservations.get(logicalId).target();
    }

    public ReservationCommand.User owner(int logicalId) {
        return reservations.get(logicalId).owner();
    }

    public ReservationCommand.Key key(int logicalId) {
        return reservations.get(logicalId).key();
    }
}
