package com.limitedbooking.backend.ratelimit;

import com.limitedbooking.backend.reservation.ReservationRepository;
import com.limitedbooking.backend.support.*;
import io.lettuce.core.*;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 관리 연결은 앱의200ms 연결과 독립적이며 이 시험 소유 Redis만 조작한다. */
final class RedisManagement implements AutoCloseable {
    final RedisClient client;
    final StatefulRedisConnection<String,String> connection;
    RedisManagement(String host,int port){
        client=RedisClient.create(RedisURI.Builder.redis(host,port).withTimeout(Duration.ofSeconds(3)).build());
        client.setOptions(ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(3)).build()).build());
        try{connection=client.connect();connection.setTimeout(Duration.ofSeconds(3));}
        catch(RuntimeException failure){
            try{client.shutdownAsync(0,10,java.util.concurrent.TimeUnit.SECONDS).get(10,java.util.concurrent.TimeUnit.SECONDS);}
            catch(Exception cleanup){if(cleanup instanceof InterruptedException)Thread.currentThread().interrupt();failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    String client(String... arguments){
        var args=new CommandArgs<>(StringCodec.UTF8);for(String value:arguments)args.add(value);
        return connection.sync().dispatch(CommandType.CLIENT,new StatusOutput<>(StringCodec.UTF8),args);
    }
    void pause(){assertThat(client("PAUSE","1000","WRITE")).isEqualTo("OK");}
    void recover(){
        assertThat(client("UNPAUSE")).isEqualTo("OK");
        long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();RuntimeException last=null;
        while(System.nanoTime()<deadline){
            connection.setTimeout(Duration.ofNanos(Math.min(Duration.ofSeconds(3).toNanos(),Math.max(1,deadline-System.nanoTime()))));
            try{assertThat(connection.sync().ping()).isEqualTo("PONG");return;}
            catch(RedisException failure){last=failure;}
        }
        throw new IllegalStateException("Redis 복구 PING5초 초과",last);
    }
    @Override public void close(){
        Throwable failure=null;
        try{connection.closeAsync().get(10,java.util.concurrent.TimeUnit.SECONDS);}
        catch(Throwable error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();failure=error;}
        try{client.shutdownAsync(0,10,java.util.concurrent.TimeUnit.SECONDS).get(10,java.util.concurrent.TimeUnit.SECONDS);}
        catch(Throwable error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();failure=ConcurrencyTestSupport.preserve(failure,error);}
        RedisFailureHttpTestSupport.rethrow(failure);
    }
}

abstract class RedisFailureHttpTestSupport extends RateLimitHttpTestSupport {
    static void rethrow(Throwable failure){
        if(failure instanceof RuntimeException error)throw error;
        if(failure instanceof Error error)throw error;
        if(failure!=null)throw new IllegalStateException(failure);
    }
    List<Throwable> causes(Throwable failure){
        var result=new ArrayList<Throwable>();var seen=Collections.newSetFromMap(new IdentityHashMap<Throwable,Boolean>());
        for(var cause=failure;cause!=null && seen.add(cause);cause=cause.getCause())result.add(cause);
        return result;
    }
    void dependencyRejected(Reply response){
        problem(response,503,"DEPENDENCY_UNAVAILABLE");var trace=response.trace();
        assertThat(trace.count("limiter-start")).isEqualTo(1);assertThat(trace.count("script-attempt")).isEqualTo(1);
        assertThat(trace.count("redis-failure")).isEqualTo(1);assertThat(trace.redisFailure).isNotNull();
        for(String phase:List.of("controller","service","tx-before","tx-start","sql","allowed","denied"))assertThat(trace.count(phase)).isZero();
        assertThat(trace.events).allSatisfy(event->assertThat(event.transaction()).isFalse());
        assertThat(STOP.stopped()).as("정상503은HTTP전송불확실/STOP가 아니다").isFalse();
        System.out.println("US3 rejected status=503 limiterMs="+Duration.ofNanos(trace.limiterNanos).toMillis()
                +" causes="+causes(trace.redisFailure).stream().map(cause->cause.getClass().getName()).toList()+" downstream=0 tx=0");
    }
    Reply operation(String kind,UUID resource,UUID owned) throws Exception {
        return switch(kind){case "create"->create(resource,"new-key");case "cancel"->cancel(owned);default->create(resource,"successful-key");};
    }
    void independent(UUID resource,UUID owned) throws Exception {
        var getResource=send("GET","/api/resources/"+resource,auth("USER_A"),null,null,null,false,Map.of());
        var getReservation=send("GET","/api/reservations/"+owned,auth("USER_A"),null,null,null,false,Map.of());
        var postResource=send("POST","/api/resources",auth("ADMIN"),"{\"name\":\"장애 중 독립 대상\",\"capacity\":0}",null,"application/json",false,Map.of());
        assertThat(getResource.status()).isEqualTo(200);assertThat(getReservation.status()).isEqualTo(200);assertThat(postResource.status()).isEqualTo(201);
        for(var response:List.of(getResource,getReservation,postResource))notCounted(response);
        System.out.println("US3 independent resourceGET=200 reservationGET=200 resourcePOST=201 redis-attempts=0");
    }
    void stoppedWritesAndIndependentApis() throws Exception {
        UUID resource=resource(3),owned=seed(resource,TestData.USER_A_ID,"successful-key");String before=snapshot();
        for(String kind:List.of("create","cancel","replay")){
            var response=operation(kind,resource,owned);dependencyRejected(response);assertThat(snapshot()).isEqualTo(before);
        }
        independent(resource,owned);quiescent();closeHttp();
        new DatabaseAssertions(dataSource).assertInventoryConserved(resource);
    }
}

@DirtiesContext
@TestExecutionListeners(listeners=RedisFailureIT.Cleanup.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RateLimitHttpTestSupport.ObservationConfiguration.class)
class RedisFailureIT extends RedisFailureHttpTestSupport {
    static final ContainerTestSupport containers=new ContainerTestSupport();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){properties(containers,p);}
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder(){return 2999;}
        @Override public void afterTestClass(TestContext context){containers.close();}
    }
    RedisManagement management(){return new RedisManagement(containers.redis.getHost(),containers.redis.getMappedPort(6379));}
    interface PausedAction{void run() throws Exception;}
    void withPause(PausedAction action){
        RedisManagement admin=management();Throwable failure=null;
        try{
            // 인증을 마친 각 쓰기의 실제 limiter 진입에서 OK를 확인해 인증 지연과 명령 지연을 분리한다.
            beforeLimiter=admin::pause;action.run();
        }catch(Throwable error){failure=error;}
        finally{
            beforeLimiter=()->{};
            try(var recovery=management()){recovery.recover();}
            catch(Throwable error){stopState().stop(error);failure=ConcurrencyTestSupport.preserve(failure,error);}
            try{admin.close();}catch(Throwable error){stopState().stop(error);failure=ConcurrencyTestSupport.preserve(failure,error);}
        }
        rethrow(failure);
    }
    @ParameterizedTest @ValueSource(strings={"create","cancel","replay"})
    void actualWritePauseTimesOutEachOperationWithoutChangingBusinessState(String kind) throws Exception {
        UUID resource=resource(3),owned=seed(resource,TestData.USER_A_ID,"successful-key");String before=snapshot();
        withPause(()->{
            var response=operation(kind,resource,owned);dependencyRejected(response);
            assertThat(causes(response.trace().redisFailure)).anySatisfy(cause->assertThat(cause).isInstanceOf(RedisCommandTimeoutException.class));
        });
        quiescent();assertThat(snapshot()).isEqualTo(before);
        long countAfterRecovery=keys.count(TestData.USER_A_ID);
        var recovered=create(resource,"after-pause");assertThat(recovered.status()).isEqualTo(201);counted(recovered,true);
        quiescent();closeHttp();new DatabaseAssertions(dataSource).assertInventoryConserved(resource);
        // timeout 응답 이후 queued Lua의 실행 여부는 불확실하다.0을 기대하거나 보상하지 않는다.
        System.out.println("US3 pause operation="+kind+" recovered=PONG countAfterRecovery="+countAfterRecovery+" businessUnchangedBeforeRecoveryWrite=true recoveryHTTP=201");
    }
    @Test void independentApisRemainAvailableDuringWritePause() throws Exception {
        UUID resource=resource(2),owned=seed(resource,TestData.USER_A_ID,"successful-key");
        withPause(()->{
            try(var admin=management()){
                admin.pause();independent(resource,owned);
            }
        });
        quiescent();closeHttp();assertThat(keys.count(TestData.USER_A_ID)).isZero();
    }
    @Test void actualWrongTypeRejectsAllWritesWithoutChangingBusinessState() throws Exception {
        UUID resource=resource(3),owned=seed(resource,TestData.USER_A_ID,"successful-key");String before=snapshot();
        redis.opsForList().rightPush(RateLimitTestSupport.key(TestData.USER_A_ID),"wrong-type");
        Throwable failure=null;
        try{
            for(String kind:List.of("create","cancel","replay")){
                var response=operation(kind,resource,owned);dependencyRejected(response);
                assertThat(causes(response.trace().redisFailure)).anySatisfy(cause->{
                    assertThat(cause).isInstanceOf(RedisCommandExecutionException.class);
                    assertThat(cause.getMessage()).startsWith("WRONGTYPE ");
                });
                assertThat(snapshot()).isEqualTo(before);
            }
        }catch(Throwable error){failure=error;}
        finally{
            try{quiescent();}catch(Throwable error){stopState().stop(error);failure=ConcurrencyTestSupport.preserve(failure,error);}
            if(!stopState().stopped())try{redis.delete(RateLimitTestSupport.key(TestData.USER_A_ID));}
            catch(Throwable error){stopState().stop(error);failure=ConcurrencyTestSupport.preserve(failure,error);}
        }
        rethrow(failure);
        assertThat(create(resource,"recovered").status()).isEqualTo(201);
    }
    @ParameterizedTest @ValueSource(strings={"create","cancel","replay"})
    void actualNoEvictionOomRejectsWritesAndRestoresOriginalConfiguration(String kind) throws Exception {
        UUID resource=resource(3),owned=seed(resource,TestData.USER_A_ID,"successful-key");String before=snapshot();
        RedisManagement admin=management();String originalMemory=null,originalPolicy=null;Throwable failure=null;
        try{
            originalMemory=admin.connection.sync().configGet("maxmemory").get("maxmemory");
            originalPolicy=admin.connection.sync().configGet("maxmemory-policy").get("maxmemory-policy");
            assertThat(originalMemory).isNotBlank();assertThat(originalPolicy).isNotBlank();
            assertThat(admin.connection.sync().configSet("maxmemory-policy","noeviction")).isEqualTo("OK");
            assertThat(admin.connection.sync().configSet("maxmemory","1")).isEqualTo("OK");
            assertThat(admin.connection.sync().configGet("maxmemory").get("maxmemory")).isEqualTo("1");
            var response=operation(kind,resource,owned);
            System.out.println("US3 OOM operation="+kind+" status="+response.status()+" causes="
                    +causes(response.trace().redisFailure).stream().map(cause->cause.getClass().getName()+":"+cause.getMessage()).toList());
            assertThat(snapshot()).isEqualTo(before);
            dependencyRejected(response);
            assertThat(causes(response.trace().redisFailure)).anySatisfy(cause->{
                assertThat(cause).isInstanceOf(RedisCommandExecutionException.class);
                assertThat(cause.getMessage()).startsWith("OOM command not allowed when used memory > 'maxmemory'.");
            });
            assertThat(response.body()).doesNotContain("OOM","maxmemory");
            independent(resource,owned);
        }catch(Throwable error){failure=error;}
        finally{
            if(originalMemory!=null)try{assertThat(admin.connection.sync().configSet("maxmemory",originalMemory)).isEqualTo("OK");}
            catch(Throwable error){stopState().stop(error);failure=ConcurrencyTestSupport.preserve(failure,error);}
            if(originalPolicy!=null)try{assertThat(admin.connection.sync().configSet("maxmemory-policy",originalPolicy)).isEqualTo("OK");}
            catch(Throwable error){stopState().stop(error);failure=ConcurrencyTestSupport.preserve(failure,error);}
            try{
                assertThat(admin.connection.sync().configGet("maxmemory").get("maxmemory")).isEqualTo(originalMemory);
                assertThat(admin.connection.sync().configGet("maxmemory-policy").get("maxmemory-policy")).isEqualTo(originalPolicy);
                System.out.println("US3 OOM restored operation="+kind+" maxmemory="+originalMemory+" policy="+originalPolicy);
            }catch(Throwable error){stopState().stop(error);failure=ConcurrencyTestSupport.preserve(failure,error);}
            try{admin.close();}catch(Throwable error){stopState().stop(error);failure=ConcurrencyTestSupport.preserve(failure,error);}
        }
        rethrow(failure);
        var recovered=create(resource,"after-oom");assertThat(recovered.status()).isEqualTo(201);counted(recovered,true);
        quiescent();closeHttp();new DatabaseAssertions(dataSource).assertInventoryConserved(resource);
        System.out.println("US3 OOM recovery operation="+kind+" HTTP=201 unchangedBeforeRecoveryWrite=true");
    }
    @MockitoSpyBean ReservationRepository reservations;
    @Test void actualStatementTimeoutRollsBackDecrementButKeepsUsageAndAllowsSameKeyRecovery() throws Exception {
        UUID resource=resource(2);seed(resource,TestData.USER_A_ID,"successful-key");String before=snapshot();
        var failOnce=new AtomicBoolean(true);var observedAvailable=new AtomicInteger(-1);
        doAnswer(call->{
            if(failOnce.getAndSet(false)){
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                observedAvailable.set(jdbc.queryForObject("SELECT available FROM resources WHERE id=?",Integer.class,resource));
                event("failure-after-decrement");jdbc.execute("SELECT pg_sleep(6)");
                fail("DB statement timeout5s가 발생해야 한다");
            }
            return call.callRealMethod();
        }).when(reservations).insert(any(),any(),any(),any(),any());
        var failed=create(resource,"recover-db");problem(failed,503,"DEPENDENCY_UNAVAILABLE");counted(failed,true);
        assertThat(observedAvailable.get()).isZero();assertThat(failed.trace().count("tx-rollback")).isEqualTo(1);
        assertThat(failed.trace().count("failure-after-decrement")).isEqualTo(1);
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(1);assertThat(snapshot()).isEqualTo(before);
        var recovered=create(resource,"recover-db");assertThat(recovered.status()).isEqualTo(201);counted(recovered,true);
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reservations WHERE user_id=? AND idempotency_key=?",Integer.class,TestData.USER_A_ID,"recover-db")).isEqualTo(1);
        quiescent();closeHttp();new DatabaseAssertions(dataSource).assertInventoryConserved(resource);
        System.out.println("US3 DB actualSQL=pg_sleep6 statementTimeout=5s afterDecrement=0 rollback=1 redisCount=1 retry201 count=2");
    }
    @Test void defaultRedisWindowExpiryDoesNotDeleteSuccessfulCreationKey() throws Exception {
        UUID resource=resource(1);var first=create(resource,"durable-key");assertThat(first.status()).isEqualTo(201);counted(first,true);
        String before=snapshot();long start=System.nanoTime(),deadline=start+Duration.ofSeconds(65).toNanos();
        while(Boolean.TRUE.equals(redis.hasKey(RateLimitTestSupport.key(TestData.USER_A_ID))) && System.nanoTime()<deadline)Thread.sleep(200);
        assertThat(redis.hasKey(RateLimitTestSupport.key(TestData.USER_A_ID))).isFalse();assertThat(snapshot()).isEqualTo(before);
        var replay=create(resource,"durable-key");assertThat(replay.status()).isEqualTo(201);counted(replay,true);
        assertThat(replay.header("Idempotent-Replayed")).isEqualTo("true");assertThat(replay.header("Location")).isEqualTo(first.header("Location"));
        assertThat(JSON.readTree(replay.body())).isEqualTo(JSON.readTree(first.body()));
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(1);quiescent();closeHttp();assertThat(snapshot()).isEqualTo(before);
        System.out.println("US3 durableKey defaultWindow60000 expiredAfterMs="+Duration.ofNanos(System.nanoTime()-start).toMillis()+" replay201=true newWindowCount=1");
    }
}

@DirtiesContext
@TestExecutionListeners(listeners=ExistingConnectionRedisFailureIT.Cleanup.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RateLimitHttpTestSupport.ObservationConfiguration.class)
class ExistingConnectionRedisFailureIT extends RedisFailureHttpTestSupport {
    static final ContainerTestSupport containers=new ContainerTestSupport();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){properties(containers,p);}
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder(){return 2999;}
        @Override public void afterTestClass(TestContext context){containers.close();}
    }
    @Test void stopsAnAlreadyConnectedOwnedRedis() throws Exception {
        try(var connection=redisFactory.getConnection()){assertThat(connection.ping()).isEqualTo("PONG");}
        containers.redis.stop();stoppedWritesAndIndependentApis();
    }
}

@DirtiesContext
@TestExecutionListeners(listeners=FirstConnectionRedisFailureIT.Cleanup.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RateLimitHttpTestSupport.ObservationConfiguration.class)
class FirstConnectionRedisFailureIT extends RedisFailureHttpTestSupport {
    static final ContainerTestSupport containers=new ContainerTestSupport();
    static final String HOST=containers.redis.getHost();
    static final int PORT=containers.redis.getMappedPort(6379);
    static {containers.redis.stop();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){
        properties(containers,p);p.add("spring.data.redis.host",()->HOST);p.add("spring.data.redis.port",()->PORT);
    }
    @Override boolean resetRedisBeforeTest(){return false;}
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder(){return 2999;}
        @Override public void afterTestClass(TestContext context){containers.close();}
    }
    @Test void rejectsTheFirstConnectionToAnAlreadyStoppedOwnedRedis() throws Exception {
        verify(redis,never()).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
        stoppedWritesAndIndependentApis();
    }
}
