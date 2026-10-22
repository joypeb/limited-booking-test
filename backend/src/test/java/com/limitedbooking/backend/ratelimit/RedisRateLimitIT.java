package com.limitedbooking.backend.ratelimit;

import com.limitedbooking.backend.support.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class RedisRateLimitIT {
    static ContainerTestSupport containers;
    static AnnotationConfigApplicationContext context;
    static StringRedisTemplate redis;
    static final ConcurrencyTestSupport.StopState STOP = new ConcurrencyTestSupport.StopState();
    RateLimitTestSupport keys;
    RedisRateLimiter limiter;

    static AnnotationConfigApplicationContext context(Map<String,Object> overrides) {
        var ctx = new AnnotationConfigApplicationContext();
        try {
            new ConfigDataApplicationContextInitializer().initialize(ctx);
            var properties = new HashMap<String,Object>(overrides);
            properties.put("spring.data.redis.host",containers.redis.getHost());
            properties.put("spring.data.redis.port",containers.redis.getMappedPort(6379));
            ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-owned",properties));
            ctx.register(DataRedisAutoConfiguration.class, RedisRateLimiter.class);
            ctx.refresh();
            var factory=ctx.getBean(LettuceConnectionFactory.class);
            assertThat(factory.getClientConfiguration().getCommandTimeout()).isEqualTo(Duration.ofMillis(200));
            assertThat(factory.getClientConfiguration().getClientOptions().orElseThrow()
                    .getSocketOptions().getConnectTimeout()).isEqualTo(Duration.ofMillis(200));
            return ctx;
        } catch (RuntimeException | Error failure) {
            try { ctx.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    @BeforeAll static void start() throws Exception {
        containers = new ContainerTestSupport();
        context=context(Map.of());
        redis=context.getBean(StringRedisTemplate.class);
        assertThat(context.getBean(RateLimitProperties.class)).isEqualTo(new RateLimitProperties(20,Duration.ofSeconds(60)));
        System.out.println("US1 versions SDR="+StringRedisTemplate.class.getPackage().getImplementationVersion()
                +" Lettuce="+io.lettuce.core.RedisClient.class.getPackage().getImplementationVersion()
                +" Redis="+containers.redis.execInContainer("redis-server","--version").getStdout().trim());
    }
    @AfterAll static void stop() {
        Throwable failure=null;
        try { if(context!=null)context.close(); } catch(Throwable error) { failure=error; }
        try { if(containers!=null)containers.close(); }
        catch(Throwable error) { failure=ConcurrencyTestSupport.preserve(failure,error); }
        if(failure instanceof RuntimeException error)throw error;
        if(failure instanceof Error error)throw error;
        if(failure!=null)throw new IllegalStateException(failure);
    }
    @BeforeEach void setup() {
        STOP.check();
        keys = new RateLimitTestSupport(redis); keys.reset();
        limiter = context.getBean(RedisRateLimiter.class);
    }
    @Test void countsEveryRequestAndDoesNotExtendTtl() throws Exception {
        assertThat(limiter.check(TestData.USER_A_ID).allowed()).isTrue();
        long first = keys.ttl(TestData.USER_A_ID);
        assertThat(first).isBetween(1L,60000L);
        Thread.sleep(40);
        for(int i=2;i<=20;i++)assertThat(limiter.check(TestData.USER_A_ID)).isEqualTo(new RateLimitDecision(true,0));
        var denied=limiter.check(TestData.USER_A_ID);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfterSeconds()).isBetween(1L,60L);
        limiter.check(TestData.USER_A_ID);
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(22);
        long later=keys.ttl(TestData.USER_A_ID);
        assertThat(later).isBetween(1L,first-20);
        System.out.println("US1 default limit=20 windowMs=60000 count=22 firstTtl="+first+" laterTtl="+later
                +" deniedRetrySeconds="+denied.retryAfterSeconds());
    }
    @Test void isolatesAllAuthenticatedUsers() {
        for(int i=0;i<21;i++)limiter.check(TestData.USER_A_ID);
        for(var user:List.of(TestData.USER_B_ID,TestData.ADMIN_ID)) {
            assertThat(limiter.check(user).allowed()).isTrue();
            assertThat(keys.count(user)).isEqualTo(1);
        }
    }
    @Test void expiresOnlyFromFirstRequestWithThreePerSecond() throws Exception {
        try (var shortContext=context(Map.of("reservation.rate-limit.limit",3,"reservation.rate-limit.window","1000ms"))) {
            var shortLimiter = shortContext.getBean(RedisRateLimiter.class);
            assertThat(shortContext.getBean(RateLimitProperties.class)).isEqualTo(new RateLimitProperties(3,Duration.ofMillis(1000)));
            assertThat(shortLimiter.check(TestData.USER_A_ID).allowed()).isTrue();
            long first=keys.ttl(TestData.USER_A_ID);
            Thread.sleep(60);
            for(int i=0;i<2;i++)assertThat(shortLimiter.check(TestData.USER_A_ID).allowed()).isTrue();
            assertThat(shortLimiter.check(TestData.USER_A_ID).allowed()).isFalse();
            long later=keys.ttl(TestData.USER_A_ID);
            assertThat(later).isBetween(1L,first-30);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(Boolean.TRUE.equals(redis.hasKey(RateLimitTestSupport.key(TestData.USER_A_ID))) && System.nanoTime()<deadline)
                Thread.sleep(20);
            assertThat(redis.hasKey(RateLimitTestSupport.key(TestData.USER_A_ID))).isFalse();
            assertThat(shortLimiter.check(TestData.USER_A_ID).allowed()).isTrue();
            assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(1);
            assertThat(keys.ttl(TestData.USER_A_ID)).isBetween(1L,1000L);
            System.out.println("US1 expiry limit=3 windowMs=1000 firstTtl="+first+" renewedTtl="+keys.ttl(TestData.USER_A_ID));
        }
    }
    @Test void rejectsInvalidConfigurationDuringContextStartup() {
        for (var invalid : List.of(Map.<String,Object>of("reservation.rate-limit.limit",0),
                Map.<String,Object>of("reservation.rate-limit.window","0ms"),
                Map.<String,Object>of("reservation.rate-limit.window","1ns")))
            assertThatThrownBy(() -> context(invalid)).hasRootCauseInstanceOf(IllegalArgumentException.class);
    }
    @Test void concurrentFirstRequestsAllowExactlyTwenty() throws Exception {
        var runner=new ConcurrencyTestSupport(STOP);
        var jobs=new ArrayList<Callable<RateLimitDecision>>();
        for(int i=0;i<40;i++)jobs.add(()->limiter.check(TestData.USER_A_ID));
        var results=runner.run(jobs,Duration.ofSeconds(30));
        for(var result:results)if(result.failure()!=null)STOP.stop(result.failure());
        assertThat(runner.terminated()).isTrue();
        assertThat(results).hasSize(40).allSatisfy(result->assertThat(result.failure()).isNull());
        long allowed=results.stream().filter(result->result.value().allowed()).count();
        assertThat(allowed).isEqualTo(20);
        assertThat(results.stream().filter(result->!result.value().allowed()).count()).isEqualTo(20);
        assertThat(keys.count(TestData.USER_A_ID)).isEqualTo(40);
        assertThat(keys.ttl(TestData.USER_A_ID)).isBetween(1L,60000L);
        System.out.println("US1 concurrent requests=40 workers=10 allowed="+allowed+" denied=20 count="
                +keys.count(TestData.USER_A_ID)+" ttl="+keys.ttl(TestData.USER_A_ID)+" terminated="+runner.terminated());
    }
}
