package com.limitedbooking.backend.ratelimit;

import com.limitedbooking.backend.common.ApiExceptionHandler;
import com.limitedbooking.backend.security.LocalIdentityResolver;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Security 체인만 소유한다. 인가 후·입력 검증 전에 인증 UUID로 한 번 집계한다. */
public class RateLimitFilter extends OncePerRequestFilter {
    private static final PathPatternRequestMatcher CREATE=PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST,"/api/reservations");
    private static final PathPatternRequestMatcher CANCEL=PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST,"/api/reservations/{id}/cancel");
    private final RedisRateLimiter limiter;
    private final LocalIdentityResolver identity;

    public RateLimitFilter(RedisRateLimiter limiter,LocalIdentityResolver identity) {
        this.limiter=limiter;this.identity=identity;
    }
    @Override @NullMarked
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !CREATE.matches(request) && !CANCEL.matches(request);
    }
    @Override @NullMarked
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var user=identity.userId(SecurityContextHolder.getContext().getAuthentication());
        RateLimitDecision decision;
        try { decision=limiter.check(user); }
        catch(RuntimeException failure) {
            if(!dependencyFailure(failure))throw failure;
            ApiExceptionHandler.writeProblem(request,response,HttpStatus.SERVICE_UNAVAILABLE,
                    "의존 서비스를 사용할 수 없습니다","잠시 후 다시 요청하세요.","DEPENDENCY_UNAVAILABLE");
            return;
        }
        if(!decision.allowed()) {
            response.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));
            ApiExceptionHandler.writeProblem(request,response,HttpStatus.TOO_MANY_REQUESTS,
                    "요청 횟수를 초과했습니다","잠시 후 다시 요청하세요.","RATE_LIMITED");
            return;
        }
        chain.doFilter(request,response);
    }
    /** 래퍼 이름만으로 판단하지 않는다. 인터럽트는 복원하고 원래 예외를 전파한다. */
    private static boolean dependencyFailure(Throwable failure) {
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable,Boolean>());
        Throwable root=failure;
        boolean implementationFailure=false;
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) {
            if(!seen.add(cause))return false;
            if(cause instanceof InterruptedException || cause instanceof io.lettuce.core.RedisCommandInterruptedException) {
                Thread.currentThread().interrupt();return false;
            }
            if(cause instanceof org.springframework.data.redis.serializer.SerializationException)implementationFailure=true;
            if(cause instanceof io.lettuce.core.RedisCommandExecutionException command
                    && !dependencyCommand(command))implementationFailure=true;
            root=cause;
        }
        if(Thread.currentThread().isInterrupted() || implementationFailure)return false;
        if(root instanceof io.lettuce.core.RedisCommandExecutionException command)return dependencyCommand(command);
        return root instanceof io.lettuce.core.RedisCommandTimeoutException
                || root instanceof java.util.concurrent.TimeoutException
                || root instanceof io.lettuce.core.RedisConnectionException
                || root instanceof java.net.SocketException
                || root instanceof java.net.SocketTimeoutException
                || root instanceof java.net.UnknownHostException
                || root instanceof java.nio.channels.ClosedChannelException
                || root instanceof io.netty.channel.ChannelException;
    }

    private static boolean dependencyCommand(io.lettuce.core.RedisCommandExecutionException command) {
        String message=command.getMessage();
        return message!=null && (message.startsWith("WRONGTYPE ")
                || message.startsWith("OOM command not allowed when used memory > 'maxmemory'."));
    }

}
