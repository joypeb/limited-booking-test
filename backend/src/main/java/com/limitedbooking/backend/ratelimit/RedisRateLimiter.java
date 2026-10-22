package com.limitedbooking.backend.ratelimit;

import java.util.UUID;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class RedisRateLimiter {
    private static final DefaultRedisScript<List> SCRIPT = script();
    private static DefaultRedisScript<List> script() {
        var script = new DefaultRedisScript<List>();
        script.setLocation(new ClassPathResource("redis/rate-limit.lua"));
        script.setResultType(List.class);
        return script;
    }
    private final StringRedisTemplate redis;
    private final RateLimitProperties properties;
    public RedisRateLimiter(StringRedisTemplate redis, RateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }
    public RateLimitDecision check(UUID userId) {
        Objects.requireNonNull(userId, "인증 UUID");
        var result = redis.execute(SCRIPT, List.of("reservation:write-limit:" + userId),
                Long.toString(properties.window().toMillis()));
        return RateLimitDecision.fromRedis(result, properties.limit());
    }
}
