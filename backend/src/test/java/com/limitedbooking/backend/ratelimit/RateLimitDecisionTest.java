package com.limitedbooking.backend.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class RateLimitDecisionTest {
    @ParameterizedTest @CsvSource({"0,1", "1,1", "999,1", "1000,1", "1001,2", "60000,60", "9223372036854775807,9223372036854776"})
    void roundsDeniedTtlWithoutOverflow(long ttl, long seconds) {
        assertThat(RateLimitDecision.fromRedis(List.of(21L, ttl),20))
                .isEqualTo(new RateLimitDecision(false,seconds));
    }
    @Test void allowsThroughTwenty() {
        for (long count : new long[]{1,19,20})
            assertThat(RateLimitDecision.fromRedis(List.of(count,60000L),20))
                    .isEqualTo(new RateLimitDecision(true,0));
    }
    @Test void rejectsInconsistentPublicDecisions() {
        for (long seconds : new long[]{-1,1,Long.MAX_VALUE})
            assertThatThrownBy(() -> new RateLimitDecision(true,seconds)).isInstanceOf(IllegalArgumentException.class);
        for (long seconds : new long[]{-1,0})
            assertThatThrownBy(() -> new RateLimitDecision(false,seconds)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMalformedScriptResults() {
        List<List<?>> invalid = new ArrayList<>(List.of(List.of(), List.of(1L), List.of(1L,2L,3L),
                List.of("1",1L), List.of(1L,"2"), List.of(1,2), List.of(0L,1L),
                List.of(-1L,1L), List.of(1L,-1L), List.of(1L,-2L), Arrays.asList(null,1L), Arrays.asList(1L,null), List.of(1.0,2L)));
        invalid.add(null);
        for (var result : invalid)
            assertThatThrownBy(() -> RateLimitDecision.fromRedis(result,20)).isInstanceOf(IllegalStateException.class);
    }
}
