package com.limitedbooking.backend.reservation;

import com.limitedbooking.backend.support.*;
import com.limitedbooking.backend.support.ConcurrencyTestSupport.*;
import com.limitedbooking.backend.resource.ResourceRepository;
import com.limitedbooking.backend.common.UniqueViolationClassifier;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DirtiesContext
@TestExecutionListeners(listeners=ReservationConcurrencyIT.Cleanup.class,
        mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservationConcurrencyIT extends ReservationApiTestSupport {
    static final ContainerTestSupport containers=ContainerTestSupport.postgresOnly();
    static final StopState STOP=new StopState();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        containers.registerLocalAuthAndDatabaseProperties(p);
        p.add("spring.datasource.hikari.maximum-pool-size",()->10);
        p.add("spring.datasource.hikari.connection-timeout",()->3000);
    }
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder() { return 2999; }
        @Override public void afterTestClass(TestContext c) { containers.close(); }
    }
    @MockitoSpyBean(reset=MockReset.NONE) ReservationRepository reservations;
    @MockitoSpyBean(reset=MockReset.NONE) ResourceRepository resources;
    @MockitoSpyBean(reset=MockReset.NONE) ReservationService service;
    final AtomicInteger inFlight=new AtomicInteger();
    final AtomicInteger poolMax=new AtomicInteger();
    final Events events=new Events();
    final List<Gate> gates=new CopyOnWriteArrayList<>();
    final ThreadLocal<String> requestId=new ThreadLocal<>();
    final AtomicBoolean settingsChecked=new AtomicBoolean();
    ScheduledExecutorService sampler;
    boolean observed;
    boolean reported;
    String caseId;
    int repetition;
    long started;
    List<Outcome<HttpResponse<String>>> results;
    Map<UUID,DatabaseAssertions.InventorySnapshot> lastSnapshot;

    @Override protected void beforeDatabaseReset() { STOP.check(); }
    @BeforeEach void observe() {
        observed=true;
        doAnswer(call -> track(call, "create", ((IdempotencyKey)call.getArgument(2)).value()))
                .when(service).create(any(),any(),any());
        doAnswer(call -> track(call, "cancel", call.getArgument(1).toString()))
                .when(service).cancel(any(),any());
        doAnswer(call -> {
            var found=call.callRealMethod();
            event("lookup", ((Optional<?>)found).isPresent()?"present":"empty");
            return found;
        }).when(reservations).findByUserAndKey(any(),anyString());
        doAnswer(call -> {
            int changed=(int)call.callRealMethod();
            if (settingsChecked.compareAndSet(false,true)) {
                assertThat(jdbc.queryForObject("SHOW lock_timeout",String.class)).isEqualTo("2s");
                assertThat(jdbc.queryForObject("SHOW statement_timeout",String.class)).isEqualTo("5s");
                assertThat(jdbc.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("read committed");
                assertThat(jdbc.queryForObject("SHOW timezone",String.class)).endsWith("UTC");
                event("settings","read committed/2s/5s/"+jdbc.queryForObject("SHOW server_version",String.class));
            }
            event("decrement",call.getArgument(0)+":"+changed);
            return changed;
        }).when(resources).decrementIfAvailable(any());
        doAnswer(call -> {
            try { var stored=call.callRealMethod(); event("insert","stored"); return stored; }
            catch (RuntimeException failure) {
                if (UniqueViolationClassifier.isReservationUserKeyViolation(failure))
                    event("unique","23505/uq_reservations_user_key");
                throw failure;
            }
        }).when(reservations).insert(any(),any(),any(),any(),any());
        doAnswer(call -> { int changed=(int)call.callRealMethod(); event("cancel-update",String.valueOf(changed)); return changed; })
                .when(reservations).markCancelled(any(),any(),any());
        doAnswer(call -> { int changed=(int)call.callRealMethod(); event("restore",String.valueOf(changed)); return changed; })
                .when(resources).incrementIfBelowCapacity(any());
        sampler=Executors.newSingleThreadScheduledExecutor();
        sampler.scheduleAtFixedRate(() -> poolMax.accumulateAndGet(
                ((HikariDataSource)dataSource).getHikariPoolMXBean().getActiveConnections(),Math::max),0,10,TimeUnit.MILLISECONDS);
    }
    Object track(org.mockito.invocation.InvocationOnMock call,String operation,String id) throws Throwable {
        inFlight.incrementAndGet(); requestId.set(id); event(operation+"-enter",id);
        try { return call.callRealMethod(); }
        finally { event(operation+"-exit",id); requestId.remove(); inFlight.decrementAndGet(); }
    }
    void event(String phase,String detail) {
        events.add(phase,Objects.toString(requestId.get(),"client"),
                TransactionSynchronizationManager.isActualTransactionActive(),detail);
    }
    Gate gate(int parties) { var gate=new Gate(parties,Duration.ofSeconds(5)); gates.add(gate); return gate; }
    void releaseAll() { gates.forEach(Gate::release); }
    void quiescent() throws InterruptedException {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(inFlight.get()!=0 && System.nanoTime()<until) TimeUnit.MILLISECONDS.sleep(10);
        if(inFlight.get()!=0) throw new IllegalStateException("서버 in-flight 종료10초 초과");
    }
    @Override @AfterEach void cleanup() throws Exception {
        if(!observed) return;
        Throwable failure=null;
        releaseAll();
        try { quiescent(); } catch(Throwable error) { STOP.stop(error); failure=error; }
        if(sampler!=null) {
            sampler.shutdownNow();
            try { if(!sampler.awaitTermination(10,TimeUnit.SECONDS)) throw new IllegalStateException("sampler 종료 실패"); }
            catch(Throwable error) { STOP.stop(error); failure=ConcurrencyTestSupport.preserve(failure,error); }
        }
        // 종료가 확인되지 않으면 Spring 자동 Spy reset도 비활성화하여 다음 회차의 오염을 막는다.
        if(failure==null) reset(service,reservations,resources);
        try { super.cleanup(); client=null; } catch(Throwable error) { STOP.stop(error); failure=ConcurrencyTestSupport.preserve(failure,error); }
        if(results!=null && !reported) {
            System.out.println("FAILED-RUN case="+caseId+" repetition="+repetition+" outcomes="+results+" snapshot="+lastSnapshot);
            System.out.println("PATH case="+caseId+" repetition="+repetition+" events="+events.all());
        }
        System.out.println("CLEANUP case="+caseId+" repetition="+repetition+" server="+inFlight.get()+" http="+(client==null)+" failure="+(failure==null?"none":failure.getClass().getSimpleName()));
        if(failure instanceof Exception error) throw error;
        if(failure instanceof Error error) throw error;
    }
    void begin(String id,RepetitionInfo info) { caseId=id; repetition=info.getCurrentRepetition(); started=System.nanoTime(); }
    List<HttpResponse<String>> run(List<Callable<HttpResponse<String>>> jobs) throws Exception {
        var runner=new ConcurrencyTestSupport(STOP);
        results=runner.run(jobs,Duration.ofSeconds(jobs.size()==100?120:30),this::releaseAll);
        try { quiescent(); } catch(Exception failure) { STOP.stop(failure); throw failure; }
        assertThat(runner.terminated()).isTrue();
        assertThat(results).hasSize(jobs.size()).allSatisfy(r -> assertThat(r.failure()).isNull());
        return results.stream().map(Outcome::value).toList();
    }
    Callable<HttpResponse<String>> creation(UUID id,String key) { return () -> create(id,"USER_A",key); }
    List<Callable<HttpResponse<String>>> same(UUID id,String key,int count) {
        return IntStream.range(0,count).mapToObj(i -> creation(id,key)).toList();
    }
    void snapshot(UUID id,int available,long total,long confirmed) {
        lastSnapshot=new DatabaseAssertions(dataSource).snapshot(List.of(id));
        assertThat(lastSnapshot.get(id)).isEqualTo(new DatabaseAssertions.InventorySnapshot(id,
                lastSnapshot.get(id).capacity(),available,total,confirmed));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM (SELECT user_id,idempotency_key FROM reservations GROUP BY user_id,idempotency_key HAVING count(*)>1) d",Integer.class)).isZero();
    }
    void report() {
        reported=true;
        var distribution=results.stream().collect(java.util.stream.Collectors.groupingBy(r -> r.value()==null ? "exception" :
                r.value().statusCode()+":"+(r.value().statusCode()==409?JSON.readTree(r.value().body()).get("code").asString():"ok"),java.util.stream.Collectors.counting()));
        assertThat(poolMax.get()).isBetween(0,10);
        System.out.println("RUN case="+caseId+" repetition="+repetition+" requests="+results.size()+" results="+results.size()
            +" elapsedMs="+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)+" workers="+Math.min(10,results.size())
            +" pool=10/3000ms activeSampleMax="+poolMax.get()+" sampleMs=10 server="+inFlight.get()
            +" distribution="+distribution+" snapshot="+lastSnapshot);
        System.out.println("PATH case="+caseId+" repetition="+repetition+" events="+events.all());
    }
    void identical(List<HttpResponse<String>> responses,int count) {
        assertThat(responses).hasSize(count).allSatisfy(r -> {
            assertThat(r.statusCode()).isEqualTo(201);
            assertThat(r.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
            assertThat(JSON.readTree(r.body())).isEqualTo(JSON.readTree(responses.getFirst().body()));
            assertThat(r.headers().firstValue("Location")).isEqualTo(responses.getFirst().headers().firstValue("Location"));
        });
        assertThat(responses.stream().filter(r -> r.headers().firstValue("Idempotent-Replayed").orElseThrow().equals("false"))).hasSize(1);
        assertThat(responses.stream().filter(r -> r.headers().firstValue("Idempotent-Replayed").orElseThrow().equals("true"))).hasSize(count-1);
        var body=JSON.readTree(responses.getFirst().body());
        assertThat(body.size()).isEqualTo(4);
        assertThat(body.get("status").asString()).isEqualTo("CONFIRMED");
        var stored=reservations.findByIdAndUser(UUID.fromString(body.get("id").asString()),TestData.USER_A_ID).orElseThrow();
        assertThat(body.get("resourceId").asString()).isEqualTo(stored.resourceId().toString());
        assertThat(responses.getFirst().headers().firstValue("Location").orElseThrow()).endsWith("/api/reservations/"+stored.id());
        assertThat(Instant.parse(body.get("createdAt").asString())).isEqualTo(stored.createdAt());
        assertThat(stored.createdAt().getNano()%1000).isZero();
    }

    @RepeatedTest(10) void C01_capacityTenHundredRequests(RepetitionInfo info) throws Exception {
        begin("C01",info); UUID target=resource(10);
        var jobs=IntStream.range(0,100).mapToObj(i -> creation(target,"C01_"+i)).toList();
        var responses=run(jobs);
        assertThat(responses.stream().filter(r -> r.statusCode()==201)).hasSize(10);
        assertThat(responses.stream().filter(r -> r.statusCode()==409)).hasSize(90);
        responses.forEach(r -> {
            if(r.statusCode()==409) problem(r,409,"SOLD_OUT");
            else { assertThat(r.statusCode()).isEqualTo(201); assertThat(JSON.readTree(r.body()).get("status").asString()).isEqualTo("CONFIRMED"); }
        });
        snapshot(target,0,10,10); report();
    }

    /** 첫 실제 빈 선조회 두 개만 맞춘다. 이 지점에는 활성 트랜잭션/연결/잠금이 없다. */
    void firstTwoEmpty(String key) {
        var barrier=gate(2); var ordinal=new AtomicInteger();
        doAnswer(call -> {
            @SuppressWarnings("unchecked") var found=(Optional<StoredReservation>)call.callRealMethod();
            event("lookup",found.isPresent()?"present":"empty");
            if(found.isEmpty() && !TransactionSynchronizationManager.isActualTransactionActive()
                    && ordinal.incrementAndGet()<=2) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                event("first-empty","no transaction"); barrier.meet();
            }
            return found;
        }).when(reservations).findByUserAndKey(TestData.USER_A_ID,key);
    }
    void uniqueThenOutsideLookup() {
        var unique=events.require("unique");
        assertThat(unique.transaction()).isTrue();
        assertThat(unique.detail()).isEqualTo("23505/uq_reservations_user_key");
        assertThat(events.all()).anySatisfy(e -> {
            assertThat(e.phase()).isEqualTo("lookup"); assertThat(e.request()).isEqualTo(unique.request());
            assertThat(e.sequence()).isGreaterThan(unique.sequence()); assertThat(e.transaction()).isFalse();
            assertThat(e.detail()).isEqualTo("present");
        });
        assertThat(events.all().stream().filter(e->e.phase().equals("first-empty"))).hasSize(2);
    }
    @RepeatedTest(10) void C02_lastOneSameKeyTenRequests(RepetitionInfo info) throws Exception {
        begin("C02",info); var target=resource(1);
        identical(run(same(target,"C02",10)),10);
        snapshot(target,0,1,1); report();
    }
    @RepeatedTest(10) void C03_sufficientStockSameKeyUniqueRollback(RepetitionInfo info) throws Exception {
        begin("C03",info); var target=resource(10); firstTwoEmpty("C03");
        identical(run(same(target,"C03",10)),10); uniqueThenOutsideLookup();
        snapshot(target,9,1,1); report();
    }
    @RepeatedTest(10) void C04_differentResourcesSameKeyRollsBackLoser(RepetitionInfo info) throws Exception {
        begin("C04",info); var a=resource(1); var b=resource(1); firstTwoEmpty("C04");
        var responses=run(List.of(creation(a,"C04"),creation(b,"C04")));
        assertThat(responses.stream().filter(r->r.statusCode()==201)).hasSize(1);
        assertThat(responses.stream().filter(r->r.statusCode()==409)).hasSize(1);
        problem(responses.stream().filter(r->r.statusCode()==409).findFirst().orElseThrow(),409,"IDEMPOTENCY_CONFLICT");
        var winner=UUID.fromString(JSON.readTree(responses.stream().filter(r->r.statusCode()==201).findFirst().orElseThrow().body()).get("resourceId").asString());
        lastSnapshot=new DatabaseAssertions(dataSource).snapshot(List.of(a,b));
        for(var id:List.of(a,b)) assertThat(lastSnapshot.get(id)).isEqualTo(
                new DatabaseAssertions.InventorySnapshot(id,1,id.equals(winner)?0:1,id.equals(winner)?1:0,id.equals(winner)?1:0));
        assertThat(events.all().stream().filter(e -> e.phase().equals("decrement") && e.detail().endsWith(":1"))).hasSize(2);
        uniqueThenOutsideLookup(); report();
    }
    @RepeatedTest(10) void C07_lastOneUpdateZeroRereadsWinnerInsideTransaction(RepetitionInfo info) throws Exception {
        begin("C07",info); var target=resource(1); firstTwoEmpty("C07");
        identical(run(same(target,"C07",2)),2);
        var zero=events.all().stream().filter(e->e.phase().equals("decrement")&&e.detail().endsWith(":0")).findFirst().orElseThrow();
        assertThat(zero.transaction()).isTrue();
        assertThat(events.all()).anySatisfy(e -> {
            assertThat(e.request()).isEqualTo(zero.request()); assertThat(e.sequence()).isGreaterThan(zero.sequence());
            assertThat(e.phase()).isEqualTo("lookup"); assertThat(e.transaction()).isTrue(); assertThat(e.detail()).isEqualTo("present");
        });
        assertThat(events.all().stream().filter(e->e.phase().equals("first-empty"))).hasSize(2);
        snapshot(target,0,1,1); report();
    }
    @RepeatedTest(10) void C08_twoStockUniqueRollsBackProvisionalDecrement(RepetitionInfo info) throws Exception {
        begin("C08",info); var target=resource(2); firstTwoEmpty("C08");
        identical(run(same(target,"C08",2)),2); uniqueThenOutsideLookup();
        assertThat(events.all().stream().filter(e->e.phase().equals("decrement")&&e.detail().endsWith(":1"))).hasSize(2);
        snapshot(target,1,1,1); report();
    }

    @RepeatedTest(10) void C05_tenCancellationsRestoreExactlyOnce(RepetitionInfo info) throws Exception {
        begin("C05",info); var target=resource(1); var id=confirmed(target);
        var before=JSON.readTree(get(id).body());
        assertThat(before.get("cancelledAt").isNull()).isTrue();
        List<Callable<HttpResponse<String>>> jobs=IntStream.range(0,10).mapToObj(i -> (Callable<HttpResponse<String>>)()->cancel(id)).toList();
        var responses=run(jobs); var first=JSON.readTree(responses.getFirst().body());
        for(var response:responses) {
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
            assertThat(JSON.readTree(response.body())).isEqualTo(first);
        }
        assertThat(first.size()).isEqualTo(5);
        assertThat(first.get("id").asString()).isEqualTo(id.toString());
        assertThat(first.get("resourceId").asString()).isEqualTo(target.toString());
        assertThat(first.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(first.get("createdAt")).isEqualTo(before.get("createdAt"));
        assertThat(first.get("cancelledAt").asString()).endsWith("Z");
        var stored=reservations.findByIdAndUser(id,TestData.USER_A_ID).orElseThrow();
        assertThat(Instant.parse(first.get("cancelledAt").asString())).isEqualTo(stored.cancelledAt());
        assertThat(events.all().stream().filter(e -> e.phase().equals("cancel-update")&&e.detail().equals("1"))).hasSize(1);
        assertThat(events.all().stream().filter(e -> e.phase().equals("restore"))).hasSize(1);
        snapshot(target,1,1,0); report();
    }
    void mixedResult(UUID target,UUID old,List<HttpResponse<String>> responses,boolean expectSuccess,String key) throws Exception {
        var creation=responses.getFirst(); var cancellation=responses.get(1);
        assertThat(cancellation.statusCode()).isEqualTo(200);
        var cancelled=JSON.readTree(cancellation.body());
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("id").asString()).isEqualTo(old.toString());
        assertThat(cancelled.get("cancelledAt").isNull()).isFalse();
        assertThat(JSON.readTree(get(old).body())).isEqualTo(cancelled);
        if(expectSuccess) {
            assertThat(creation.statusCode()).isEqualTo(201);
            var created=JSON.readTree(creation.body());
            assertThat(created.get("status").asString()).isEqualTo("CONFIRMED");
            assertThat(created.get("id").asString()).isNotEqualTo(old.toString());
            snapshot(target,0,2,1);
        } else {
            problem(creation,409,"SOLD_OUT"); snapshot(target,1,1,0);
            event("before-retry",String.valueOf(lastSnapshot));
            assertThat(reservations.findByUserAndKey(TestData.USER_A_ID,key)).isEmpty();
            var retry=create(target,"USER_A",key); assertThat(retry.statusCode()).isEqualTo(201);
            assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("false");
            assertThat(JSON.readTree(retry.body()).get("id").asString()).isNotEqualTo(old.toString());
            snapshot(target,0,2,1); event("after-retry",String.valueOf(lastSnapshot));
        }
        assertThat(reservations.findByIdAndUser(old,TestData.USER_A_ID).orElseThrow().status()).isEqualTo(ReservationStatus.CANCELLED);
    }
    @RepeatedTest(10) void C06_createCancelRaceAllowsOnlyTwoStates(RepetitionInfo info) throws Exception {
        begin("C06",info); var target=resource(1); var old=confirmed(target);
        var responses=run(List.of(creation(target,"C06"),()->cancel(old)));
        mixedResult(target,old,responses,responses.getFirst().statusCode()==201,"C06"); report();
    }
    @RepeatedTest(10) void C09_committedCancellationPrecedesCreateDecision(RepetitionInfo info) throws Exception {
        begin("C09",info); var target=resource(1); var old=confirmed(target); var wait=gate(1);
        var first=new AtomicBoolean(true);
        doAnswer(call -> {
            var found=call.callRealMethod(); event("lookup", "empty");
            if(first.compareAndSet(true,false)) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                event("create-wait","no transaction"); wait.awaitRelease(); event("create-released","after cancel commit");
            }
            return found;
        }).when(reservations).findByUserAndKey(TestData.USER_A_ID,"C09");
        var responses=run(List.of(creation(target,"C09"),()-> {
            wait.awaitReady(); var response=cancel(old); assertThat(response.statusCode()).isEqualTo(200);
            snapshot(target,1,1,0); event("cancel-committed",String.valueOf(lastSnapshot)); wait.release(); return response;
        }));
        assertThat(events.require("cancel-committed").sequence()).isGreaterThan(events.require("create-wait").sequence());
        assertThat(events.require("create-released").sequence()).isGreaterThan(events.require("cancel-committed").sequence());
        assertThat(events.all()).anySatisfy(e -> {
            assertThat(e.phase()).isEqualTo("decrement"); assertThat(e.request()).isEqualTo("C09");
            assertThat(e.sequence()).isGreaterThan(events.require("create-released").sequence()); assertThat(e.detail()).endsWith(":1");
        });
        mixedResult(target,old,responses,true,"C09"); report();
    }
    @RepeatedTest(10) void C10_soldOutCompletesBeforeCancellationRestore(RepetitionInfo info) throws Exception {
        begin("C10",info); var target=resource(1); var old=confirmed(target); var wait=gate(1);
        doAnswer(call -> {
            inFlight.incrementAndGet(); requestId.set(old.toString()); event("cancel-enter",old.toString());
            try {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                event("cancel-wait","no transaction"); wait.awaitRelease(); event("cancel-released","after sold out");
                return call.callRealMethod();
            } finally { event("cancel-exit",old.toString()); requestId.remove(); inFlight.decrementAndGet(); }
        }).when(service).cancel(TestData.USER_A_ID,old);
        var responses=run(List.of(()-> {
            wait.awaitReady(); var response=create(target,"USER_A","C10"); problem(response,409,"SOLD_OUT");
            event("sold-out-completed","409/SOLD_OUT"); wait.release(); return response;
        },()->cancel(old)));
        assertThat(events.require("sold-out-completed").sequence()).isGreaterThan(events.require("cancel-wait").sequence());
        assertThat(events.require("cancel-released").sequence()).isGreaterThan(events.require("sold-out-completed").sequence());
        assertThat(events.require("restore").sequence()).isGreaterThan(events.require("cancel-released").sequence());
        mixedResult(target,old,responses,false,"C10"); report();
    }

    @RepeatedTest(10) void C11_insertFaultRollsBackOnlyFailedRequestAndKeyCanRetry(RepetitionInfo info) throws Exception {
        begin("C11",info); var target=resource(2); var fault=new AtomicBoolean(true);
        doAnswer(call -> {
            var key=(IdempotencyKey)call.getArgument(3);
            if(key.value().equals("C11_fail") && fault.compareAndSet(true,false)) {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(events.all()).anySatisfy(e -> {
                    assertThat(e.request()).isEqualTo("C11_fail"); assertThat(e.phase()).isEqualTo("decrement");
                    assertThat(e.transaction()).isTrue(); assertThat(e.detail()).endsWith(":1");
                });
                event("injected-insert-fault","IllegalStateException after real decrement");
                throw new IllegalStateException("C11 injected INSERT fault");
            }
            var stored=call.callRealMethod(); event("insert","stored"); return stored;
        }).when(reservations).insert(any(),any(),any(),any(),any());
        var responses=run(List.of(creation(target,"C11_fail"),creation(target,"C11_ok")));
        assertThat(responses.getFirst().statusCode()).isEqualTo(500);
        ReservationCreateIT.assertSafe(responses.getFirst().body());
        assertThat(responses.getFirst().body()).doesNotContain("C11 injected INSERT fault");
        assertThat(responses.get(1).statusCode()).isEqualTo(201);
        assertThat(reservations.findByUserAndKey(TestData.USER_A_ID,"C11_fail")).isEmpty();
        assertThat(reservations.findByUserAndKey(TestData.USER_A_ID,"C11_ok")).isPresent();
        snapshot(target,1,1,1); event("before-retry",String.valueOf(lastSnapshot));
        events.require("injected-insert-fault");
        var retry=create(target,"USER_A","C11_fail"); assertThat(retry.statusCode()).isEqualTo(201);
        assertThat(retry.headers().firstValue("Idempotent-Replayed")).contains("false");
        snapshot(target,0,2,2); event("after-retry",String.valueOf(lastSnapshot)); report();
    }

    @Test void parentSetupStopsBeforeResetPreparationAndSubmissionAndNullCleanupIsSafe() throws Exception {
        class Fixture extends ReservationApiTestSupport {
            final StopState state=new StopState(); int prepared; int submitted;
            @Override protected void beforeDatabaseReset() { state.check(); }
            void next() { setup(); prepared++; submitted++; }
        }
        var fixture=new Fixture(); fixture.dataSource=mock(javax.sql.DataSource.class);
        var first=new IllegalStateException("termination injected"); fixture.state.stop(first);
        for(int round=0;round<2;round++) assertThatThrownBy(fixture::next).hasCause(first);
        verifyNoInteractions(fixture.dataSource);
        assertThat(fixture.prepared).isZero(); assertThat(fixture.submitted).isZero();
        assertThat(fixture.client).isNull(); fixture.cleanup();
    }
}
