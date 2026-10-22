package com.limitedbooking.backend.ratelimit;

import com.limitedbooking.backend.reservation.*;
import com.limitedbooking.backend.resource.ResourceRepository;
import com.limitedbooking.backend.support.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 실제 HTTP 서버의 요청별 사건과 업무 전체행을 관측한다. 제품 장애후크는 없다. */
abstract class RateLimitHttpTestSupport {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static final Map<String,String> PASSWORDS=Map.of("ADMIN",UUID.randomUUID().toString(),
            "USER_A",UUID.randomUUID().toString(),"USER_B",UUID.randomUUID().toString());
    static final ConcurrencyTestSupport.StopState STOP=new ConcurrencyTestSupport.StopState();
    static final ThreadLocal<Trace> CURRENT=new ThreadLocal<>();
    static final ConcurrentMap<String,Trace> TRACES=new ConcurrentHashMap<>();
    static final AtomicInteger IN_FLIGHT=new AtomicInteger();
    static final AtomicLong SEQUENCE=new AtomicLong();
    record Event(String phase, boolean transaction) {}
    static final class Trace {
        final List<Event> events=new CopyOnWriteArrayList<>();
        volatile boolean complete;
        volatile long scriptCount;
        volatile long scriptTtl;
        volatile long contentLength;
        volatile String transferEncoding;
        long count(String phase) { return events.stream().filter(e->e.phase().equals(phase)).count(); }
    }
    record Reply(int status, Map<String,List<String>> headers, String body, Trace trace) {
        String header(String name) {
            return headers.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name))
                    .flatMap(e->e.getValue().stream()).findFirst().orElseThrow();
        }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class ObservationConfiguration {
        @Bean FilterRegistrationBean<OncePerRequestFilter> requestObservation() {
            var filter=new OncePerRequestFilter() {
                @Override protected boolean shouldNotFilterErrorDispatch(){return false;}
                @Override protected void doFilterNestedErrorDispatch(HttpServletRequest request,HttpServletResponse response,
                        FilterChain chain) throws ServletException,IOException {doFilterInternal(request,response,chain);}
                @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,
                        FilterChain chain) throws ServletException,IOException {
                    String id=request.getHeader("X-Test-Request-Id");
                    if(id==null)id="server-"+SEQUENCE.incrementAndGet();
                    Trace trace;
                    if(request.getDispatcherType()==DispatcherType.ERROR){
                        trace=TRACES.get(id);
                        if(trace==null)throw new IllegalStateException("원 요청 없는 ERROR dispatch");
                        trace.events.add(new Event("error-dispatch",TransactionSynchronizationManager.isActualTransactionActive()));
                    }else{
                        trace=new Trace();
                        trace.contentLength=request.getContentLengthLong();trace.transferEncoding=request.getHeader("Transfer-Encoding");
                        if(TRACES.putIfAbsent(id,trace)!=null)throw new IllegalStateException("중복 시험 요청 ID");
                    }
                    trace.complete=false;CURRENT.set(trace);IN_FLIGHT.incrementAndGet();
                    try { chain.doFilter(request,response); }
                    finally { CURRENT.remove();IN_FLIGHT.decrementAndGet();trace.complete=true; }
                }
            };
            var registration=new FilterRegistrationBean<OncePerRequestFilter>(filter);
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            registration.setDispatcherTypes(DispatcherType.REQUEST,DispatcherType.ERROR);
            return registration;
        }
    }
    static void properties(ContainerTestSupport containers,DynamicPropertyRegistry p) {
        containers.registerLocalAuthAndDatabaseProperties(p);
        PASSWORDS.forEach((user,password)->p.add(switch(user){
            case "ADMIN"->"ADMIN_PASSWORD";case "USER_A"->"USER_A_PASSWORD";default->"USER_B_PASSWORD";
        },()->password));
        p.add("spring.datasource.hikari.maximum-pool-size",()->10);
        p.add("spring.datasource.hikari.connection-timeout",()->3000);
    }
    @LocalServerPort int port;
    @Autowired DataSource dataSource;
    @MockitoSpyBean org.springframework.data.redis.core.StringRedisTemplate redis;
    @Autowired ConfigurableTransactionManager transactionManager;
    @Autowired org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisFactory;
    @Autowired RateLimitProperties policy;
    @MockitoSpyBean RedisRateLimiter limiter;
    @MockitoSpyBean ReservationController controller;
    @MockitoSpyBean ReservationService service;
    @MockitoSpyBean ResourceRepository resources;
    JdbcTemplate jdbc;
    TestData data;
    RateLimitTestSupport keys;
    HttpClient client;
    final AtomicBoolean failService=new AtomicBoolean();
    static void event(String phase) {
        var trace=CURRENT.get();
        if(trace!=null)trace.events.add(new Event(phase,TransactionSynchronizationManager.isActualTransactionActive()));
    }
    @BeforeEach void setupHttp() {
        STOP.check();
        assertThat(IN_FLIGHT.get()).isZero();
        TRACES.clear();
        keys=new RateLimitTestSupport(redis);keys.reset();
        data=new TestData(dataSource);data.reset();jdbc=new JdbcTemplate(dataSource);
        client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var pool=(com.zaxxer.hikari.HikariDataSource)dataSource;
        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
        assertThat(policy).isEqualTo(new RateLimitProperties(20,Duration.ofSeconds(60)));
        assertThat(redisFactory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofMillis(200));
        assertThat(redisFactory.getClientConfiguration().getClientOptions().orElseThrow().getSocketOptions()
                .getConnectTimeout()).isEqualTo(Duration.ofMillis(200));
        doAnswer(call->{var result=(List<?>)call.callRealMethod();var trace=CURRENT.get();
            if(trace!=null){event("script");trace.scriptCount=(Long)result.get(0);trace.scriptTtl=(Long)result.get(1);}
            return result;}).when(redis).execute(any(org.springframework.data.redis.core.script.RedisScript.class),anyList(),any(Object[].class));
        doAnswer(call->{event("limiter-start");var result=(RateLimitDecision)call.callRealMethod();
            event(result.allowed()?"allowed":"denied");return result;}).when(limiter).check(any());
        doAnswer(call->{event("controller");return call.callRealMethod();}).when(controller).create(any(),anyString(),any());
        doAnswer(call->{event("controller");return call.callRealMethod();}).when(controller).cancel(any(),any(),any());
        doAnswer(call->{event("service");if(failService.get())throw new IllegalStateException("시험 후속 내부 결함");
            return call.callRealMethod();}).when(service).create(any(),any(),any());
        doAnswer(call->{event("service");return call.callRealMethod();}).when(service).cancel(any(),any());
        transactionManager.setTransactionExecutionListeners(List.of(new TransactionExecutionListener() {
            @Override public void beforeBegin(TransactionExecution transaction) { event("tx-before"); }
            @Override public void afterBegin(TransactionExecution transaction,Throwable failure) { event("tx-start"); }
        }));
        doAnswer(call->{event("sql");
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(jdbc.queryForObject("SHOW transaction_isolation",String.class)).isEqualTo("read committed");
            assertThat(jdbc.queryForObject("SHOW lock_timeout",String.class)).isEqualTo("2s");
            assertThat(jdbc.queryForObject("SHOW statement_timeout",String.class)).isEqualTo("5s");
            assertThat(jdbc.queryForObject("SHOW timezone",String.class)).endsWith("UTC");
            return call.callRealMethod();}).when(resources).decrementIfAvailable(any());
    }
    void quiescent() throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        while(IN_FLIGHT.get()!=0 && System.nanoTime()<deadline)Thread.sleep(10);
        if(IN_FLIGHT.get()!=0)throw new IllegalStateException("HTTP 서버 종료10초 초과");
    }
    void closeHttp() throws Exception { if(client!=null){HttpClientTestSupport.close(client);client=null;} }
    @AfterEach void cleanupHttp() throws Exception {
        Throwable failure=null;
        try{quiescent();}catch(Throwable error){failure=error;STOP.stop(error);}
        try{closeHttp();}catch(Throwable error){failure=ConcurrencyTestSupport.preserve(failure,error);STOP.stop(error);}
        if(failure==null && !STOP.stopped())transactionManager.setTransactionExecutionListeners(List.of());
        if(failure instanceof Exception error)throw error;
        if(failure instanceof Error error)throw error;
    }
    UUID resource(int capacity) {
        var id=UUID.randomUUID();data.resource(id,"제한 시험",capacity,capacity,Instant.EPOCH);return id;
    }
    UUID seed(UUID resource,UUID owner,String key) {
        var id=UUID.randomUUID();
        jdbc.update("UPDATE resources SET available=available-1 WHERE id=?",resource);
        data.reservation(new StoredReservation(id,resource,owner,key,ReservationStatus.CONFIRMED,Instant.EPOCH,null));
        return id;
    }
    String payload(UUID resource) { return "{\"resourceId\":\""+resource+"\"}"; }
    static String auth(String user) { return HttpClientTestSupport.basicAuth(user,PASSWORDS.get(user)); }
    Trace awaitTrace(String id) throws InterruptedException {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        Trace trace;
        while(((trace=TRACES.get(id))==null || !trace.complete) && System.nanoTime()<deadline)Thread.sleep(5);
        if(trace==null || !trace.complete){var failure=new IllegalStateException("요청 종료 미확인 "+id);STOP.stop(failure);throw failure;}
        return trace;
    }
    Reply send(String method,String path,String authorization,String body,String key,String type,
            boolean unknownLength,Map<String,String> extra) throws Exception {
        STOP.check();String id="http-"+SEQUENCE.incrementAndGet();
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(9))
                .version(HttpClient.Version.HTTP_1_1).header("X-Test-Request-Id",id);
        if(authorization!=null)builder.header("Authorization",authorization);
        if(key!=null)builder.header("Idempotency-Key",key);
        if(type!=null)builder.header("Content-Type",type);
        extra.forEach(builder::header);
        byte[] bytes=body==null?new byte[0]:body.getBytes(StandardCharsets.UTF_8);
        var publisher=unknownLength?HttpRequest.BodyPublishers.ofInputStream(()->new java.io.ByteArrayInputStream(bytes)):
                HttpRequest.BodyPublishers.ofByteArray(bytes);
        try {
            var response=HttpClientTestSupport.send(client,builder.method(method,publisher).build());
            return new Reply(response.statusCode(),response.headers().map(),response.body(),awaitTrace(id));
        } catch(Exception failure){STOP.stop(failure);throw failure;}
    }
    Reply write(String path,String body,String key) throws Exception {
        return send("POST",path,auth("USER_A"),body,key,"application/json",false,Map.of());
    }
    Reply create(UUID resource,String key) throws Exception {return write("/api/reservations",payload(resource),key);}
    Reply cancel(UUID id) throws Exception {return write("/api/reservations/"+id+"/cancel",null,null);}
    void exhaust() { for(int i=0;i<20;i++)limiter.check(TestData.USER_A_ID); }
    String snapshot() {
        // 전체 대상·예약의 모든 컬럼을 하나의 SELECT snapshot으로 읽는다.
        return jdbc.queryForObject("""
                SELECT jsonb_build_object(
                  'resources',COALESCE((SELECT jsonb_agg(to_jsonb(r) ORDER BY r.id) FROM resources r),'[]'::jsonb),
                  'reservations',COALESCE((SELECT jsonb_agg(to_jsonb(v) ORDER BY v.id) FROM reservations v),'[]'::jsonb)
                )::text
                """,String.class);
    }
    void problem(Reply response,int status,String code) {
        assertThat(response.status()).isEqualTo(status);
        assertThat(response.header("Content-Type")).startsWith("application/problem+json");
        var body=JSON.readTree(response.body());assertThat(body.size()).isEqualTo(6);
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("code").asString()).isEqualTo(code);
        for(String field:List.of("type","title","detail","instance"))assertThat(body.get(field).asString()).isNotBlank();
        assertThat(response.body()).doesNotContain(PASSWORDS.values().toArray(String[]::new))
                .doesNotContain("PSQLException","java.lang.","stackTrace","INSERT INTO","UPDATE resources","SELECT ");
    }
    void counted(Reply response,boolean allowed) {
        var trace=response.trace();assertThat(trace.complete).isTrue();
        assertThat(trace.count("limiter-start")).isEqualTo(1);assertThat(trace.count("script")).isEqualTo(1);
        assertThat(trace.count(allowed?"allowed":"denied")).isEqualTo(1);
        assertThat(trace.events.stream().filter(e->e.phase().equals("limiter-start") || e.phase().equals("allowed")
                || e.phase().equals("denied"))).allSatisfy(e->assertThat(e.transaction()).isFalse());
        assertThat(trace.scriptCount).isPositive();assertThat(trace.scriptTtl).isBetween(0L,60000L);
        if(!allowed){
            long expected=Math.max(1,trace.scriptTtl/1000+(trace.scriptTtl%1000==0?0:1));
            assertThat(response.header("Retry-After")).isEqualTo(Long.toString(expected));
            assertThat(trace.count("controller")).isZero();assertThat(trace.count("service")).isZero();
            assertThat(trace.count("tx-before")).isZero();assertThat(trace.count("tx-start")).isZero();
        }
        int allowedAt=-1;
        for(int i=0;i<trace.events.size();i++){
            String phase=trace.events.get(i).phase();if(phase.equals("allowed"))allowedAt=i;
            if(List.of("tx-start","sql").contains(phase))assertThat(trace.events.get(i).transaction()).isTrue();
            if(List.of("controller","service","tx-before","tx-start","sql").contains(phase))assertThat(allowedAt).isGreaterThanOrEqualTo(0);
        }
    }
    void notCounted(Reply response) {
        assertThat(response.trace().count("limiter-start")).isZero();assertThat(response.trace().count("script")).isZero();
    }
}
