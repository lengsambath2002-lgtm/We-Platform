package com.businessplatform.common.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.businessplatform.common.error.BusinessException;
import com.businessplatform.common.error.ErrorCode;

/** The signed-in user's id, from the access token's subject. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static UUID id() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
