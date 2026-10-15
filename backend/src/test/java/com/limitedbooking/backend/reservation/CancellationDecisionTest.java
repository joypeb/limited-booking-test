package com.limitedbooking.backend.reservation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationDecisionTest {
    // 잘못된 상태 매핑·기본 반환과 이미 취소된 예약의 재취소 판단을 탐지한다.
    @ParameterizedTest
    @CsvSource({"CONFIRMED, CANCEL_NOW", "CANCELLED, ALREADY_CANCELLED"})
    void mapsReservationStatus(ReservationStatus status, CancellationDecision expected) {
        assertThat(CancellationDecision.from(status)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"CONFIRMED, CANCEL_NOW", "CANCELLED, ALREADY_CANCELLED"})
    void repeatsDecisionWithoutStateChange(ReservationStatus status, CancellationDecision expected) {
        for (int invocation = 0; invocation < 3; invocation++) {
            assertThat(CancellationDecision.from(status)).isEqualTo(expected);
        }
    }

    @Test
    void rejectsMissingInternalStatus() {
        assertThatThrownBy(() -> CancellationDecision.from(null))
                .isInstanceOf(NullPointerException.class);
    }
}
