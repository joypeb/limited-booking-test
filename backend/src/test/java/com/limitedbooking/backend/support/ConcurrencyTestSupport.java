package com.limitedbooking.backend.support;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** 테스트 전용 유한 실행/관측. DB 연결·잠금을 소유하지 않는다. */
public final class ConcurrencyTestSupport {
    public record Outcome<T>(T value, Throwable failure) {}
    private final StopState state;
    private final Duration shutdown;
    private boolean terminated;
    public ConcurrencyTestSupport(StopState state) { this(state, Duration.ofSeconds(10)); }
    ConcurrencyTestSupport(StopState state, Duration shutdown) { this.state=state; this.shutdown=shutdown; }
    public boolean terminated() { return terminated; }

    public <T> List<Outcome<T>> run(List<Callable<T>> jobs, Duration timeout) {
        return run(jobs, timeout, () -> {});
    }
    public <T> List<Outcome<T>> run(List<Callable<T>> jobs, Duration timeout, Runnable releaseAll) {
        state.check();
        if (jobs.isEmpty()) throw new IllegalArgumentException("빈 작업 목록");
        int workers = Math.min(10, jobs.size());
        var ready = new CountDownLatch(workers);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(workers);
        var futures = new ArrayList<Future<T>>();
        var outcomes = new ArrayList<Outcome<T>>();
        long deadline = System.nanoTime()+timeout.toNanos();
        Throwable primary = null;
        try {
            for (var job : jobs) futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new TimeoutException("시작 장벽5초 초과");
                return job.call();
            }));
            if (!ready.await(Math.min(timeout.toNanos(), TimeUnit.SECONDS.toNanos(5)), TimeUnit.NANOSECONDS))
                throw new IllegalStateException("작업자 준비 시간 초과");
            start.countDown();
            for (var future : futures) {
                try {
                    // 이미 완료된 작업은 deadline이 지나도 결과·원래 예외를 잃지 않는다.
                    T value = future.isDone() ? future.get() : future.get(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
                    outcomes.add(new Outcome<>(value,null));
                } catch (ExecutionException failure) {
                    outcomes.add(new Outcome<>(null,failure.getCause()));
                } catch (TimeoutException failure) {
                    outcomes.add(new Outcome<>(null,failure));
                }
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            primary = new IllegalStateException("경쟁 수집 중 인터럽트",failure);
        } catch (RuntimeException | Error failure) { primary=failure; }
        finally {
            start.countDown();
            try { releaseAll.run(); } catch (RuntimeException | Error cleanup) { primary=preserve(primary != null ? primary : outcomes.stream().map(Outcome::failure).filter(Objects::nonNull).findFirst().orElse(null),cleanup); }
            // 클라이언트 작업 취소 이전에 외부 장벽은 호출자가 releaseAll로 해제한다.
            for (var future : futures) if (!future.isDone()) future.cancel(true);
            executor.shutdownNow();
            try {
                terminated = executor.awaitTermination(shutdown.toNanos(),TimeUnit.NANOSECONDS);
                if (!terminated) throw new IllegalStateException("Executor 종료 시간 초과");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                var cleanup = new IllegalStateException("Executor 종료 인터럽트",failure);
                state.stop(cleanup); primary=preserve(primary != null ? primary : outcomes.stream().map(Outcome::failure).filter(Objects::nonNull).findFirst().orElse(null),cleanup);
            } catch (RuntimeException cleanup) { state.stop(cleanup); primary=preserve(primary != null ? primary : outcomes.stream().map(Outcome::failure).filter(Objects::nonNull).findFirst().orElse(null),cleanup); }
        }
        if (primary instanceof Error error) throw error;
        if (primary instanceof RuntimeException failure) throw failure;
        if (primary != null) throw new IllegalStateException("경쟁 실행/정리 실패", primary);
        return List.copyOf(outcomes);
    }
    public static Throwable preserve(Throwable original, Throwable cleanup) {
        if (original==null) return cleanup;
        if (original!=cleanup) original.addSuppressed(cleanup);
        return original;
    }
    public static final class StopState {
        private final AtomicReference<Throwable> first = new AtomicReference<>();
        public void stop(Throwable cause) { first.compareAndSet(null,Objects.requireNonNull(cause)); }
        public void check() {
            if (first.get()!=null) throw new IllegalStateException("최초 종료 실패: 다음 회차 금지",first.get());
        }
    }
    public static final class Gate {
        private final CountDownLatch ready;
        private final CountDownLatch released = new CountDownLatch(1);
        private final Duration timeout;
        public Gate(int parties, Duration timeout) { this.ready=new CountDownLatch(parties); this.timeout=timeout; }
        public void meet() {
            ready.countDown();
            if (ready.getCount()==0) released.countDown();
            await(released);
        }
        public void awaitReady() { await(ready); }
        public void awaitRelease() { ready.countDown(); await(released); }
        private void await(CountDownLatch latch) {
            try {
                if (!latch.await(timeout.toNanos(),TimeUnit.NANOSECONDS)) throw new TimeoutException("장벽 시간 초과");
            } catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            catch (TimeoutException failure) { throw new IllegalStateException(failure); }
        }
        public void release() { released.countDown(); }
    }
    public record Event(long sequence, String phase, String request, boolean transaction, String detail) {}
    public static final class Events {
        private final List<Event> entries = new ArrayList<>();
        public synchronized void add(String phase, String request, boolean tx, String detail) {
            entries.add(new Event(entries.size()+1,phase,request,tx,detail));
        }
        public synchronized List<Event> all() { return List.copyOf(entries); }
        public synchronized Event require(String phase) {
            return entries.stream().filter(e -> e.phase().equals(phase)).findFirst()
                .orElseThrow(() -> new AssertionError("미관측 path="+phase));
        }
    }
}
