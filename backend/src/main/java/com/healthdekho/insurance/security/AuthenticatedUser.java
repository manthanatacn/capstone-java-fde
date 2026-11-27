package com.healthdekho.insurance.security;

import org.springframework.security.oauth2.jwt.Jwt;

import com.healthdekho.insurance.domain.Role;

/**
 * The caller of the current request, rebuilt from the bearer token.
 * Controllers receive it as a method parameter (see {@link AuthenticatedUserArgumentResolver}).
 */
public record AuthenticatedUser(Long id, String email, String fullName, Role role) {

    public static AuthenticatedUser from(Jwt jwt) {
        Number uid = jwt.getClaim(JwtTokenService.CLAIM_USER_ID);
        return new AuthenticatedUser(
                uid.longValue(),
                jwt.getSubject(),
                jwt.getClaimAsString(JwtTokenService.CLAIM_NAME),
                Role.valueOf(jwt.getClaimAsString(JwtTokenService.CLAIM_ROLE)));
    }

    public boolean isStaff() {
        return role == Role.STAFF;
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }
}
