package com.limitedbooking.backend.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.postgresql.util.*;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

public class UniqueViolationClassifierTest {
    public static PSQLException pg(String state, String constraint) {
        return new PSQLException(new ServerErrorMessage("SERROR\0C"+state+"\0M시험\0n"+constraint+"\0\0"));
    }
    @Test void acceptsExactStateAndConstraintOnSamePgException() {
        assertThat(UniqueViolationClassifier.isReservationUserKeyViolation(pg("23505","uq_reservations_user_key"))).isTrue();
    }
    @Test void traversesCausesAndNextException() {
        var next=new SQLException("wrapper");next.setNextException(pg("23505","uq_reservations_user_key"));
        assertThat(UniqueViolationClassifier.isReservationUserKeyViolation(new RuntimeException(next))).isTrue();
    }
    @ParameterizedTest @CsvSource({"23505,reservations_pkey", "23503,uq_reservations_user_key", "08006,uq_reservations_user_key",
            "23505,prefix_uq_reservations_user_key", "23505,uq_reservations_user_key_suffix"})
    void rejectsOtherMetadata(String state,String constraint) {
        assertThat(UniqueViolationClassifier.isReservationUserKeyViolation(pg(state,constraint))).isFalse();
    }
    @Test void rejectsMissingMetadataOrMessageOnlyMatches() {
        for (Throwable t : new Throwable[]{null,new SQLException("uq_reservations_user_key","23505"),
                new PSQLException("uq_reservations_user_key",PSQLState.UNIQUE_VIOLATION),
                new PSQLException(new ServerErrorMessage("SERROR\0C23505\0Mmissing constraint\0\0"))}) {
            assertThat(UniqueViolationClassifier.isReservationUserKeyViolation(t)).isFalse();
        }
    }
    @Test void neverCombinesStateAndConstraintFromDifferentExceptions() {
        var a=pg("23505","reservations_pkey");a.setNextException(pg("23503","uq_reservations_user_key"));
        assertThat(UniqueViolationClassifier.isReservationUserKeyViolation(a)).isFalse();
    }
    @Test void terminatesOnCauseAndNextCycles() {
        var a=new SQLException("a");var b=new SQLException("b");
        a.initCause(b); b.initCause(a);a.setNextException(b);b.setNextException(a);
        org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(java.time.Duration.ofSeconds(1), () ->
                assertThat(UniqueViolationClassifier.isReservationUserKeyViolation(a)).isFalse());
    }
}
