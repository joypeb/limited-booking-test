package com.limitedbooking.backend.resource;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceRulesTest {
    // 하한 0과 상한 1000의 제외, 범위 밖 수량의 무검증 수용을 탐지한다.
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 1000})
    void acceptsCapacityBoundary(int capacity) {
        assertThatCode(() -> ResourceRules.validateCapacity(capacity)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1001})
    void rejectsCapacityOutsideRange(int capacity) {
        assertThatThrownBy(() -> ResourceRules.validateCapacity(capacity))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // 정리하지 않은 원문 반환과 strip 뒤 빈 값의 수용을 탐지한다.
    @ParameterizedTest
    @MethodSource("normalNames")
    void normalizesName(String input, String expected) {
        assertThat(ResourceRules.normalizeName(input)).isEqualTo(expected);
    }

    static Stream<Arguments> normalNames() {
        return Stream.of(
                Arguments.of("  예약 대상  ", "예약 대상"),
                Arguments.of("\t\n예약 대상\n\t", "예약 대상"),
                Arguments.of(" 예  약 ", "예  약"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n", " \t\n "})
    void rejectsInvalidName(String name) {
        assertThatThrownBy(() -> ResourceRules.normalizeName(name))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // UTF-16 길이 판정, strip 전 길이 판정, 초과 이름 절단을 탐지한다.
    @ParameterizedTest
    @MethodSource("codePointNames")
    void countsCodePoints(String input, String expected) {
        assertThat(ResourceRules.normalizeName(input)).isEqualTo(expected);
    }

    static Stream<Arguments> codePointNames() {
        return Stream.of(
                Arguments.of("가", "가"),
                Arguments.of("가".repeat(100), "가".repeat(100)),
                Arguments.of("😀".repeat(100), "😀".repeat(100)),
                Arguments.of("가".repeat(99) + "😀", "가".repeat(99) + "😀"),
                Arguments.of(" " + "가".repeat(100) + " ", "가".repeat(100)),
                Arguments.of(" " + "😀".repeat(100) + " ", "😀".repeat(100)));
    }

    @ParameterizedTest
    @MethodSource("tooLongNames")
    void rejectsNamesBeyondCodePointLimit(String name) {
        assertThatThrownBy(() -> ResourceRules.normalizeName(name))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<String> tooLongNames() {
        return Stream.of("가".repeat(101), "😀".repeat(101),
                "\u00a0".repeat(101), "\u2007".repeat(101), "\u202f".repeat(101));
    }

    // NBSP 계열의 제거·금지 및 길이 제외를 탐지한다.
    @ParameterizedTest
    @MethodSource("nonBreakingNames")
    void preservesNonBreakingSpaces(String name) {
        assertThat(ResourceRules.normalizeName(name)).isEqualTo(name);
    }

    static Stream<String> nonBreakingNames() {
        return Stream.of("\u00a0", "\u2007", "\u202f",
                "\u00a0이름\u00a0", "\u2007이름\u2007", "\u202f이름\u202f",
                "\u00a0".repeat(100), "\u2007".repeat(100), "\u202f".repeat(100));
    }

    @ParameterizedTest
    @MethodSource("acceptedNames")
    void normalizesAgainToSameValue(String input, String expected) {
        String normalized = ResourceRules.normalizeName(input);
        assertThat(normalized).isEqualTo(expected);
        assertThat(ResourceRules.normalizeName(normalized)).isEqualTo(expected);
    }

    static Stream<Arguments> acceptedNames() {
        return Stream.concat(Stream.concat(normalNames(), codePointNames()),
                nonBreakingNames().map(name -> Arguments.of(name, name)));
    }
}
