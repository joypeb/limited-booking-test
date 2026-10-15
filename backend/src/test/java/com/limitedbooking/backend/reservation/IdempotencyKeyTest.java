package com.limitedbooking.backend.reservation;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {
    // 키 변환, 길이 경계 제외, public 생성자의 검증 우회를 탐지한다.
    @ParameterizedTest
    @MethodSource("validKeys")
    void preservesValidKey(String raw) {
        assertThat(IdempotencyKey.parse(raw).value()).isEqualTo(raw);
        assertThat(new IdempotencyKey(raw).value()).isEqualTo(raw);
    }

    static Stream<String> validKeys() {
        return Stream.of("A", "a", "0", "-", "_", "Ab-1_9", "A".repeat(64));
    }

    @ParameterizedTest
    @MethodSource("invalidKeys")
    void rejectsInvalidKey(String raw) {
        assertThatThrownBy(() -> IdempotencyKey.parse(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<String> invalidKeys() {
        return Stream.of(null, "", "A".repeat(65));
    }

    // trim/부분 검색으로 끝 LF·CRLF나 내부 공백을 수용하는 결함을 탐지한다.
    @ParameterizedTest
    @MethodSource("whitespaceKeys")
    void rejectsWhitespaceAnywhere(String raw) {
        assertThatThrownBy(() -> IdempotencyKey.parse(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<String> whitespaceKeys() {
        return Stream.of(" ", "\t", "\n", " A", "A ", "A B",
                "\tA", "A\t", "A\tB", "\nA", "A\n", "A\nB", "A\r\n");
    }

    @ParameterizedTest
    @MethodSource("nonAsciiOrSeparators")
    void rejectsNonAsciiOrSeparators(String raw) {
        assertThatThrownBy(() -> IdempotencyKey.parse(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<String> nonAsciiOrSeparators() {
        return Stream.of("A한", "A😀", "A.B", "A/B", "A:B", "A\u00a0B", "AＡ", "A１");
    }

    @ParameterizedTest
    @MethodSource("allInvalidKeys")
    void rejectsInvalidDirectConstruction(String raw) {
        assertThatThrownBy(() -> new IdempotencyKey(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<String> allInvalidKeys() {
        return Stream.concat(Stream.concat(invalidKeys(), whitespaceKeys()), nonAsciiOrSeparators());
    }

    // 참조 비교·대소문자 무시 동등성 및 원문 변환을 탐지한다.
    @Test
    void preservesCaseSensitiveEquality() {
        var parsed = IdempotencyKey.parse("Ab-1");
        var same = new IdempotencyKey("Ab-1");
        var lower = IdempotencyKey.parse("ab-1");
        assertThat(parsed.value()).isEqualTo("Ab-1");
        assertThat(lower.value()).isEqualTo("ab-1");
        assertThat(parsed).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(parsed).isNotEqualTo(lower);
    }
}
