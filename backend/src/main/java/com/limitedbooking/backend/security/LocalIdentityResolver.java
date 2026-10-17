package com.limitedbooking.backend.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LocalIdentityResolver {
    public UUID userId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityConfig.LocalUserPrincipal principal)
                || principal.userId() == null) {
            throw new InsufficientAuthenticationException("인증된 로컬 사용자 식별자가 없습니다.");
        }
        return principal.userId();
    }
}
