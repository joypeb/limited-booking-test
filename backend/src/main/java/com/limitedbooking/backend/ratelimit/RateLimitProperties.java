package com.limitedbooking.backend.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("reservation.rate-limit")
public record RateLimitProperties(@DefaultValue("20") long limit, @DefaultValue("60000ms") Duration window) {
    public RateLimitProperties {
        if (limit < 1 || window == null || window.isNegative() || window.isZero())
            throw new IllegalArgumentException("제한 횟수와 창은 양수여야 합니다.");
        try {
            if (!Duration.ofMillis(window.toMillis()).equals(window))
                throw new IllegalArgumentException("창은 정수 밀리초로 표현해야 합니다.");
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("창이 밀리초 범위를 벗어났습니다.", overflow);
        }
    }
}
