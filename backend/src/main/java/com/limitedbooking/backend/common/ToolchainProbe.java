package com.limitedbooking.backend.common;

/** 도구 최소 검증용 순수 프로브. 후속 단계에서 실제 규칙 테스트로 대체한다. */
public final class ToolchainProbe {
    private int value;
    public void store(int value) { this.value = value; }
    public int read() { return value; }
}
