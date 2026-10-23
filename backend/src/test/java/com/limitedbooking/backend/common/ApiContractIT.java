package com.limitedbooking.backend.common;

import com.limitedbooking.backend.support.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import static org.assertj.core.api.Assertions.*;

@Tag("contract")
@DirtiesContext
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ApiContractIT.ObservationConfiguration.class)
@TestExecutionListeners(listeners=ApiContractIT.Cleanup.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class ApiContractIT {
    static final ContainerTestSupport CONTAINERS=new ContainerTestSupport();
    static final ApiContractHttpSupport.ServerObservation OBSERVATION=new ApiContractHttpSupport.ServerObservation();
    static final ConcurrencyTestSupport.StopState STOP=new ConcurrencyTestSupport.StopState();
    static volatile CountDownLatch hold;
    static final AtomicBoolean holdNext=new AtomicBoolean();
    @LocalServerPort int port;
    @Autowired DataSource database;
    @Autowired StringRedisTemplate redis;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        CONTAINERS.registerLocalAuthAndDatabaseProperties(properties);
        properties.add("spring.datasource.hikari.maximum-pool-size",()->10);
        properties.add("spring.datasource.hikari.connection-timeout",()->3000);
    }
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder(){return 2999;}
        @Override public void afterTestClass(TestContext context){CONTAINERS.close();}
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class ObservationConfiguration {
        @Bean FilterRegistrationBean<Filter> contractObservation() {
            Filter filter=(request,response,chain)->{
                var http=(HttpServletRequest)request;
                String id=http.getHeader("X-Test-Request-Id");
                if(id==null){chain.doFilter(request,response);return;}
                OBSERVATION.entered();
                try {
                    chain.doFilter(request,response);
                    if(holdNext.compareAndSet(true,false)) {
                        response.getOutputStream().close();
                        try { if(!hold.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("fixture 해제5초 초과"); }
                        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
                    }
                } finally { OBSERVATION.completed(id); }
            };
            var bean=new FilterRegistrationBean<>(filter);bean.setOrder(Ordered.HIGHEST_PRECEDENCE);return bean;
        }
    }
    @Test void capturesActualRawExchangeAndCommittedOwnedDatabase() throws Exception {
        UUID id=UUID.randomUUID();
        UUID createdId;
        String body=" { \"capacity\" : 1, \"name\" : \"원문\" } ";
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            http.reset(()->{new RateLimitTestSupport(redis).reset();new TestData(database).reset();});
            http.prepare(()->new TestData(database).resource(id,"준비",1,1,Instant.parse("2026-10-23T00:00:00Z")));
            var exchange=http.send("POST","/api/resources",Map.of("Content-Type","application/json",
                    "Authorization",HttpClientTestSupport.basicAuth(TestData.ADMIN_USERNAME,TestData.ADMIN_PASSWORD)),body);
            assertThat(exchange.status()).isEqualTo(201);
            createdId=UUID.fromString(tools.jackson.databind.json.JsonMapper.builder().build()
                    .readTree(exchange.responseBody()).get("id").asText());
            assertThat(exchange.requestBody()).isEqualTo(body);
            assertThat(exchange.path()).isEqualTo("/api/resources");
            assertThat(exchange.requestHeaders()).containsKey("Authorization");
            exchange.assertValid(ApiContractValidator.load("business.openapi.yaml"));
            String rawPath="/api/resources/%"+Integer.toHexString(id.toString().charAt(0))+id.toString().substring(1);
            var get=http.send("GET",rawPath,Map.of("Authorization",
                    HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME,TestData.USER_A_PASSWORD)),null);
            assertThat(get.status()).isEqualTo(200);assertThat(get.requestBody()).isNull();
            assertThat(get.path()).isEqualTo(rawPath);
            var empty=http.send("GET",rawPath,Map.of("Authorization",
                    HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME,TestData.USER_A_PASSWORD)),"");
            assertThat(empty.status()).isEqualTo(200);assertThat(empty.requestBody()).isEmpty();
        }
        var snapshot=new DatabaseAssertions(database).snapshot(List.of(id,createdId));
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.values()).allSatisfy(row->{
            assertThat(row.capacity()).isEqualTo(1);assertThat(row.available()).isEqualTo(1);
            assertThat(row.totalCount()).isZero();
        });
    }
    @Test void resourceSuccessMatchesContract() throws Exception {
        var validator=ApiContractValidator.load("business.openapi.yaml");
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        // 기대 이름은 제품 strip/길이 규칙을 호출하지 않고 명시한다.
        var names=List.of("수량0", "수량1000", "가".repeat(100), "😀".repeat(100), "\u00a0");
        var rawNames=List.of("수량0", "수량1000", " "+"가".repeat(100)+" ", "😀".repeat(100), "\u00a0");
        var capacities=List.of(0,1000,1,1,1);
        var ids=new ArrayList<UUID>();
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            resetOwnedData(http);
            for(int i=0;i<names.size();i++) {
                String body=" { \"capacity\" : "+capacities.get(i)+", \"name\" : "
                        +mapper.writeValueAsString(rawNames.get(i))+" } ";
                var created=http.send("POST","/api/resources",Map.of("content-type","application/json",
                        "aUtHoRiZaTiOn",adminAuth()),body);
                assertThat(created.status()).isEqualTo(201);
                assertThat(created.requestBody()).isEqualTo(body);
                created.assertValid(validator);
                var resource=mapper.readTree(created.responseBody());
                assertResource(resource,names.get(i),capacities.get(i));
                UUID id=UUID.fromString(resource.get("id").asText());ids.add(id);
                assertThat(header(created.responseHeaders(),"LOCATION")).isEqualTo("/api/resources/"+id);
                var found=http.send("GET","/api/resources/"+id,Map.of("authorization",userAuth()),null);
                assertThat(found.status()).isEqualTo(200);found.assertValid(validator);
                assertThat(mapper.readTree(found.responseBody())).isEqualTo(resource);
            }
        }
        var snapshot=new DatabaseAssertions(database).snapshot(ids);
        for(int i=0;i<ids.size();i++) {
            var row=snapshot.get(ids.get(i));
            assertThat(row.capacity()).isEqualTo(capacities.get(i));
            assertThat(row.available()).isEqualTo(capacities.get(i));
            assertThat(row.totalCount()).isZero();assertThat(row.confirmedCount()).isZero();
        }
    }

    @Test void resourceJsonOrderAndHeaderCasePreserveEquivalentValues() throws Exception {
        var validator=ApiContractValidator.load("business.openapi.yaml");
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        var bodies=List.of(" { \"name\" : \"동등성\", \"capacity\" : 1 } ",
                "{\"capacity\":1,\"name\":\"동등성\"}");
        var ids=new ArrayList<UUID>();
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            resetOwnedData(http);
            for(int i=0;i<bodies.size();i++) {
                var headers=i==0?Map.of("Content-Type","application/json","Authorization",adminAuth())
                        :Map.of("cOnTeNt-TyPe","application/json","authorization",adminAuth());
                var exchange=http.send("POST","/api/resources",headers,bodies.get(i));
                assertThat(exchange.status()).isEqualTo(201);
                assertThat(exchange.requestBody()).isEqualTo(bodies.get(i));exchange.assertValid(validator);
                var body=mapper.readTree(exchange.responseBody());assertResource(body,"동등성",1);
                UUID id=UUID.fromString(body.get("id").asText());ids.add(id);
                assertThat(header(exchange.responseHeaders(),"Location")).isEqualTo("/api/resources/"+id);
            }
        }
        assertThat(ids).doesNotHaveDuplicates();
        var snapshot=new DatabaseAssertions(database).snapshot(ids);
        assertThat(snapshot.values()).allSatisfy(row->{
            assertThat(row.capacity()).isEqualTo(1);assertThat(row.available()).isEqualTo(1);
            assertThat(row.totalCount()).isZero();assertThat(row.confirmedCount()).isZero();
        });
    }

    @Test void reservationCreateOwnerReadAndCancelMatchContract() throws Exception {
        var validator=ApiContractValidator.load("business.openapi.yaml");
        var mapper=tools.jackson.databind.json.JsonMapper.builder().build();
        UUID resourceId=UUID.randomUUID();
        String key="contract-"+UUID.randomUUID();
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            resetOwnedData(http);
            http.prepare(()->new TestData(database).resource(resourceId,"예약 대상",1,1,
                    Instant.parse("2026-10-23T00:00:00Z")));
            String request=" { \"resourceId\" : \""+resourceId+"\" } ";
            var created=http.send("POST","/api/reservations",Map.of("Content-Type","application/json",
                    "Authorization",userAuth(),"iDeMpOtEnCy-KeY",key),request);
            assertThat(created.status()).isEqualTo(201);
            assertThat(created.requestBody()).isEqualTo(request);created.assertValid(validator);
            assertThat(header(created.responseHeaders(),"Idempotent-Replayed")).isEqualTo("false");
            var initial=mapper.readTree(created.responseBody());
            assertFields(initial,"id","resourceId","status","createdAt");
            UUID reservationId=UUID.fromString(initial.get("id").asText());
            assertThat(initial.get("resourceId").asText()).isEqualTo(resourceId.toString());
            assertThat(initial.get("status").asText()).isEqualTo("CONFIRMED");
            assertThat(initial.has("cancelledAt")).isFalse();
            assertUtcMicros(initial.get("createdAt").asText());
            String path="/api/reservations/"+reservationId;
            assertThat(header(created.responseHeaders(),"location")).isEqualTo(path);
            var found=http.send("GET",path,Map.of("Authorization",userAuth()),null);
            assertThat(found.status()).isEqualTo(200);found.assertValid(validator);
            var current=mapper.readTree(found.responseBody());
            assertCurrentReservation(current,initial,"CONFIRMED");
            assertThat(current.get("cancelledAt").isNull()).isTrue();
            var cancelled=http.send("POST",path+"/cancel",Map.of("authorization",userAuth()),null);
            assertThat(cancelled.status()).isEqualTo(200);
            assertThat(cancelled.requestBody()).isNull();cancelled.assertValid(validator);
            var finalView=mapper.readTree(cancelled.responseBody());
            assertCurrentReservation(finalView,initial,"CANCELLED");
            assertUtcMicros(finalView.get("cancelledAt").asText());
        }
        var row=new DatabaseAssertions(database).snapshot(List.of(resourceId)).get(resourceId);
        assertThat(row.capacity()).isEqualTo(1);assertThat(row.available()).isEqualTo(1);
        assertThat(row.totalCount()).isEqualTo(1);assertThat(row.confirmedCount()).isZero();
    }

    private void resetOwnedData(ApiContractHttpSupport http) {
        http.reset(()->{new RateLimitTestSupport(redis).reset();new TestData(database).reset();});
    }
    private static String adminAuth() {
        return HttpClientTestSupport.basicAuth(TestData.ADMIN_USERNAME,TestData.ADMIN_PASSWORD);
    }
    private static String userAuth() {
        return HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME,TestData.USER_A_PASSWORD);
    }
    private static String header(Map<String,String> headers,String name) {
        var values=headers.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue).toList();
        assertThat(values).as("필수 헤더 %s",name).hasSize(1);return values.getFirst();
    }
    private static void assertFields(tools.jackson.databind.JsonNode body,String... fields) {
        assertThat(body.isObject()).isTrue();assertThat(body.size()).isEqualTo(fields.length);
        for(String field:fields) assertThat(body.has(field)).as("필수 필드 %s",field).isTrue();
    }
    private static void assertResource(tools.jackson.databind.JsonNode body,String name,int capacity) {
        assertFields(body,"id","name","capacity","available");
        assertThat(UUID.fromString(body.get("id").asText())).isNotNull();
        assertThat(body.get("name").asText()).isEqualTo(name);
        assertThat(body.get("capacity").isIntegralNumber()).isTrue();
        assertThat(body.get("available").isIntegralNumber()).isTrue();
        assertThat(body.get("capacity").asInt()).isEqualTo(capacity);
        assertThat(body.get("available").asInt()).isEqualTo(capacity);
    }
    private static void assertCurrentReservation(tools.jackson.databind.JsonNode body,
            tools.jackson.databind.JsonNode initial,String status) {
        assertFields(body,"id","resourceId","status","createdAt","cancelledAt");
        for(String field:List.of("id","resourceId","createdAt"))
            assertThat(body.get(field)).as("생성값 유지 %s",field).isEqualTo(initial.get(field));
        assertThat(body.get("status").asText()).isEqualTo(status);
        assertUtcMicros(body.get("createdAt").asText());
    }
    private static void assertUtcMicros(String value) {
        assertThat(value).endsWith("Z");
        var time=OffsetDateTime.parse(value);
        assertThat(time.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(time.getNano()%1000).isZero();
    }

    @Test void responseReceivedButServerStillRunningBlocksNextStages() throws Exception {
        var state=new ConcurrencyTestSupport.StopState();
        var calls=new AtomicInteger();
        var http=new ApiContractHttpSupport(port,OBSERVATION,state,Duration.ofMillis(100));
        hold=new CountDownLatch(1);holdNext.set(true);
        try {
            var reply=http.send("GET","/api/resources/"+UUID.randomUUID(),Map.of("Authorization",
                    HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME,TestData.USER_A_PASSWORD)),null);
            assertThat(reply.status()).isEqualTo(404);
            assertThatThrownBy(http::close).isInstanceOf(IllegalStateException.class).hasMessageContaining("in-flight");
            assertThat(state.stopped()).isTrue();
            try(var next=new ApiContractHttpSupport(port,OBSERVATION,state)) {
                assertThatThrownBy(()->next.reset(calls::incrementAndGet)).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(()->next.prepare(calls::incrementAndGet)).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(()->next.send("GET","/api/resources/"+UUID.randomUUID(),Map.of(),null)).isInstanceOf(IllegalStateException.class);
                assertThat(next.submittedCount()).isZero();
                assertThat(calls).hasValue(0);
            }
        } finally { hold.countDown();holdNext.set(false);OBSERVATION.await(Set.of(),Duration.ofSeconds(10));http.close(); }
    }
    @Test void actualTruncatedHttpTransportStopsAndPreservesOriginalFailure() throws Exception {
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        var executor=Executors.newSingleThreadExecutor();server.setExecutor(executor);
        server.createContext("/",exchange->{
            exchange.sendResponseHeaders(200,100);exchange.getResponseBody().write(new byte[]{123,125});exchange.close();
        });server.start();
        var state=new ConcurrencyTestSupport.StopState();
        var http=new ApiContractHttpSupport(server.getAddress().getPort(),new ApiContractHttpSupport.ServerObservation(),state,Duration.ofMillis(50));
        try {
            Throwable original=catchThrowable(()->{
                try(http) { http.send("GET","/api/resources/"+UUID.randomUUID(),Map.of(),null); }
            });
            assertThat(original).isInstanceOf(IOException.class);
            assertThat(state.stopped()).isTrue();
            assertThat(original.getSuppressed()).isNotEmpty();
            var calls=new AtomicInteger();
            try(var next=new ApiContractHttpSupport(port,OBSERVATION,state)) {
                assertThatThrownBy(()->next.reset(calls::incrementAndGet)).hasCause(original);
                assertThatThrownBy(()->next.prepare(calls::incrementAndGet)).hasCause(original);
                assertThatThrownBy(()->next.send("GET","/api/resources/"+UUID.randomUUID(),Map.of(),null)).hasCause(original);
                assertThat(next.submittedCount()).isZero();assertThat(calls).hasValue(0);
            }
        } finally {
            try { http.close(); } finally {
                server.stop(0);executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();
            }
        }
    }
}
