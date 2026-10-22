package com.limitedbooking.backend.security;

import com.limitedbooking.backend.common.ApiExceptionHandler;
import com.limitedbooking.backend.common.RequestBodyLimitFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

/** 요청 제한 뒤에만 실행하며 MVC와 같은 디코딩 경로를 사용한다. */
public class CancelRequestValidationFilter extends OncePerRequestFilter {
    private static final PathPatternRequestMatcher CANCEL=PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST,"/api/reservations/{id}/cancel");
    @Override @NullMarked
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var match=CANCEL.matcher(request);
        if(match.isMatch()) {
            if(RequestBodyLimitFilter.getCachedBody(request).length>0) {
                invalid(request,response,"예약 취소 요청 본문은 비어 있어야 합니다.");return;
            }
            try { UUID.fromString(match.getVariables().get("id")); }
            catch(IllegalArgumentException invalidUuid) {
                invalid(request,response,"예약 식별자 형식이 올바르지 않습니다.");return;
            }
        }
        chain.doFilter(request,response);
    }
    private static void invalid(HttpServletRequest request,HttpServletResponse response,String detail) throws IOException {
        ApiExceptionHandler.writeProblem(request,response,HttpStatus.BAD_REQUEST,"잘못된 요청입니다",detail,"INVALID_REQUEST");
    }
}
