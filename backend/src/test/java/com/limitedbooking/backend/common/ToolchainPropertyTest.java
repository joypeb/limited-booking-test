package com.limitedbooking.backend.common;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.atomic.AtomicInteger;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.integers;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("property")
class ToolchainPropertyTest {
    @ParameterizedTest @ValueSource(ints={0,100})
    void storesBoundaryInteger(int expected) {
        var probe=new ToolchainProbe();probe.store(expected);
        assertThat(probe.read()).isEqualTo(expected);
    }
    @Test void storesGeneratedIntegerAccordingToIndependentInputModel() {
        long seed=Long.parseLong(System.getProperty("QT_SEED","20261013"));
        var attempts=new AtomicInteger();
        System.out.println("property seed="+seed);
        // 도구는 중복 입력을 생략하므로 생성 시도와 실제 assertion 수를 구분한다.
        qt().withFixedSeed(seed).withExamples(1000).forAll(integers().between(0,100)).checkAssert(expected->{
            attempts.incrementAndGet();
            var probe=new ToolchainProbe();probe.store(expected);
            assertThat(probe.read()).isEqualTo(expected);
        });
        assertThat(attempts.get()).isGreaterThanOrEqualTo(100);
        System.out.println("property generated-attempts="+attempts.get()+" seed="+seed);
    }
}
