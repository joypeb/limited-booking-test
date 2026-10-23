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
