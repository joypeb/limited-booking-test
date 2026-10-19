package com.limitedbooking.backend.common;

import com.limitedbooking.backend.resource.ResourceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.type.LogicalType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final JsonMapper PROBLEM_MAPPER = JsonMapper.builder().build();

    public record ProblemResponse(
            String type,
            String title,
            int status,
            String detail,
            String instance,
            String code
    ) {}

    @Bean
    public JsonMapperBuilderCustomizer strictJsonMapperCustomizer() {
        return builder -> builder
                .enable(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES
                )
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .withCoercionConfig(LogicalType.Integer, coercion -> coercion
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail));
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            ServletRequestBindingException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ProblemResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return problemEntity(
                HttpStatus.BAD_REQUEST,
                "잘못된 요청입니다",
                "요청 형식 또는 입력 값이 유효하지 않습니다.",
                request,
                "INVALID_REQUEST"
        );
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return problemEntity(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "지원하지 않는 미디어 타입입니다",
                "Content-Type은 application/json이어야 합니다.",
                request,
                "UNSUPPORTED_MEDIA_TYPE"
        );
    }

    @ExceptionHandler(ResourceService.ResourceNotFoundException.class)
    public ResponseEntity<ProblemResponse> handleResourceNotFound(
            ResourceService.ResourceNotFoundException exception,
            HttpServletRequest request
    ) {
        return problemEntity(
                HttpStatus.NOT_FOUND,
                "예약 대상을 찾을 수 없습니다",
                "요청한 예약 대상이 존재하지 않습니다.",
                request,
                "NOT_FOUND"
        );
    }

    @ExceptionHandler({
            TransientDataAccessException.class,
            DataAccessResourceFailureException.class,
            QueryTimeoutException.class
    })
    public ResponseEntity<ProblemResponse> handleDependencyUnavailable(
            Exception exception,
            HttpServletRequest request
    ) {
        return problemEntity(
                HttpStatus.SERVICE_UNAVAILABLE,
                "일시적으로 요청을 처리할 수 없습니다",
                "저장소 연결 또는 응답 지연으로 요청을 처리할 수 없습니다.",
                request,
                "DEPENDENCY_UNAVAILABLE"
        );
    }

    @ExceptionHandler(com.limitedbooking.backend.reservation.ReservationWriter.SoldOutException.class)
    public ResponseEntity<ProblemResponse> handleSoldOut(Exception exception, HttpServletRequest request) {
        return problemEntity(HttpStatus.CONFLICT, "예약 가능한 수량이 없습니다", "대상은 현재 품절입니다.", request, "SOLD_OUT");
    }

    @ExceptionHandler(com.limitedbooking.backend.reservation.ReservationService.IdempotencyConflictException.class)
    public ResponseEntity<ProblemResponse> handleIdempotencyConflict(Exception exception, HttpServletRequest request) {
        return problemEntity(HttpStatus.CONFLICT, "생성 키가 충돌했습니다", "같은 생성 키가 다른 예약 대상에 사용됐습니다.", request, "IDEMPOTENCY_CONFLICT");
    }

    @ExceptionHandler(org.springframework.jdbc.UncategorizedSQLException.class)
    public ResponseEntity<ProblemResponse> handleUncategorizedSql(
            org.springframework.jdbc.UncategorizedSQLException exception, HttpServletRequest request) {
        // 현재 드라이버/Spring 조합에서 잠금 timeout은 일반 SQL 예외로 전달된다.
        if (!"55P03".equals(exception.getSQLException().getSQLState())) throw exception;
        return handleDependencyUnavailable(exception, request);
    }

    @ExceptionHandler(org.springframework.transaction.CannotCreateTransactionException.class)
    public ResponseEntity<ProblemResponse> handleTransactionStart(
            org.springframework.transaction.CannotCreateTransactionException exception, HttpServletRequest request) {
        var cause = exception.getCause();
        if (cause instanceof java.sql.SQLException sql
                && (sql instanceof java.sql.SQLTransientConnectionException
                || sql instanceof java.sql.SQLNonTransientConnectionException
                || (sql.getSQLState() != null && sql.getSQLState().startsWith("08")))) {
            return handleDependencyUnavailable(exception, request);
        }
        throw exception;
    }

    public static ProblemResponse problem(int status, String title, String detail, String instance, String code) {
        return new ProblemResponse(
                "about:blank",
                title,
                status,
                detail,
                instance == null || instance.isBlank() ? "/" : instance,
                code
        );
    }

    public static ResponseEntity<ProblemResponse> problemEntity(
            HttpStatus status,
            String title,
            String detail,
            HttpServletRequest request,
            String code
    ) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem(status.value(), title, detail, request.getRequestURI(), code));
    }

    public static void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String title,
            String detail,
            String code
    ) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        ProblemResponse body = problem(status.value(), title, detail, request.getRequestURI(), code);
        response.getWriter().write(PROBLEM_MAPPER.writeValueAsString(body));
        response.getWriter().flush();
    }
}
