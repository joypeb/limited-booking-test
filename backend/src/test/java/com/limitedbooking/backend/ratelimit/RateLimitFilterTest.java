package com.limitedbooking.backend.ratelimit;

import com.limitedbooking.backend.security.LocalIdentityResolver;
import io.lettuce.core.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.*;
import org.springframework.data.redis.*;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.mock.web.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {
    final RedisRateLimiter limiter=mock(RedisRateLimiter.class);
    final LocalIdentityResolver identity=mock(LocalIdentityResolver.class);
    final RateLimitFilter filter=new RateLimitFilter(limiter,identity);
    final MockHttpServletRequest request=new MockHttpServletRequest("POST","/api/reservations");
    final MockHttpServletResponse response=new MockHttpServletResponse();
    @BeforeEach void identity(){when(identity.userId(any())).thenReturn(UUID.randomUUID());}
    static Stream<RuntimeException> dependencies(){return Stream.of(
            new RedisConnectionFailureException("connect",new RedisConnectionException("refused",new ConnectException("refused"))),
            new RedisConnectionFailureException("closed",new RedisConnectionException("closed")),
            new RedisSystemException("observed closed transport",new RedisException("channel",new SocketException("Connection reset"))),
            new QueryTimeoutException("timeout",new RedisCommandTimeoutException("200ms")),
            new RedisSystemException("timeout",new java.util.concurrent.TimeoutException("redis future")),
            new RedisSystemException("OOM",new RedisCommandExecutionException("OOM command not allowed when used memory > 'maxmemory'.")),
            new RedisSystemException("script OOM",new RedisCommandExecutionException("OOM command not allowed when used memory > 'maxmemory'. script: 218b0e0a17530b3260b642d96184f36dbbfacc98, on @user_script:1.")),
            new RedisSystemException("WRONGTYPE",new RedisCommandExecutionException("WRONGTYPE Operation against a key holding the wrong kind of value")));}
    @ParameterizedTest @MethodSource("dependencies")
    void translatesOnlyVerifiedDependencyFailures(RuntimeException failure) throws Exception {
        when(limiter.check(any())).thenThrow(failure);var entered=new AtomicInteger();
        filter.doFilterInternal(request,response,(req,res)->entered.incrementAndGet());
        assertThat(response.getStatus()).isEqualTo(503);assertThat(entered.get()).isZero();
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"DEPENDENCY_UNAVAILABLE\"")
                .doesNotContain("ConnectException","200ms","redis future","WRONGTYPE");
    }
    static Stream<RuntimeException> defects(){return Stream.of(
            new RedisSystemException("script",new RedisCommandExecutionException("ERR Error compiling script")),
            new RedisSystemException("command",new RedisCommandExecutionException("ERR unknown command 'BROKEN'")),
            new RedisConnectionFailureException("auth",new RedisConnectionException("auth",new RedisCommandExecutionException("WRONGPASS invalid credentials"))),
            new RedisConnectionFailureException("config",new IllegalArgumentException("bad Redis URI")),
            new RedisSystemException("wrapper",new IllegalStateException("internal")),
            new RedisSystemException("unknown OOM",new RedisCommandExecutionException("OOM programmer error")),
            new SerializationException("serializer owns OOM",new RedisCommandExecutionException("OOM command not allowed when used memory > 'maxmemory'.")),
            new SerializationException("serialization"),new SerializationException("serializer owns the failure",new ConnectException("nested transport")),new DataAccessResourceFailureException("unclassified"),
            new IllegalStateException("broken Lua response"),new RuntimeException("internal"));}
    @ParameterizedTest @MethodSource("defects")
    void propagatesUnclassifiedOrImplementationFailures(RuntimeException failure){
        when(limiter.check(any())).thenThrow(failure);
        assertThatThrownBy(()->filter.doFilterInternal(request,response,(req,res)->fail("후속 진입 금지"))).isSameAs(failure);
        assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getContentAsByteArray()).isEmpty();
    }
    @Test void doesNotCatchFailuresFromTheDownstreamChain(){
        when(limiter.check(any())).thenReturn(new RateLimitDecision(true,0));
        var failure=new RedisConnectionFailureException("후속체인의 오류",new RedisConnectionException("closed"));
        assertThatThrownBy(()->filter.doFilterInternal(request,response,(req,res)->{throw failure;})).isSameAs(failure);
        assertThat(response.getStatus()).isEqualTo(200);
    }
    @Test void restoresInterruptedStatusAndPropagatesOriginalFailure(){
        var failure=new RedisSystemException("interrupted",new RedisCommandInterruptedException(new InterruptedException("stop")));
        when(limiter.check(any())).thenThrow(failure);
        try{
            assertThat(Thread.currentThread().isInterrupted()).isFalse();
            assertThatThrownBy(()->filter.doFilterInternal(request,response,(req,res)->fail("후속 진입 금지"))).isSameAs(failure);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();assertThat(response.getStatus()).isEqualTo(200);
        }finally{Thread.interrupted();}
    }
}
