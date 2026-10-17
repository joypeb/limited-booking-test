package com.limitedbooking.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
