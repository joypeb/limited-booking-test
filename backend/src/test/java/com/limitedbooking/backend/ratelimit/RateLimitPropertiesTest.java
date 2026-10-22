package com.limitedbooking.backend.ratelimit;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class RateLimitPropertiesTest {
    @Test void bindsDefaults() {
        var value = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("reservation.rate-limit", RateLimitProperties.class);
        assertThat(value.limit()).isEqualTo(20);
        assertThat(value.window()).isEqualTo(Duration.ofMillis(60000));
    }
    @Test void rejectsNonpositiveOrUnrepresentableWindows() {
        for (Duration duration : new Duration[]{Duration.ZERO, Duration.ofMillis(-1),Duration.ofNanos(1),
                Duration.ofNanos(1000001),Duration.ofSeconds(Long.MAX_VALUE)})
            assertThatThrownBy(() -> new RateLimitProperties(20,duration)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RateLimitProperties(20,null)).isInstanceOf(IllegalArgumentException.class);
        for (long limit : new long[]{0,-1})
            assertThatThrownBy(() -> new RateLimitProperties(limit,Duration.ofSeconds(60))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void acceptsPositiveMillisecondBoundaries() {
        for (long millis : new long[]{1,1000,60000,Long.MAX_VALUE})
            assertThat(new RateLimitProperties(Long.MAX_VALUE,Duration.ofMillis(millis)).window().toMillis()).isEqualTo(millis);
    }
}
