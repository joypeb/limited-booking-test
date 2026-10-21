package com.limitedbooking.backend.common;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final MockHttpServletRequest request =
            new MockHttpServletRequest("POST", "/api/reservations");

    @Test
    void preservesOriginalExceptionWhenSqlExceptionIsAbsent() {
        // Spring의 nullable 반환 계약을 재현한다. 생성자는 null 원인을 허용하지 않는다.
        var exception = new UncategorizedSQLException("예약", null, new SQLException()) {
            @Override
            public @Nullable SQLException getSQLException() {
                return null;
            }
        };

        assertThatThrownBy(() -> handler.handleUncategorizedSql(exception, request))
                .isSameAs(exception);
    }

    @Test
    void mapsLockTimeoutToDependencyUnavailableWithoutExposingSql() {
        var exception = new UncategorizedSQLException("예약", "비공개 SQL",
                new SQLException("비공개 상세", "55P03"));

        var response = handler.handleUncategorizedSql(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(response.getBody().instance()).isEqualTo("/api/reservations");
        assertThat(response.getBody().detail()).doesNotContain("비공개");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"23505", "23503", "XX000"})
    void preservesUnclassifiedSqlExceptions(@Nullable String state) {
        var exception = new UncategorizedSQLException("예약", null, new SQLException("실패", state));

        assertThatThrownBy(() -> handler.handleUncategorizedSql(exception, request))
                .isSameAs(exception);
    }
}
