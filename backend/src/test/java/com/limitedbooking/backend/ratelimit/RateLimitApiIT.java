package com.limitedbooking.backend.ratelimit;

import com.limitedbooking.backend.support.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import java.util.*;
import java.util.stream.Stream;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

@DirtiesContext
@TestExecutionListeners(listeners=RateLimitApiIT.Cleanup.class,mergeMode=TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RateLimitHttpTestSupport.ObservationConfiguration.class)
class RateLimitApiIT extends RateLimitHttpTestSupport {
    static final ContainerTestSupport containers=new ContainerTestSupport();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p){properties(containers,p);}
    public static class Cleanup extends AbstractTestExecutionListener {
        @Override public int getOrder(){return 2999;}
        @Override public void afterTestClass(TestContext context){containers.close();}
    }
    @Test void combinesCreatesAndCancelsAndIsolatesPrincipalUsers() throws Exception {
        UUID resource=resource(30), owned=seed(resource,TestData.USER_A_ID,"seed");
        for(int i=1;i<=20;i++){
            var response=i<=10?create(resource,"create-"+i):cancel(owned);
            assertThat(response.status()).isEqualTo(i<=10?201:200);counted(response,true);
            assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(i);
        }
        String before=snapshot();var denied=create(resource,"over");problem(denied,429,"RATE_LIMITED");counted(denied,false);
        assertThat(Long.parseLong(denied.header("Retry-After"))).isBetween(1L,60L);
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(21);assertThat(snapshot()).isEqualTo(before);
        for(String user:List.of("USER_B","ADMIN")){
            var response=send("POST","/api/reservations",auth(user),payload(resource),"independent","application/json",false,
                    Map.of("X-User-Id",TestData.USER_A_ID.toString()));
            assertThat(response.status()).isEqualTo(201);counted(response,true);
            UUID owner=user.equals("ADMIN")?TestData.ADMIN_ID:TestData.USER_B_ID;
            assertThat(keys.count(owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT user_id FROM reservations WHERE id=?",UUID.class,
                    UUID.fromString(JSON.readTree(response.body()).get("id").asString()))).isEqualTo(owner);
        }
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(21);
    }
    record Invalid(String body,String key,String type,int status,String code) {}
    static Stream<Invalid> invalidCreates(){return Stream.of(
            new Invalid("{","k","application/json",400,"INVALID_REQUEST"),
            new Invalid("{\"resourceId\":\"not-uuid\"}","k","application/json",400,"INVALID_REQUEST"),
            new Invalid("VALID","invalid.key","application/json",400,"INVALID_REQUEST"),
            new Invalid("VALID",null,"application/json",400,"INVALID_REQUEST"),
            new Invalid("{\"resourceId\":\"ID\",\"quantity\":1}","k","application/json",400,"INVALID_REQUEST"),
            new Invalid("{\"resourceId\":\"ID\",\"userId\":\"ID\"}","k","application/json",400,"INVALID_REQUEST"),
            new Invalid("{\"resourceId\":\"ID\",\"resourceId\":\"ID\"}","k","application/json",400,"INVALID_REQUEST"),
            new Invalid("VALID","k","text/plain",415,"UNSUPPORTED_MEDIA_TYPE"));}
    String body(Invalid input,UUID resource){return input.body().equals("VALID")?payload(resource):input.body().replace("ID",resource.toString());}
    @ParameterizedTest @MethodSource("invalidCreates")
    void countsInvalidCreatesAndRejectsBeforeInputWhenExhausted(Invalid input) throws Exception {
        UUID resource=resource(1);String before=snapshot();
        var response=send("POST","/api/reservations",auth("USER_A"),body(input,resource),input.key(),input.type(),false,Map.of());
        problem(response,input.status(),input.code());counted(response,true);assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(1);
        assertThat(snapshot()).isEqualTo(before);
        for(int i=1;i<20;i++)limiter.check(TestData.USER_A_ID);
        var denied=send("POST","/api/reservations",auth("USER_A"),body(input,resource),input.key(),input.type(),false,Map.of());
        problem(denied,429,"RATE_LIMITED");counted(denied,false);
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(21);assertThat(snapshot()).isEqualTo(before);
    }
    @Test void countsOwnershipMissingSoldOutAndConflictingRequests() throws Exception {
        UUID full=resource(2), empty=resource(0), other=resource(1), foreign=seed(full,TestData.USER_B_ID,"foreign");
        seed(full,TestData.USER_A_ID,"existing");String before=snapshot();
        var responses=List.of(cancel(foreign),cancel(UUID.randomUUID()),create(empty,"empty"),create(other,"existing"));
        String[] codes={"NOT_FOUND","NOT_FOUND","SOLD_OUT","IDEMPOTENCY_CONFLICT"};
        for(int i=0;i<responses.size();i++){problem(responses.get(i),i<2?404:409,codes[i]);counted(responses.get(i),true);}
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(4);assertThat(snapshot()).isEqualTo(before);
        for(int i=4;i<20;i++)limiter.check(TestData.USER_A_ID);
        for(var response:List.of(cancel(foreign),cancel(UUID.randomUUID()),create(empty,"empty"),create(other,"existing"))){
            problem(response,429,"RATE_LIMITED");counted(response,false);
        }
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(24);assertThat(snapshot()).isEqualTo(before);
    }
    @Test void doesNotCountAuthenticationFailuresOrIndependentApis() throws Exception {
        UUID resource=resource(2), owned=seed(resource,TestData.USER_A_ID,"seed");
        for(String authorization:Arrays.asList(null,HttpClientTestSupport.basicAuth("USER_A","wrong"))){
            for(String path:List.of("/api/reservations","/api/reservations/"+owned+"/cancel")){
                var response=send("POST",path,authorization,"{","invalid.key","text/plain",false,Map.of());
                problem(response,401,"UNAUTHENTICATED");assertThat(response.header("WWW-Authenticate")).startsWith("Basic ");notCounted(response);
            }
        }
        var resourceGet=send("GET","/api/resources/"+resource,auth("USER_A"),null,null,null,false,Map.of());
        var ownedGet=send("GET","/api/reservations/"+owned,auth("USER_A"),null,null,null,false,Map.of());
        var resourcePost=send("POST","/api/resources",auth("ADMIN"),"{\"name\":\"독립 대상\",\"capacity\":0}",null,"application/json",false,Map.of());
        assertThat(resourceGet.status()).isEqualTo(200);assertThat(ownedGet.status()).isEqualTo(200);assertThat(resourcePost.status()).isEqualTo(201);
        for(var response:List.of(resourceGet,ownedGet,resourcePost))notCounted(response);
        for(UUID user:List.of(TestData.USER_A_ID,TestData.USER_B_ID,TestData.ADMIN_ID))assertThat(keys.count(user)).isZero();
    }
    static Stream<Arguments> sizes(){return Stream.of("fixed","unknown","raw-chunked").flatMap(mode->
            Stream.of(false,true).flatMap(authenticated->Stream.of(8192,8193).map(size->Arguments.of(mode,authenticated,size))));}
    @ParameterizedTest @MethodSource("sizes")
    void enforcesActualByteLimitBeforeAuthenticationAndRedis(String mode,boolean authenticated,int size) throws Exception {
        UUID resource=resource(1);String base=payload(resource), body=base+" ".repeat(size-base.getBytes(StandardCharsets.UTF_8).length);
        assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize(size);String before=snapshot();Reply response;
        if(mode.equals("raw-chunked")){
            String id="raw-"+SEQUENCE.incrementAndGet();
            String headers="POST /api/reservations HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nConnection: close\r\n"
                    +"Transfer-Encoding: chunked\r\nContent-Type: application/json\r\nIdempotency-Key: size\r\nX-Test-Request-Id: "+id+"\r\n"
                    +(authenticated?"Authorization: "+auth("USER_A")+"\r\n":"")+"\r\n";
            byte[] wire=(Integer.toHexString(size)+"\r\n"+body+"\r\n0\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            try{
                var raw=HttpClientTestSupport.sendRawHttp(port,headers,wire);
                var responseHeaders=new HashMap<String,List<String>>();raw.headers().forEach((k,v)->responseHeaders.put(k,List.of(v)));
                response=new Reply(raw.statusCode(),responseHeaders,raw.body(),awaitTrace(id));
            }catch(Exception failure){STOP.stop(failure);throw failure;}
        } else response=send("POST","/api/reservations",authenticated?auth("USER_A"):null,body,"size","application/json",mode.equals("unknown"),Map.of());
        if(mode.equals("fixed"))assertThat(response.trace().contentLength).isEqualTo(size);
        else {assertThat(response.trace().contentLength).isEqualTo(-1);assertThat(response.trace().transferEncoding).isEqualToIgnoringCase("chunked");}
        if(size>8192){problem(response,413,"PAYLOAD_TOO_LARGE");notCounted(response);assertThat(snapshot()).isEqualTo(before);}
        else if(!authenticated){problem(response,401,"UNAUTHENTICATED");notCounted(response);assertThat(snapshot()).isEqualTo(before);}
        else {assertThat(response.status()).isEqualTo(201);counted(response,true);}
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(size==8192 && authenticated?1:0);
        if(size>8192 || !authenticated){
            assertThat(response.trace().count("controller")).isZero();assertThat(response.trace().count("service")).isZero();
            assertThat(response.trace().count("tx-before")).isZero();
        }
    }
    static Stream<Arguments> encoded(){return Stream.of("/api/reservations/ID/cancel","/api/reservations/ID/%63ancel","/api/%72eservations/ID/cancel")
            .flatMap(path->Stream.of("empty","body","uuid").map(kind->Arguments.of(path,kind)));}
    @ParameterizedTest @MethodSource("encoded")
    void matchesDecodedCancelPathsOnceAndLimitsBeforeValidation(String template,String kind) throws Exception {
        UUID resource=resource(1), owned=seed(resource,TestData.USER_A_ID,"seed");
        String path=template.replace("ID",kind.equals("uuid")?"not-uuid":owned.toString());
        String before=snapshot();var response=write(path,kind.equals("body")?"{}":null,null);
        if(kind.equals("empty"))assertThat(response.status()).isEqualTo(200);
        else {problem(response,400,"INVALID_REQUEST");assertThat(snapshot()).isEqualTo(before);}
        counted(response,true);assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(1);
        for(int i=1;i<20;i++)limiter.check(TestData.USER_A_ID);
        before=snapshot();var denied=write(path,"{}",null);problem(denied,429,"RATE_LIMITED");counted(denied,false);
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(21);assertThat(snapshot()).isEqualTo(before);
    }

    @Test void replaysAndDuplicateCancelsConsumeQuotaWithoutChangingOriginalContracts() throws Exception {
        UUID resource=resource(2);var first=create(resource,"original");
        assertThat(first.status()).isEqualTo(201);assertThat(first.header("Idempotent-Replayed")).isEqualTo("false");counted(first,true);
        UUID reservation=UUID.fromString(JSON.readTree(first.body()).get("id").asString());
        var replay=create(resource,"original");
        assertThat(replay.status()).isEqualTo(201);counted(replay,true);
        assertThat(replay.header("Location")).isEqualTo(first.header("Location"));
        assertThat(replay.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(JSON.readTree(replay.body())).isEqualTo(JSON.readTree(first.body()));
        var cancelled=cancel(reservation);var duplicate=cancel(reservation);
        assertThat(cancelled.status()).isEqualTo(200);assertThat(duplicate.status()).isEqualTo(200);
        counted(cancelled,true);counted(duplicate,true);
        assertThat(JSON.readTree(duplicate.body())).isEqualTo(JSON.readTree(cancelled.body()));
        assertThat(JSON.readTree(cancelled.body()).get("cancelledAt").asString()).endsWith("Z");
        var afterCancel=create(resource,"original");counted(afterCancel,true);
        assertThat(afterCancel.status()).isEqualTo(201);assertThat(afterCancel.header("Location")).isEqualTo(first.header("Location"));
        assertThat(afterCancel.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(JSON.readTree(afterCancel.body())).isEqualTo(JSON.readTree(first.body()));
        assertThat(JSON.readTree(afterCancel.body()).get("status").asString()).isEqualTo("CONFIRMED");
        var current=send("GET","/api/reservations/"+reservation,auth("USER_A"),null,null,null,false,Map.of());
        assertThat(current.status()).isEqualTo(200);notCounted(current);
        assertThat(JSON.readTree(current.body())).isEqualTo(JSON.readTree(cancelled.body()));
        assertThat(JSON.readTree(current.body()).get("status").asString()).isEqualTo("CANCELLED");
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(5);
        for(int i=5;i<20;i++)limiter.check(TestData.USER_A_ID);
        String before=snapshot();
        for(var response:List.of(create(resource,"original"),cancel(reservation))){
            problem(response,429,"RATE_LIMITED");counted(response,false);
        }
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(22);assertThat(snapshot()).isEqualTo(before);
        new DatabaseAssertions(dataSource).assertInventoryConserved(resource);
    }
    @Test void concurrentFortyRealHttpRequestsAllowExactlyTwentyAndPreserveInventory() throws Exception {
        UUID resource=resource(40);
        var jobs=new ArrayList<java.util.concurrent.Callable<Reply>>();
        for(int i=0;i<40;i++){String key="parallel-"+i;jobs.add(()->create(resource,key));}
        var runner=new ConcurrencyTestSupport(STOP);
        var results=runner.run(jobs,java.time.Duration.ofSeconds(30));
        for(var result:results)if(result.failure()!=null)STOP.stop(result.failure());
        assertThat(runner.terminated()).isTrue();
        assertThat(results).hasSize(40).allSatisfy(result->assertThat(result.failure()).isNull());
        quiescent();closeHttp();
        long created=results.stream().filter(result->result.value().status()==201).count();
        long denied=results.stream().filter(result->result.value().status()==429).count();
        assertThat(created).isEqualTo(20);assertThat(denied).isEqualTo(20);
        for(var result:results){
            var response=result.value();counted(response,response.status()==201);
            if(response.status()==429)problem(response,429,"RATE_LIMITED");
            else {assertThat(response.trace().count("controller")).isEqualTo(1);
                assertThat(response.trace().count("service")).isEqualTo(1);
                assertThat(response.trace().count("tx-start")).isEqualTo(1);
                assertThat(response.trace().count("sql")).isEqualTo(1);}
        }
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(40);
        assertThat(keys.ttl(TestData.USER_A_ID)).isBetween(1L,60000L);
        var inventory=new DatabaseAssertions(dataSource).snapshot(List.of(resource)).get(resource);
        assertThat(inventory.available()).isEqualTo(20);assertThat(inventory.confirmedCount()).isEqualTo(20);
        assertThat(inventory.totalCount()).isEqualTo(20);
        System.out.println("US2 HTTP concurrent requests=40 workers=10 created="+created+" denied="+denied
                +" count="+keys.count(TestData.USER_A_ID)+" ttl="+keys.ttl(TestData.USER_A_ID)
                +" server="+IN_FLIGHT.get()+" executorTerminated="+runner.terminated()+" httpClosed="+(client==null)+" inventory="+inventory);
    }
    @org.springframework.beans.factory.annotation.Autowired org.springframework.security.web.FilterChainProxy securityChain;
    @org.springframework.beans.factory.annotation.Autowired jakarta.servlet.ServletContext servletContext;
    @org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext applicationContext;
    @Test void ownsFiltersOnlyInTheExplicitSecurityOrder() {
        var filters=securityChain.getFilterChains().getFirst().getFilters().stream().map(filter->filter.getClass().getSimpleName()).toList();
        assertThat(filters).containsSubsequence("RequestBodyLimitFilter","BasicAuthenticationFilter","AuthorizationFilter",
                "RateLimitFilter","CancelRequestValidationFilter");
        assertThat(filters.stream().filter("RateLimitFilter"::equals).count()).isEqualTo(1);
        assertThat(filters.stream().filter("CancelRequestValidationFilter"::equals).count()).isEqualTo(1);
        assertThat(applicationContext.getBeansOfType(RateLimitFilter.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(com.limitedbooking.backend.security.CancelRequestValidationFilter.class)).isEmpty();
        assertThat(servletContext.getFilterRegistrations().values()).noneSatisfy(registration->
                assertThat(registration.getClassName()).endsWith(".RateLimitFilter"));
    }
    @Test void downstreamInternalFailureRemainsFiveHundredAndErrorDispatchDoesNotCountTwice() throws Exception {
        UUID resource=resource(1);String before=snapshot();failService.set(true);
        try{
            var response=create(resource,"internal");
            assertThat(response.status()).isEqualTo(500);counted(response,true);
            assertThat(response.trace().count("error-dispatch")).isEqualTo(1);
            assertThat(response.trace().count("controller")).isEqualTo(1);
            assertThat(response.trace().count("service")).isEqualTo(1);
            assertThat(response.trace().count("tx-before")).isZero();
            assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(1);assertThat(snapshot()).isEqualTo(before);
            assertThat(response.body()).doesNotContain("시험 후속 내부 결함","IllegalStateException","stackTrace");
        }finally{failService.set(false);}
    }
    @Test void appliesLimitToUtf8BytesBeforeReadingJsonOrCredentials() throws Exception {
        resource(1);String before=snapshot();String body="가".repeat(2731);
        assertThat(body.length()).isEqualTo(2731);assertThat(body.getBytes(StandardCharsets.UTF_8)).hasSize(8193);
        for(String authorization:Arrays.asList(null,auth("USER_A"))){
            var response=send("POST","/api/reservations",authorization,body,"utf8","application/json",true,Map.of());
            problem(response,413,"PAYLOAD_TOO_LARGE");notCounted(response);
            assertThat(response.trace().count("controller")).isZero();assertThat(response.trace().count("tx-before")).isZero();
            assertThat(keys.count(TestData.USER_A_ID)).isZero();assertThat(snapshot()).isEqualTo(before);
        }
    }

}
