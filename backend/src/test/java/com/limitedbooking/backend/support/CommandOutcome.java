package com.limitedbooking.backend.support;

/** 테스트 전용 명령 결과. 제품 상태 타입을 참조하지 않는다. */
public record CommandOutcome(
        Kind kind,
        Integer logicalId,
        ReservationCommand.Target target,
        ModelStatus status,
        Boolean firstCancel) {

    public enum Kind {
        CREATED, REPLAYED, SOLD_OUT, IDEMPOTENCY_CONFLICT, RESOURCE_NOT_FOUND,
        RESERVATION_NOT_FOUND, VIEWED, CANCELLED
    }

    public enum ModelStatus { CONFIRMED, CANCELLED }

    public static CommandOutcome created(int id, ReservationCommand.Target t) {
        return new CommandOutcome(Kind.CREATED, id, t, null, null);
    }

    public static CommandOutcome replayed(int id, ReservationCommand.Target t) {
        return new CommandOutcome(Kind.REPLAYED, id, t, null, null);
    }

    public static CommandOutcome soldOut() {
        return new CommandOutcome(Kind.SOLD_OUT, null, null, null, null);
    }

    public static CommandOutcome conflict() {
        return new CommandOutcome(Kind.IDEMPOTENCY_CONFLICT, null, null, null, null);
    }

    public static CommandOutcome resourceNotFound() {
        return new CommandOutcome(Kind.RESOURCE_NOT_FOUND, null, null, null, null);
    }

    public static CommandOutcome reservationNotFound() {
        return new CommandOutcome(Kind.RESERVATION_NOT_FOUND, null, null, null, null);
    }

    public static CommandOutcome viewed(int id, ModelStatus s) {
        return new CommandOutcome(Kind.VIEWED, id, null, s, null);
    }

    public static CommandOutcome cancelled(int id, boolean first) {
        return new CommandOutcome(Kind.CANCELLED, id, null, null, first);
    }
}
