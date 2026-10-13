package com.limitedbooking.backend.common;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class ToolchainSmokeTest {
    // 저장 무시 또는 조회값 변경을 탐지한다. 업무 capacity 규칙이 아니다.
    @ParameterizedTest
    @ValueSource(ints = {0, 37, 100})
    void readsTheStoredInteger(int expected) {
        var probe = new ToolchainProbe();
        probe.store(expected);
        assertThat(probe.read()).isEqualTo(expected);
    }
}
