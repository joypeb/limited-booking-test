package com.limitedbooking.backend.ratelimit;

import java.util.List;

/** Redis가 한 스크립트에서 반환한 count/PTTL만으로 판단한다. */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds) {
    public RateLimitDecision {
        if (allowed ? retryAfterSeconds != 0 : retryAfterSeconds < 1)
            throw new IllegalArgumentException("허용 여부와 대기 시간이 일치하지 않습니다.");
    }

    static RateLimitDecision fromRedis(List<?> result, long limit) {
        if (result == null || result.size() != 2
                || !(result.get(0) instanceof Long count) || !(result.get(1) instanceof Long ttl)
                || count < 1 || ttl < 0 || limit < 1)
            throw new IllegalStateException("Redis 제한 응답 불변식 위반");
        return count <= limit ? new RateLimitDecision(true, 0)
                : new RateLimitDecision(false, Math.max(1, ttl / 1000 + (ttl % 1000 == 0 ? 0 : 1)));
    }
}
