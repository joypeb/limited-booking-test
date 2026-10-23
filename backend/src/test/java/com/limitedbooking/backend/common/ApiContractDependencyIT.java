package com.limitedbooking.backend.common;

import com.limitedbooking.backend.reservation.*;
import com.limitedbooking.backend.support.*;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import static org.assertj.core.api.Assertions.*;

/** 각 하위 fixture는 자체 컨테이너와 컨텍스트를 소유하며 중단 뒤 재사용하지 않는다. */
abstract class ContractDependencyFixture {
    static final UUID RESOURCE=UUID.fromString("31000000-0000-0000-0000-000000000001");
    static final UUID RESERVATION=UUID.fromString("32000000-0000-0000-0000-000000000001");
    final ConcurrencyTestSupport.StopState stop=new ConcurrencyTestSupport.StopState();
    @LocalServerPort int port;
    @Autowired DataSource database;
    @Autowired StringRedisTemplate redis;
    @Autowired ApiContractHttpSupport.ServerObservation observation;
    @Autowired com.limitedbooking.backend.ratelimit.RateLimitProperties policy;
    @Autowired org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisFactory;

    static void properties(ContainerTestSupport containers,DynamicPropertyRegistry p) {
        containers.registerLocalAuthAndDatabaseProperties(p);
        p.add("spring.datasource.hikari.maximum-pool-size",()->10);
        p.add("spring.datasource.hikari.connection-timeout",()->3000);
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class Observations {
        @Bean ApiContractHttpSupport.ServerObservation observation(){return new ApiContractHttpSupport.ServerObservation();}
        @Bean FilterRegistrationBean<Filter> dependencyObservation(ApiContractHttpSupport.ServerObservation observation) {
            Filter filter=(request,response,chain)->{
                String id=((HttpServletRequest)request).getHeader("X-Test-Request-Id");
                if(id==null){chain.doFilter(request,response);return;}
                observation.entered();
                try{chain.doFilter(request,response);}finally{observation.completed(id);}
            };
            var bean=new FilterRegistrationBean<>(filter);bean.setOrder(Ordered.HIGHEST_PRECEDENCE);return bean;
        }
    }
    record Operation(String id,String method,String path,String body,boolean admin) { }
    static List<Operation> operations() {
        return List.of(
                new Operation("resource-create","POST","/api/resources","{\"name\":\"장애 시험\",\"capacity\":1}",true),
                new Operation("resource-get","GET","/api/resources/"+RESOURCE,null,false),
                new Operation("reservation-create","POST","/api/reservations","{\"resourceId\":\""+RESOURCE+"\"}",false),
                new Operation("reservation-get","GET","/api/reservations/"+RESERVATION,null,false),
                new Operation("reservation-cancel","POST","/api/reservations/"+RESERVATION+"/cancel",null,false));
    }
    void prepare(ApiContractHttpSupport http) {
        RateLimitTestSupport.assertSettings(policy,redisFactory,20);
        http.reset(()->{new RateLimitTestSupport(redis).reset();new TestData(database).reset();});
        http.prepare(()->{
            var data=new TestData(database);
            data.resource(RESOURCE,"사전 커밋",2,1,Instant.EPOCH);
            data.reservation(new StoredReservation(RESERVATION,RESOURCE,TestData.USER_A_ID,"prepared-key",
                    ReservationStatus.CONFIRMED,Instant.EPOCH,null));
        });
        assertThat(new DatabaseAssertions(database).snapshot(List.of(RESOURCE)).get(RESOURCE).confirmedCount()).isEqualTo(1);
        var pool=(HikariDataSource)database;
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
    }
    ApiContractValidator.HttpExchange send(ApiContractHttpSupport http,Operation operation,int status) throws Exception {
        var headers=new LinkedHashMap<String,String>();
        headers.put("Authorization",HttpClientTestSupport.basicAuth(operation.admin()?"ADMIN":"USER_A",
                operation.admin()?TestData.ADMIN_PASSWORD:TestData.USER_A_PASSWORD));
        if(operation.body()!=null)headers.put("Content-Type","application/json");
        if(operation.id().equals("reservation-create"))headers.put("Idempotency-Key","dependency-key");
        var reply=http.send(operation.method(),operation.path(),headers,operation.body());
        assertThat(reply.status()).as(operation.id()).isEqualTo(status);
        reply.assertValid(ApiContractValidator.load("business.openapi.yaml"));
        if(status==503)assertThat(tools.jackson.databind.json.JsonMapper.builder().build().readTree(reply.responseBody())
                .get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(stop.stopped()).isFalse();
        System.out.println("contract-dependency id="+operation.id()+" method="+operation.method()+" path="+reply.path()
                +" status="+status+" requestViolations=0 responseValid=true");
        return reply;
    }
    /** 업무 두 테이블의 모든 컬럼·모든 행을 한 SQL 시점에 비교한다. */
    String snapshot() {
        return new JdbcTemplate(database).queryForObject("""
                SELECT jsonb_build_object(
                    'resources', (SELECT coalesce(jsonb_agg(to_jsonb(r) ORDER BY r.id), '[]'::jsonb) FROM resources r),
                    'reservations', (SELECT coalesce(jsonb_agg(to_jsonb(v) ORDER BY v.id), '[]'::jsonb) FROM reservations v)
                )::text
                """,String.class);
    }
    void stopOwned(Runnable action) throws Exception {
        stop.check();
        var executor=Executors.newSingleThreadExecutor(task->{
            var thread=new Thread(task,"contract-owned-stop");thread.setDaemon(true);return thread;
        });
        Throwable failure=null;
        try{executor.submit(action).get(10,TimeUnit.SECONDS);}
        catch(Exception | Error error){
            if(error instanceof InterruptedException)Thread.currentThread().interrupt();
            failure=error;
        } finally {
            executor.shutdownNow();
            try{if(!executor.awaitTermination(10,TimeUnit.SECONDS))
                throw new IllegalStateException("소유 의존성 중단 작업 미종료");}
            catch(Exception | Error cleanup){
                if(cleanup instanceof InterruptedException)Thread.currentThread().interrupt();
                failure=ConcurrencyTestSupport.preserve(failure,cleanup);
            }
        }
        if(failure!=null){
            stop.stop(failure);
            if(failure instanceof Exception error)throw error;
            throw (Error)failure;
        }
    }
    void finished(String fixture) {
        assertThat(observation.idle(Set.of())).isTrue();
        assertThat(stop.stopped()).isFalse();
        System.out.println("contract-dependency fixture="+fixture+" serverIdle=true httpClosed=true stop=false");
    }
}

@Tag("contract") @DirtiesContext
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContractDependencyFixture.Observations.class)
@TestExecutionListeners(listeners=ApiContractDependencyIT.Cleanup.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class ApiContractDependencyIT extends ContractDependencyFixture {
    static final ContainerTestSupport CONTAINERS=new ContainerTestSupport();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){properties(CONTAINERS,p);}
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder(){return 2999;}
        @Override public void afterTestClass(TestContext context){CONTAINERS.close();}
    }
    @Test void stoppedOwnedDatabaseRejectsAllFiveOperationsWithHealthyRedis() throws Exception {
        try(var http=new ApiContractHttpSupport(port,observation,stop)) {
            prepare(http);
            assertThat(redis.getConnectionFactory()).isNotNull();
            try(var connection=redisFactory.getConnection()){assertThat(connection.ping()).isEqualTo("PONG");}
            stopOwned(CONTAINERS.postgres::stop);
            assertThat(CONTAINERS.postgres.isRunning()).as("실제 소유 DB 중단").isFalse();
            ((HikariDataSource)database).getHikariPoolMXBean().softEvictConnections();
            assertThat(((HikariDataSource)database).getHikariPoolMXBean().getTotalConnections()).isZero();
            for(var operation:operations()) {
                send(http,operation,503);
                assertThat(new RateLimitTestSupport(redis).count(TestData.USER_A_ID))
                        .isEqualTo(operation.id().equals("reservation-create")||operation.id().equals("reservation-get")?1:
                                operation.id().equals("reservation-cancel")?2:0);
            }
            try(var connection=redisFactory.getConnection()){assertThat(connection.ping()).isEqualTo("PONG");}
        }
        // 중단 DB에는 snapshot을 조회하지 않는다. 부분반영 롤백 증거는 기존 실제 timeout 회귀와 분리한다.
        finished("DB-stopped");
    }
}

@Tag("contract") @DirtiesContext
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContractDependencyFixture.Observations.class)
@TestExecutionListeners(listeners=RedisApiContractDependencyIT.Cleanup.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class RedisApiContractDependencyIT extends ContractDependencyFixture {
    static final ContainerTestSupport CONTAINERS=new ContainerTestSupport();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){properties(CONTAINERS,p);}
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder(){return 2999;}
        @Override public void afterTestClass(TestContext context){CONTAINERS.close();}
    }
    @Test void stoppedOwnedRedisRejectsWritesWithoutChangingDatabaseAndLeavesIndependentApisAvailable() throws Exception {
        try(var http=new ApiContractHttpSupport(port,observation,stop)) {
            prepare(http);
            try(var connection=redisFactory.getConnection()){assertThat(connection.ping()).isEqualTo("PONG");}
            String before=snapshot();
            stopOwned(CONTAINERS.redis::stop);
            assertThat(CONTAINERS.redis.isRunning()).as("실제 소유 Redis 중단").isFalse();
            for(var operation:operations())if(operation.id().equals("reservation-create")||operation.id().equals("reservation-cancel")) {
                send(http,operation,503);
                http.prepare(()->assertThat(snapshot()).as("Redis503 후 업무 전체 행 동일").isEqualTo(before));
            }
            for(var operation:operations())if(operation.id().endsWith("get"))send(http,operation,200);
            send(http,operations().getFirst(),201);
        }
        new DatabaseAssertions(database).assertInventoryConserved(RESOURCE);
        assertThat(new TestData(database).resourceCount()).isEqualTo(2);
        finished("Redis-stopped");
    }
}
