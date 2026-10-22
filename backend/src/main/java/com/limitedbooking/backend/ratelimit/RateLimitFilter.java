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
        var decision=limiter.check(user);
        if(!decision.allowed()) {
            response.setHeader("Retry-After",Long.toString(decision.retryAfterSeconds()));
            ApiExceptionHandler.writeProblem(request,response,HttpStatus.TOO_MANY_REQUESTS,
                    "요청 횟수를 초과했습니다","잠시 후 다시 요청하세요.","RATE_LIMITED");
            return;
        }
        chain.doFilter(request,response);
    }
}
