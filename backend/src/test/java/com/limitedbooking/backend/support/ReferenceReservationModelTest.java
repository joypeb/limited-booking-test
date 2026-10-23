package com.limitedbooking.backend.support;

import org.junit.jupiter.api.Test;

import static com.limitedbooking.backend.support.CommandOutcome.ModelStatus.CANCELLED;
import static com.limitedbooking.backend.support.CommandOutcome.ModelStatus.CONFIRMED;
import static com.limitedbooking.backend.support.ReservationCommand.Key.K1;
import static com.limitedbooking.backend.support.ReservationCommand.Key.K2;
import static com.limitedbooking.backend.support.ReservationCommand.Key.K3;
import static com.limitedbooking.backend.support.ReservationCommand.Target.MISSING;
import static com.limitedbooking.backend.support.ReservationCommand.Target.R1;
import static com.limitedbooking.backend.support.ReservationCommand.Target.R2;
import static com.limitedbooking.backend.support.ReservationCommand.User.A;
import static com.limitedbooking.backend.support.ReservationCommand.User.B;
import static org.assertj.core.api.Assertions.assertThat;

/** data-model.md 전이를 손으로 계산한 예제로 검증한다. Spring·제품 구현·SQL에 의존하지 않는다. */
class ReferenceReservationModelTest {

    @Test
    void initialStatePreservesBothCapacitiesWithoutReservations() {
        var model = new ReferenceReservationModel(0, 5);

        assertInventory(model, 0, 0, 0, 5, 5, 0, 0);
    }

    @Test
    void soldOutDoesNotRetainKeyAndAnotherTargetCanCreate() {
        var model = new ReferenceReservationModel(0, 2);

        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.soldOut());
        assertInventory(model, 0, 0, 0, 2, 2, 0, 0);
        expect(model, new ReservationCommand.Create(A, R2, K1), CommandOutcome.created(0, R2));
        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.conflict());
        expect(model, new ReservationCommand.Get(A, 1), CommandOutcome.viewed(0, CONFIRMED));

        assertReservation(model, 0, A, R2, K1, CONFIRMED);
        assertInventory(model, 0, 0, 0, 2, 1, 1, 1);
    }

    @Test
    void replayUsesOriginalLogicalIdEvenWhenTargetIsSoldOut() {
        var model = new ReferenceReservationModel(1, 2);

        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.created(0, R1));
        assertInventory(model, 1, 0, 1, 2, 2, 0, 1);
        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.replayed(0, R1));
        expect(model, new ReservationCommand.Get(A, 1), CommandOutcome.viewed(0, CONFIRMED));

        assertReservation(model, 0, A, R1, K1, CONFIRMED);
        assertInventory(model, 1, 0, 1, 2, 2, 0, 1);
    }

    @Test
    void sameUserAndKeyOnAnotherTargetConflictsWithoutChangingState() {
        var model = new ReferenceReservationModel(2, 3);

        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.created(0, R1));
        assertInventory(model, 2, 1, 1, 3, 3, 0, 1);
        expect(model, new ReservationCommand.Create(A, R2, K1), CommandOutcome.conflict());
        expect(model, new ReservationCommand.Get(A, 0), CommandOutcome.viewed(0, CONFIRMED));
        expect(model, new ReservationCommand.Get(A, 1), CommandOutcome.reservationNotFound());

        assertReservation(model, 0, A, R1, K1, CONFIRMED);
        assertInventory(model, 2, 1, 1, 3, 3, 0, 1);
    }

    @Test
    void differentUsersCanCreateIndependentlyWithSameKey() {
        var model = new ReferenceReservationModel(3, 1);

        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.created(0, R1));
        expect(model, new ReservationCommand.Create(B, R1, K1), CommandOutcome.created(1, R1));
        expect(model, new ReservationCommand.Get(A, 0), CommandOutcome.viewed(0, CONFIRMED));
        expect(model, new ReservationCommand.Get(B, 1), CommandOutcome.viewed(1, CONFIRMED));

        assertReservation(model, 0, A, R1, K1, CONFIRMED);
        assertReservation(model, 1, B, R1, K1, CONFIRMED);
        assertInventory(model, 3, 1, 2, 1, 1, 0, 2);
    }

    @Test
    void cancellationRestoresOnceAndReplayKeepsCurrentReservationCancelled() {
        var model = new ReferenceReservationModel(2, 1);

        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.created(0, R1));
        assertInventory(model, 2, 1, 1, 1, 1, 0, 1);
        expect(model, new ReservationCommand.Cancel(A, 0), CommandOutcome.cancelled(0, true));
        assertReservation(model, 0, A, R1, K1, CANCELLED);
        assertInventory(model, 2, 2, 0, 1, 1, 0, 1);
        expect(model, new ReservationCommand.Cancel(A, 0), CommandOutcome.cancelled(0, false));
        assertInventory(model, 2, 2, 0, 1, 1, 0, 1);
        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.replayed(0, R1));
        expect(model, new ReservationCommand.Get(A, 1), CommandOutcome.viewed(0, CANCELLED));
        expect(model, new ReservationCommand.Cancel(A, 1), CommandOutcome.cancelled(0, false));

        assertReservation(model, 0, A, R1, K1, CANCELLED);
        assertInventory(model, 2, 2, 0, 1, 1, 0, 1);
    }

    @Test
    void otherUserCannotViewOrCancelAndDoesNotChangeState() {
        var model = new ReferenceReservationModel(1, 2);

        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.created(0, R1));
        expect(model, new ReservationCommand.Create(B, R2, K2), CommandOutcome.created(1, R2));
        expect(model, new ReservationCommand.Get(B, 0), CommandOutcome.reservationNotFound());
        expect(model, new ReservationCommand.Cancel(B, 0), CommandOutcome.reservationNotFound());
        expect(model, new ReservationCommand.Get(A, 1), CommandOutcome.reservationNotFound());
        expect(model, new ReservationCommand.Cancel(A, 1), CommandOutcome.reservationNotFound());
        expect(model, new ReservationCommand.Get(A, 0), CommandOutcome.viewed(0, CONFIRMED));
        expect(model, new ReservationCommand.Get(B, 1), CommandOutcome.viewed(1, CONFIRMED));

        assertReservation(model, 0, A, R1, K1, CONFIRMED);
        assertReservation(model, 1, B, R2, K2, CONFIRMED);
        assertInventory(model, 1, 0, 1, 2, 1, 1, 2);
    }

    @Test
    void refsCountEveryCreateIncludingFailuresAndReplayButNotGetOrCancel() {
        var model = new ReferenceReservationModel(0, 3);

        expect(model, new ReservationCommand.Get(A, 0), CommandOutcome.reservationNotFound());
        expect(model, new ReservationCommand.Cancel(A, 0), CommandOutcome.reservationNotFound());
        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.soldOut()); // ref 0
        expect(model, new ReservationCommand.Create(A, R2, K1), CommandOutcome.created(0, R2)); // ref 1
        expect(model, new ReservationCommand.Get(A, 1), CommandOutcome.viewed(0, CONFIRMED));
        expect(model, new ReservationCommand.Create(A, R2, K1), CommandOutcome.replayed(0, R2)); // ref 2
        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.conflict()); // ref 3
        expect(model, new ReservationCommand.Create(A, MISSING, K2), CommandOutcome.resourceNotFound()); // ref 4
        expect(model, new ReservationCommand.Cancel(A, 2), CommandOutcome.cancelled(0, true));
        expect(model, new ReservationCommand.Create(B, R2, K3), CommandOutcome.created(1, R2)); // ref 5

        for (int ref : new int[]{-1, 0, 3, 4, 6, 9}) {
            expect(model, new ReservationCommand.Get(A, ref), CommandOutcome.reservationNotFound());
            expect(model, new ReservationCommand.Cancel(A, ref), CommandOutcome.reservationNotFound());
        }
        expect(model, new ReservationCommand.Get(A, 1), CommandOutcome.viewed(0, CANCELLED));
        expect(model, new ReservationCommand.Get(A, 2), CommandOutcome.viewed(0, CANCELLED));
        expect(model, new ReservationCommand.Get(B, 5), CommandOutcome.viewed(1, CONFIRMED));

        assertReservation(model, 0, A, R2, K1, CANCELLED);
        assertReservation(model, 1, B, R2, K3, CONFIRMED);
        assertInventory(model, 0, 0, 0, 3, 2, 1, 2);
    }

    @Test
    void missingTargetDoesNotRetainKeyAndExistingKeyTakesPrecedence() {
        var model = new ReferenceReservationModel(2, 1);

        expect(model, new ReservationCommand.Create(A, MISSING, K1), CommandOutcome.resourceNotFound());
        assertInventory(model, 2, 2, 0, 1, 1, 0, 0);
        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.created(0, R1));
        expect(model, new ReservationCommand.Create(A, MISSING, K1), CommandOutcome.conflict());
        expect(model, new ReservationCommand.Get(A, 1), CommandOutcome.viewed(0, CONFIRMED));

        assertReservation(model, 0, A, R1, K1, CONFIRMED);
        assertInventory(model, 2, 1, 1, 1, 1, 0, 1);
    }

    @Test
    void logicalIdsFollowNewReservationOrderAndDifferentKeysAllowAdditionalReservations() {
        var model = new ReferenceReservationModel(3, 2);

        expect(model, new ReservationCommand.Create(A, MISSING, K1), CommandOutcome.resourceNotFound());
        expect(model, new ReservationCommand.Create(A, R2, K1), CommandOutcome.created(0, R2));
        expect(model, new ReservationCommand.Create(A, R2, K1), CommandOutcome.replayed(0, R2));
        expect(model, new ReservationCommand.Create(A, R1, K1), CommandOutcome.conflict());
        expect(model, new ReservationCommand.Create(A, R1, K2), CommandOutcome.created(1, R1));
        expect(model, new ReservationCommand.Create(B, R2, K3), CommandOutcome.created(2, R2));
        expect(model, new ReservationCommand.Get(A, 4), CommandOutcome.viewed(1, CONFIRMED));
        expect(model, new ReservationCommand.Get(B, 5), CommandOutcome.viewed(2, CONFIRMED));

        assertReservation(model, 0, A, R2, K1, CONFIRMED);
        assertReservation(model, 1, A, R1, K2, CONFIRMED);
        assertReservation(model, 2, B, R2, K3, CONFIRMED);
        assertInventory(model, 3, 2, 1, 2, 0, 2, 3);
    }

    private static void expect(ReferenceReservationModel model, ReservationCommand command,
                               CommandOutcome expected) {
        assertThat(model.apply(command)).as("명령 %s", command).isEqualTo(expected);
    }

    private static void assertReservation(ReferenceReservationModel model, int id,
                                          ReservationCommand.User owner,
                                          ReservationCommand.Target target,
                                          ReservationCommand.Key key,
                                          CommandOutcome.ModelStatus status) {
        assertThat(model.owner(id)).as("예약 %s 소유자", id).isEqualTo(owner);
        assertThat(model.target(id)).as("예약 %s 대상", id).isEqualTo(target);
        assertThat(model.key(id)).as("예약 %s 키", id).isEqualTo(key);
        assertThat(model.status(id)).as("예약 %s 상태", id).isEqualTo(status);
    }

    private static void assertInventory(ReferenceReservationModel model,
                                        int capacityR1, int availableR1, int confirmedR1,
                                        int capacityR2, int availableR2, int confirmedR2,
                                        int reservationCount) {
        assertThat(model.capacity(R1)).isEqualTo(capacityR1);
        assertThat(model.available(R1)).isEqualTo(availableR1);
        assertThat(model.confirmed(R1)).isEqualTo(confirmedR1);
        assertThat(model.available(R1) + model.confirmed(R1)).isEqualTo(capacityR1);
        assertThat(model.available(R1)).isBetween(0, capacityR1);
        assertThat(model.capacity(R2)).isEqualTo(capacityR2);
        assertThat(model.available(R2)).isEqualTo(availableR2);
        assertThat(model.confirmed(R2)).isEqualTo(confirmedR2);
        assertThat(model.available(R2) + model.confirmed(R2)).isEqualTo(capacityR2);
        assertThat(model.available(R2)).isBetween(0, capacityR2);
        assertThat(model.reservationCount()).isEqualTo(reservationCount);
    }
}
