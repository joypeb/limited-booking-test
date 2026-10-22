package com.limitedbooking.backend.security;

import com.limitedbooking.backend.common.ApiExceptionHandler;
import com.limitedbooking.backend.common.RequestBodyLimitFilter;
import com.limitedbooking.backend.ratelimit.RateLimitFilter;
import com.limitedbooking.backend.ratelimit.RedisRateLimiter;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
public class SecurityConfig {
    public static final UUID ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID USER_A_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final UUID USER_B_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    public static final String BASIC_REALM = "Basic realm=\"reservation-local\"";

    @NullMarked
    public record LocalUserPrincipal(
            UUID userId,
            String username,
            String passwordHash,
            List<GrantedAuthority> authorities
    ) implements UserDetails {
        public LocalUserPrincipal {
            authorities = List.copyOf(authorities);
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return passwordHash;
        }

        @Override
        public String getUsername() {
            return username;
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder,
            @Value("${reservation.security.admin-password:${ADMIN_PASSWORD}}") String adminPassword,
            @Value("${reservation.security.user-a-password:${USER_A_PASSWORD}}") String userAPassword,
            @Value("${reservation.security.user-b-password:${USER_B_PASSWORD}}") String userBPassword
    ) {
        requireNonBlank(adminPassword, "ADMIN_PASSWORD");
        requireNonBlank(userAPassword, "USER_A_PASSWORD");
        requireNonBlank(userBPassword, "USER_B_PASSWORD");

        Map<String, LocalUserPrincipal> users = Map.of(
                "ADMIN", new LocalUserPrincipal(
                        ADMIN_ID,
                        "ADMIN",
                        passwordEncoder.encode(adminPassword),
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                ),
                "USER_A", new LocalUserPrincipal(
                        USER_A_ID,
                        "USER_A",
                        passwordEncoder.encode(userAPassword),
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                ),
                "USER_B", new LocalUserPrincipal(
                        USER_B_ID,
                        "USER_B",
                        passwordEncoder.encode(userBPassword),
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        return username -> {
            LocalUserPrincipal principal = users.get(username);
            if (principal == null) {
                throw new UsernameNotFoundException("로컬 인증 계정을 찾을 수 없습니다.");
            }
            return principal;
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RequestBodyLimitFilter requestBodyLimitFilter,
            RedisRateLimiter limiter,
            LocalIdentityResolver identity
    ) throws Exception {
        AuthenticationEntryPoint authenticationEntryPoint = (request, response, authException) -> {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BASIC_REALM);
            ApiExceptionHandler.writeProblem(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "인증이 필요합니다",
                    "유효한 Basic 인증 자격 증명이 필요합니다.",
                    "UNAUTHENTICATED"
            );
        };

        AccessDeniedHandler accessDeniedHandler = (request, response, accessDeniedException) ->
                ApiExceptionHandler.writeProblem(
                        request,
                        response,
                        HttpStatus.FORBIDDEN,
                        "접근 권한이 없습니다",
                        "요청한 작업을 수행할 권한이 없습니다.",
                        "FORBIDDEN"
                );

        // 서블릿 Filter Bean으로 등록하지 않고 Security 체인에서만 소유해 중복 집계를 막는다.
        var rateLimitFilter = new RateLimitFilter(limiter, identity);
        var cancelRequestValidationFilter = new CancelRequestValidationFilter();

        // 본 서비스는 localhost(127.0.0.1) 전용 비브라우저 HTTP API를 전제로 서버 세션을 생성하지 않는
        // 무상태(Stateless) Basic 인증을 사용한다. 쿠키·세션 기반 인증을 사용하지 않으므로 CSRF 보호를
        // 비활성화하며, 와일드카드(*) CORS 설정은 추가하지 않는다. 브라우저 클라이언트에서 호출하거나
        // 외부 네트워크에 공개할 때는 TLS·CSRF·인증 방식을 반드시 재검토해야 한다.
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(requestBodyLimitFilter, BasicAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, AuthorizationFilter.class)
                .addFilterAfter(cancelRequestValidationFilter, RateLimitFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/resources").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );

        return http.build();
    }

    private static void requireNonBlank(String value, String keyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(keyName + " 환경 변수가 비어 있습니다.");
        }
    }
}
