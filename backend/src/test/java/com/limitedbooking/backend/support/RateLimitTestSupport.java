package com.limitedbooking.backend.support;

import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.List;
import java.util.UUID;

/** 소유 시험 사용자의 키만 다룬다. 호출 전 in-flight 종료 보호는 부모 fixture의 책임이다. */
public final class RateLimitTestSupport {
    private final StringRedisTemplate redis;
    public RateLimitTestSupport(StringRedisTemplate redis) { this.redis = redis; }
    public static String key(UUID user) { return "reservation:write-limit:" + user; }
    public void reset() {
        redis.delete(List.of(key(TestData.ADMIN_ID), key(TestData.USER_A_ID), key(TestData.USER_B_ID)));
    }
    public long count(UUID user) {
        String value = redis.opsForValue().get(key(user));
        return value == null ? 0 : Long.parseLong(value);
    }
    public long ttl(UUID user) { return redis.getExpire(key(user), java.util.concurrent.TimeUnit.MILLISECONDS); }
}
