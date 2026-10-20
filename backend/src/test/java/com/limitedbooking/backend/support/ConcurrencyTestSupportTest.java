package com.limitedbooking.backend.support;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class ConcurrencyTestSupportTest {
    @Test void queuesOneHundredRequestsWithAtMostTenWorkersAndCollectsAll() {
        var active = new AtomicInteger(); var max = new AtomicInteger();
        List<Callable<Integer>> jobs = IntStream.range(0,100).mapToObj(i -> (Callable<Integer>) () -> {
            max.accumulateAndGet(active.incrementAndGet(), Math::max);
            try { return i; } finally { active.decrementAndGet(); }
        }).toList();
        var run = new ConcurrencyTestSupport(new ConcurrencyTestSupport.StopState());
        var result = run.run(jobs, Duration.ofSeconds(3));
        assertThat(result).hasSize(100).allSatisfy(r -> assertThat(r.failure()).isNull());
        assertThat(result.stream().map(ConcurrencyTestSupport.Outcome::value)).containsExactlyElementsOf(IntStream.range(0,100).boxed().toList());
        assertThat(max.get()).isBetween(1,10);
        assertThat(run.terminated()).isTrue();
    }
    @Test void retainsEveryExceptionInsteadOfDroppingFailedFuture() {
        var error = new IllegalArgumentException("job fault");
        var run = new ConcurrencyTestSupport(new ConcurrencyTestSupport.StopState());
        var result = run.run(List.of(() -> 1, () -> { throw error; }, () -> 3), Duration.ofSeconds(2));
        assertThat(result).hasSize(3);
        assertThat(result.get(1).failure()).isSameAs(error);
        assertThat(result.get(2).value()).isEqualTo(3);
        assertThat(run.terminated()).isTrue();
    }
    @Test void commonDeadlineCollectsTimeoutsAndTerminatesInterruptibleTasks() {
        var gate = new CountDownLatch(1);
        var run = new ConcurrencyTestSupport(new ConcurrencyTestSupport.StopState());
        var result = run.run(List.of(() -> { gate.await(); return 1; }, () -> { gate.await(); return 2; }), Duration.ofMillis(50));
        assertThat(result).hasSize(2).allSatisfy(r -> assertThat(r.failure()).isInstanceOf(TimeoutException.class));
        assertThat(run.terminated()).isTrue();
    }
    @Test void barrierTimeoutFailsAndReleaseAllUnblocksOtherWaiters() {
        var gate = new ConcurrencyTestSupport.Gate(2, Duration.ofMillis(20));
        assertThatThrownBy(gate::meet).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(TimeoutException.class);
        gate.release();
    }
    @Test void firstStopCauseSurvivesNewRoundAndPreventsResetPreparationAndSubmission() {
        var state = new ConcurrencyTestSupport.StopState();
        var original = new IllegalStateException("first cleanup");
        state.stop(original); state.stop(new IllegalStateException("later cleanup"));
        var calls = new AtomicInteger();
        for (int round=0; round<2; round++) {
            assertThatThrownBy(() -> { state.check(); calls.incrementAndGet(); })
                .isInstanceOf(IllegalStateException.class).hasCause(original);
        }
        assertThat(calls).hasValue(0);
    }
    @Test void cleanupFailureIsSuppressedWithoutReplacingOriginal() {
        var original = new IllegalArgumentException("job");
        var cleanup = new IllegalStateException("cleanup");
        assertThat(ConcurrencyTestSupport.preserve(original, cleanup)).isSameAs(original);
        assertThat(original.getSuppressed()).containsExactly(cleanup);
        assertThat(ConcurrencyTestSupport.preserve(null, cleanup)).isSameAs(cleanup);
    }
    @Test void rejectsMissingObservedPath() {
        var events = new ConcurrencyTestSupport.Events();
        assertThatThrownBy(() -> events.require("unique")).isInstanceOf(AssertionError.class);
        events.add("unique", "request", false, "23505/uq_reservations_user_key");
        assertThat(events.require("unique").sequence()).isEqualTo(1);
    }
    @Test void shutdownFailurePermanentlyBlocksNextRun() throws Exception {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var state = new ConcurrencyTestSupport.StopState();
        var run = new ConcurrencyTestSupport(state, Duration.ofMillis(20));
        try {
            assertThatThrownBy(() -> run.run(List.of(() -> {
                started.countDown();
                while(release.getCount()>0) { try { release.await(); } catch(InterruptedException ignored) { } }
                return 1;
            }), Duration.ofMillis(100))).isInstanceOf(IllegalStateException.class);
            assertThat(started.getCount()).isZero();
            assertThatThrownBy(state::check).isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> new ConcurrencyTestSupport(state).run(List.of(() -> 2), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
        } finally { release.countDown(); }
    }
}
