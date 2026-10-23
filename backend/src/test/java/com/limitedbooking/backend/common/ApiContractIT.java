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
    static final ThreadLocal<String> CURRENT_REQUEST=new ThreadLocal<>();
    static final ConcurrentMap<String,List<Long>> SCRIPT_RESULTS=new ConcurrentHashMap<>();
    static volatile CountDownLatch hold;
    static final AtomicBoolean holdNext=new AtomicBoolean();
    @LocalServerPort int port;
    @Autowired DataSource database;
    @Autowired com.limitedbooking.backend.ratelimit.RateLimitProperties ratePolicy;
    @Autowired org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory redisFactory;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean StringRedisTemplate redis;
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
                OBSERVATION.entered();CURRENT_REQUEST.set(id);
                OBSERVATION.receiving(id,http.getContentLengthLong(),http.getHeader("Transfer-Encoding"));
                var wrapped=new HttpServletRequestWrapper(http) {
                    @Override public ServletInputStream getInputStream() throws IOException {
                        var input=super.getInputStream();
                        return new ServletInputStream() {
                            @Override public boolean isFinished(){return input.isFinished();}
                            @Override public boolean isReady(){return input.isReady();}
                            @Override public void setReadListener(ReadListener listener){input.setReadListener(listener);}
                            @Override public int read() throws IOException {
                                int value=input.read();if(value>=0)OBSERVATION.read(id,1);return value;
                            }
                            @Override public int read(byte[] bytes,int offset,int length) throws IOException {
                                int count=input.read(bytes,offset,length);if(count>0)OBSERVATION.read(id,count);return count;
                            }
                        };
                    }
                };
                try {
                    chain.doFilter(wrapped,response);
                    if(holdNext.compareAndSet(true,false)) {
                        response.getOutputStream().close();
                        try { if(!hold.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("fixture 해제5초 초과"); }
                        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
                    }
                } finally { CURRENT_REQUEST.remove();OBSERVATION.completed(id); }
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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void responseReceivedButServerStillRunningBlocksNextStages(boolean transportWait) throws Exception {
        var state=new ConcurrencyTestSupport.StopState();
        var calls=new AtomicInteger();
        var http=new ApiContractHttpSupport(port,OBSERVATION,state,Duration.ofMillis(100));
        hold=new CountDownLatch(1);holdNext.set(true);
        try {
            var reply=http.send("GET","/api/resources/"+UUID.randomUUID(),Map.of("Authorization",
                    HttpClientTestSupport.basicAuth(TestData.USER_A_USERNAME,TestData.USER_A_PASSWORD)),null);
            assertThat(reply.status()).isEqualTo(404);
            if(transportWait) {
                assertThatThrownBy(()->http.transport(reply)).isInstanceOf(IllegalStateException.class).hasMessageContaining("in-flight");
                hold.countDown();OBSERVATION.await(Set.of(),Duration.ofSeconds(10));
                http.close(); // 두 번째 대기 이전에 종료돼도 최초 불확실 상태는 남아야 한다.
            } else assertThatThrownBy(http::close).isInstanceOf(IllegalStateException.class).hasMessageContaining("in-flight");
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
    private static final String CONTRACT_ID="10000000-0000-0000-0000-000000000001";
    record InvalidInput(String id,String method,String path,String body,Map<String,String> headers,
            List<ApiContractValidator.ValidationFailure> library,List<ApiContractValidator.ValidationFailure> raw) {
        @Override public String toString(){return id;}
    }
    private static ApiContractValidator.ValidationFailure schema(String suffix,String location) {
        return new ApiContractValidator.ValidationFailure("request-schema","validation.request."+suffix,location);
    }
    private static ApiContractValidator.ValidationFailure raw(String rule,String location) {
        return new ApiContractValidator.ValidationFailure("raw",rule,location);
    }
    static java.util.stream.Stream<InvalidInput> invalidInputs() {
        var cases=new ArrayList<InvalidInput>();
        var json=Map.of("Content-Type","application/json");
        String path="/api/resources";
        cases.add(new InvalidInput("name-blank","POST",path,"{\"name\":\"  \",\"capacity\":1}",json,List.of(),List.of(raw("normalized-name","/body/name"))));
        cases.add(new InvalidInput("name-101","POST",path,"{\"name\":\""+"가".repeat(101)+"\",\"capacity\":1}",json,List.of(),List.of(raw("normalized-name","/body/name"))));
        for(String[] fixture:List.of(new String[]{"-1","minimum"},new String[]{"1001","maximum"},new String[]{"\"1\"","type"},new String[]{"1.0","type"},new String[]{"null","type"})) {
            cases.add(new InvalidInput("capacity-"+fixture[0],"POST",path,"{\"name\":\"대상\",\"capacity\":"+fixture[0]+"}",json,
                    List.of(schema("body.schema."+fixture[1],"/body/capacity")),fixture[0].equals("1.0")?List.of(raw("integer-token","/body/capacity")):List.of()));
        }
        cases.add(new InvalidInput("capacity-missing","POST",path,"{\"name\":\"대상\"}",json,List.of(schema("body.schema.required","/body")),List.of()));
        cases.add(new InvalidInput("name-missing","POST",path,"{\"capacity\":1}",json,List.of(schema("body.schema.required","/body")),List.of()));
        cases.add(new InvalidInput("name-null","POST",path,"{\"name\":null,\"capacity\":1}",json,List.of(schema("body.schema.type","/body/name")),List.of()));
        cases.add(new InvalidInput("resource-extra","POST",path,"{\"name\":\"대상\",\"capacity\":1,\"extra\":1}",json,List.of(schema("body.schema.additionalProperties","/body")),List.of()));
        cases.add(new InvalidInput("resource-duplicate","POST",path,"{\"name\":\"앞\",\"name\":\"뒤\",\"capacity\":1}",json,List.of(),List.of(raw("duplicate-json","/body/name"))));
        cases.add(new InvalidInput("resource-json","POST",path,"{",json,List.of(schema("body.schema.invalidJson","/body")),List.of(raw("json","/body"))));
        cases.add(new InvalidInput("resource-trailing-root","POST",path,"{\"name\":\"대상\",\"capacity\":1} {}",json,List.of(),List.of(raw("json","/body"))));
        path="/api/reservations";
        var reservationHeaders=Map.of("Content-Type","application/json","Idempotency-Key","invalid-input");
        String valid="{\"resourceId\":\""+CONTRACT_ID+"\"}";
        cases.add(new InvalidInput("reservation-missing-id","POST",path,"{}",reservationHeaders,List.of(schema("body.schema.required","/body")),List.of()));
        cases.add(new InvalidInput("reservation-bad-uuid","POST",path,"{\"resourceId\":\"invalid\"}",reservationHeaders,List.of(schema("body.schema.format.uuid","/body/resourceId")),List.of()));
        cases.add(new InvalidInput("key-missing","POST",path,valid,json,List.of(schema("parameter.header.missing","/header/Idempotency-Key")),List.of()));
        cases.add(new InvalidInput("key-invalid","POST",path,valid,Map.of("Content-Type","application/json","Idempotency-Key","bad!"),List.of(schema("parameter.schema.pattern","/header/Idempotency-Key")),List.of()));
        cases.add(new InvalidInput("key-65","POST",path,valid,Map.of("Content-Type","application/json","Idempotency-Key","a".repeat(65)),List.of(schema("parameter.schema.pattern","/header/Idempotency-Key"),schema("parameter.schema.maxLength","/header/Idempotency-Key")),List.of()));
        for(String field:List.of("quantity","userId")) cases.add(new InvalidInput("reservation-extra-"+field,"POST",path,
                valid.substring(0,valid.length()-1)+",\""+field+"\":1}",reservationHeaders,List.of(schema("body.schema.additionalProperties","/body")),List.of()));
        cases.add(new InvalidInput("reservation-duplicate","POST",path,"{\"resourceId\":\""+CONTRACT_ID+"\",\"resourceId\":\""+CONTRACT_ID+"\"}",reservationHeaders,List.of(),List.of(raw("duplicate-json","/body/resourceId"))));
        cases.add(new InvalidInput("reservation-json","POST",path,"{",reservationHeaders,List.of(schema("body.schema.invalidJson","/body")),List.of(raw("json","/body"))));
        for(String endpoint:List.of("/api/resources/invalid","/api/reservations/invalid","/api/reservations/invalid/cancel")) {
            cases.add(new InvalidInput("path-"+endpoint,endpoint.endsWith("cancel")?"POST":"GET",endpoint,null,Map.of(),
                    List.of(schema("parameter.schema.format.uuid","/path/id")),List.of()));
        }
        for(String body:List.of(" ","{}")) cases.add(new InvalidInput("cancel-body-"+body,"POST","/api/reservations/"+CONTRACT_ID+"/cancel",body,Map.of(),List.of(schema("body.unexpected","/body")),List.of(raw("empty-cancel-body","/body"))));
        return cases.stream();
    }
    @org.junit.jupiter.params.ParameterizedTest(name="{0}")
    @org.junit.jupiter.params.provider.MethodSource("invalidInputs")
    void invalidInputMatchesExactContract(InvalidInput input) throws Exception {
        var headers=new LinkedHashMap<>(input.headers());headers.put("Authorization",adminAuth());
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            resetOwnedData(http);
            var reply=http.send(input.method(),input.path(),headers,input.body());
            assertThat(reply.status()).as(input.id()).isEqualTo(400);
            var validator=ApiContractValidator.load("business.openapi.yaml").expectInvalidRequest(
                    new ApiContractValidator.ExpectedRequestViolations(input.library(),input.raw(),400,"INVALID_REQUEST"));
            reply.assertValid(validator);
        }
    }
    private static final UUID ERROR_RESOURCE=UUID.fromString(CONTRACT_ID);
    private static final UUID ERROR_OTHER=UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ERROR_RESERVATION=UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String MISSING_ID="99999999-0000-0000-0000-000000000001";
    record ErrorCase(String id,String method,String path,String body,String user,boolean wrongPassword,
            String key,int status,String code) {
        @Override public String toString(){return id;}
    }
    static java.util.stream.Stream<ErrorCase> securityAndBusinessCases() {
        var cases=new ArrayList<ErrorCase>();
        for(String endpoint:List.of("/api/resources","/api/resources/"+ERROR_RESOURCE,"/api/reservations",
                "/api/reservations/"+ERROR_RESERVATION,"/api/reservations/"+ERROR_RESERVATION+"/cancel")) {
            String method=endpoint.equals("/api/resources")||endpoint.equals("/api/reservations")||endpoint.endsWith("cancel")?"POST":"GET";
            String body=endpoint.equals("/api/resources")?"{\"name\":\"대상\",\"capacity\":1}":endpoint.equals("/api/reservations")?"{\"resourceId\":\""+ERROR_OTHER+"\"}":null;
            cases.add(new ErrorCase("no-auth "+endpoint,method,endpoint,body,null,false,"valid-key",401,"UNAUTHENTICATED"));
            cases.add(new ErrorCase("wrong-auth "+endpoint,method,endpoint,body,"USER_A",true,"valid-key",401,"UNAUTHENTICATED"));
        }
        cases.add(new ErrorCase("resource-forbidden","POST","/api/resources","{\"name\":\"대상\",\"capacity\":1}","USER_A",false,null,403,"FORBIDDEN"));
        for(String path:List.of("/api/resources/"+MISSING_ID,"/api/reservations/"+MISSING_ID,"/api/reservations/"+MISSING_ID+"/cancel"))
            cases.add(new ErrorCase("missing "+path,path.endsWith("cancel")?"POST":"GET",path,null,"USER_A",false,null,404,"NOT_FOUND"));
        cases.add(new ErrorCase("missing-create-resource","POST","/api/reservations","{\"resourceId\":\""+MISSING_ID+"\"}","USER_A",false,"new-key",404,"NOT_FOUND"));
        for(String user:List.of("USER_B","ADMIN")) for(boolean cancel:List.of(false,true))
            cases.add(new ErrorCase("other-owner "+user+" "+cancel,cancel?"POST":"GET","/api/reservations/"+ERROR_RESERVATION+(cancel?"/cancel":""),null,user,false,null,404,"NOT_FOUND"));
        cases.add(new ErrorCase("sold-out","POST","/api/reservations","{\"resourceId\":\""+ERROR_RESOURCE+"\"}","USER_A",false,"new-key",409,"SOLD_OUT"));
        cases.add(new ErrorCase("key-conflict","POST","/api/reservations","{\"resourceId\":\""+ERROR_OTHER+"\"}","USER_A",false,"existing-key",409,"IDEMPOTENCY_CONFLICT"));
        return cases.stream();
    }
    private void prepareErrorData(ApiContractHttpSupport http) {
        http.prepare(()->{
            var data=new TestData(database);
            data.resource(ERROR_RESOURCE,"소진 대상",1,0,Instant.EPOCH);
            data.resource(ERROR_OTHER,"다른 대상",1,1,Instant.EPOCH);
            data.reservation(new com.limitedbooking.backend.reservation.StoredReservation(ERROR_RESERVATION,ERROR_RESOURCE,
                    TestData.USER_A_ID,"existing-key",com.limitedbooking.backend.reservation.ReservationStatus.CONFIRMED,Instant.EPOCH,null));
        });
    }
    private static String auth(String user,boolean wrong) {
        return HttpClientTestSupport.basicAuth(user,wrong?"incorrect-test-password":switch(user){
            case "ADMIN"->TestData.ADMIN_PASSWORD;case "USER_A"->TestData.USER_A_PASSWORD;default->TestData.USER_B_PASSWORD;
        });
    }
    private static void assertProblem(ApiContractValidator.HttpExchange exchange,int status,String code) throws Exception {
        assertThat(exchange.status()).isEqualTo(status);
        var problem=tools.jackson.databind.json.JsonMapper.builder().build().readTree(exchange.responseBody());
        assertThat(problem.get("code").asString()).isEqualTo(code);
        exchange.assertValid(ApiContractValidator.load("business.openapi.yaml"));
    }
    @org.junit.jupiter.params.ParameterizedTest(name="{0}")
    @org.junit.jupiter.params.provider.MethodSource("securityAndBusinessCases")
    void securityAndBusinessErrorMatchesContract(ErrorCase fixture) throws Exception {
        Map<UUID,DatabaseAssertions.InventorySnapshot> before;
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            resetOwnedData(http);prepareErrorData(http);
            before=new DatabaseAssertions(database).snapshot(List.of(ERROR_RESOURCE,ERROR_OTHER));
            var headers=new LinkedHashMap<String,String>();
            if(fixture.user()!=null)headers.put("Authorization",auth(fixture.user(),fixture.wrongPassword()));
            if(fixture.body()!=null)headers.put("Content-Type","application/json");
            if(fixture.key()!=null)headers.put("Idempotency-Key",fixture.key());
            var reply=http.send(fixture.method(),fixture.path(),headers,fixture.body());
            assertProblem(reply,fixture.status(),fixture.code());
        }
        assertThat(new DatabaseAssertions(database).snapshot(List.of(ERROR_RESOURCE,ERROR_OTHER))).isEqualTo(before);
    }
    record BodyCase(String id,String method,String path,int bytes,boolean chunked,String user,boolean wrong,
            boolean multibyte,int status,String code) {
        @Override public String toString(){return id;}
    }
    static java.util.stream.Stream<BodyCase> bodyCases() {
        var cases=new ArrayList<BodyCase>();
        for(boolean chunked:List.of(false,true)) for(String endpoint:List.of("/api/resources","/api/resources/"+ERROR_RESOURCE,
                "/api/reservations","/api/reservations/"+ERROR_RESERVATION,"/api/reservations/"+ERROR_RESERVATION+"/cancel")) {
            String method=endpoint.equals("/api/resources")||endpoint.equals("/api/reservations")||endpoint.endsWith("cancel")?"POST":"GET";
            for(int bytes:List.of(8192,8193)) {
                int status=bytes==8193?413:endpoint.endsWith("cancel")?400:endpoint.equals("/api/resources")||endpoint.equals("/api/reservations")?201:200;
                cases.add(new BodyCase(endpoint+" "+bytes+" "+chunked,method,endpoint,bytes,chunked,endpoint.equals("/api/resources")?"ADMIN":"USER_A",false,false,status,
                        status==413?"PAYLOAD_TOO_LARGE":status==400?"INVALID_REQUEST":null));
            }
        }
        for(boolean chunked:List.of(false,true)) {
            cases.add(new BodyCase("multibyte "+chunked,"POST","/api/resources",8193,chunked,"ADMIN",false,true,413,"PAYLOAD_TOO_LARGE"));
            cases.add(new BodyCase("no-auth-size-first "+chunked,"POST","/api/resources",8193,chunked,null,false,false,413,"PAYLOAD_TOO_LARGE"));
            cases.add(new BodyCase("wrong-auth-size-first "+chunked,"POST","/api/resources",8193,chunked,"ADMIN",true,false,413,"PAYLOAD_TOO_LARGE"));
        }
        return cases.stream();
    }
    private static String sizedBody(String path,int bytes,boolean multibyte) {
        String base=path.equals("/api/resources")?"{\"name\":\""+(multibyte?"😀":"경계")+"\",\"capacity\":1}":
                path.equals("/api/reservations")?"{\"resourceId\":\""+ERROR_OTHER+"\"}":"";
        int padding=bytes-base.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return base+" ".repeat(padding);
    }
    private static ApiContractValidator forBody(String method,String path,int bytes,int status,String code) throws Exception {
        var library=new ArrayList<ApiContractValidator.ValidationFailure>();
        var raw=new ArrayList<ApiContractValidator.ValidationFailure>();
        if(!path.equals("/api/resources")&&!path.equals("/api/reservations"))library.add(schema("body.unexpected","/body"));
        if(path.endsWith("cancel"))raw.add(raw("empty-cancel-body","/body"));
        if(bytes>8192)raw.add(raw("byte-limit","/body"));
        var base=ApiContractValidator.load("business.openapi.yaml");
        return library.isEmpty()&&raw.isEmpty()?base:base.expectInvalidRequest(
                new ApiContractValidator.ExpectedRequestViolations(library,raw,status,code));
    }
    @org.junit.jupiter.params.ParameterizedTest(name="{0}")
    @org.junit.jupiter.params.provider.MethodSource("bodyCases")
    void actualBodyFramingAndUtf8BoundaryMatchesContract(BodyCase fixture) throws Exception {
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            resetOwnedData(http);prepareErrorData(http);
            var headers=new LinkedHashMap<String,String>();
            headers.put("Content-Type","application/json");headers.put("Idempotency-Key","boundary-key");
            if(fixture.user()!=null)headers.put("Authorization",auth(fixture.user(),fixture.wrong()));
            String body=sizedBody(fixture.path(),fixture.bytes(),fixture.multibyte());
            assertThat(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(fixture.bytes());
            if(fixture.multibyte())assertThat(body.length()).isLessThan(fixture.bytes());
            var reply=http.sendFramed(fixture.method(),fixture.path(),headers,body,fixture.chunked());
            assertThat(reply.status()).isEqualTo(fixture.status());
            reply.assertValid(forBody(fixture.method(),fixture.path(),fixture.bytes(),fixture.status(),fixture.code()));
            var transport=http.transport(reply);
            assertThat(transport.intendedBytes()).isEqualTo(fixture.bytes());
            assertThat(transport.publishedBytes()).isBetween(0,fixture.bytes());
            assertThat(transport.receivedBytes()).isBetween(0,fixture.bytes());
            if(!fixture.chunked() && fixture.bytes()>8192)assertThat(transport.receivedBytes()).isZero();
            else assertThat(transport.receivedBytes()).isEqualTo(fixture.bytes());
            if(fixture.chunked()) {
                assertThat(transport.contentLength()).isEqualTo(-1);
                assertThat(transport.transferEncoding()).isEqualToIgnoringCase("chunked");
            } else assertThat(transport.contentLength()).isEqualTo(fixture.bytes());
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"/api/resources","/api/reservations"})
    void unsupportedContentTypeMatchesContract(String path) throws Exception {
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            resetOwnedData(http);
            String body=path.endsWith("resources")?"{\"name\":\"대상\",\"capacity\":1}":"{\"resourceId\":\""+ERROR_OTHER+"\"}";
            var reply=http.send("POST",path,Map.of("Authorization",adminAuth(),"Content-Type","text/plain","Idempotency-Key","media-key"),body);
            assertThat(reply.status()).isEqualTo(415);
            reply.assertValid(ApiContractValidator.load("business.openapi.yaml").expectInvalidRequest(new ApiContractValidator.ExpectedRequestViolations(
                    List.of(schema("contentType.notAllowed","/headers/Content-Type")),List.of(),415,"UNSUPPORTED_MEDIA_TYPE")));
        }
    }
    private long decisionPttl(ApiContractValidator.HttpExchange reply) {
        var result=SCRIPT_RESULTS.get(header(reply.requestHeaders(),"X-Test-Request-Id"));
        assertThat(result).as("실제 Lua 판정 관측").isNotNull().hasSize(2);
        assertThat(result.getFirst()).isEqualTo(21);return result.get(1);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void defaultRateLimitAndDecisionPttlMatchContract(boolean cancel) throws Exception {
        RateLimitTestSupport.assertSettings(ratePolicy,redisFactory,20);
        SCRIPT_RESULTS.clear();
        org.mockito.Mockito.doAnswer(call->{
            var result=(List<?>)call.callRealMethod();
            String id=CURRENT_REQUEST.get();
            if(id!=null) SCRIPT_RESULTS.put(id,List.of((Long)result.get(0),(Long)result.get(1)));
            return result;
        }).when(redis).execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                org.mockito.ArgumentMatchers.anyList(),org.mockito.ArgumentMatchers.any(Object[].class));
        var validator=ApiContractValidator.load("business.openapi.yaml");
        Map<UUID,DatabaseAssertions.InventorySnapshot> before;
        try(var http=new ApiContractHttpSupport(port,OBSERVATION,STOP)) {
            resetOwnedData(http);prepareErrorData(http);
            String path=cancel?"/api/reservations/"+ERROR_RESERVATION+"/cancel":"/api/reservations";
            String body=cancel?null:"{\"resourceId\":\""+ERROR_OTHER+"\"}";
            var headers=Map.of("Content-Type","application/json","Authorization",userAuth(),"Idempotency-Key","rate-key");
            for(int i=0;i<20;i++) {
                var admitted=http.send("POST",path,headers,body);
                assertThat(admitted.status()).isEqualTo(cancel?200:201);admitted.assertValid(validator);
            }
            before=new DatabaseAssertions(database).snapshot(List.of(ERROR_RESOURCE,ERROR_OTHER));
            var denied=http.send("POST",path,headers,body);
            assertProblem(denied,429,"RATE_LIMITED");
            validator.assertRetryAfter(denied,decisionPttl(denied));
            assertThat(Long.parseLong(header(denied.responseHeaders(),"Retry-After")))
                    .isEqualTo(Math.max(1,(decisionPttl(denied)+999)/1000));
            System.out.println("contract-rate cancel="+cancel+" count=21 decisionPttl="+decisionPttl(denied)
                    +" retryAfter="+header(denied.responseHeaders(),"Retry-After"));
            assertThat(new RateLimitTestSupport(redis).count(TestData.USER_A_ID)).isEqualTo(21);
            for(boolean chunked:List.of(false,true)) {
                var oversized=http.sendFramed("POST",path,headers,sizedBody(path,8193,false),chunked);
                assertThat(oversized.status()).isEqualTo(413);
                oversized.assertValid(forBody("POST",path,8193,413,"PAYLOAD_TOO_LARGE"));
                assertThat(SCRIPT_RESULTS).doesNotContainKey(header(oversized.requestHeaders(),"X-Test-Request-Id"));
            }
            assertThat(new RateLimitTestSupport(redis).count(TestData.USER_A_ID)).isEqualTo(21);
        } finally {org.mockito.Mockito.reset(redis);}
        assertThat(new DatabaseAssertions(database).snapshot(List.of(ERROR_RESOURCE,ERROR_OTHER))).isEqualTo(before);
    }
}
