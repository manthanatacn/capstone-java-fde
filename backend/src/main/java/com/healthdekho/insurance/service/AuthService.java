package com.healthdekho.insurance.service;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.healthdekho.insurance.domain.Role;
import com.healthdekho.insurance.dto.auth.LoginRequest;
import com.healthdekho.insurance.dto.auth.LoginResponse;
import com.healthdekho.insurance.dto.auth.UserProfileResponse;
import com.healthdekho.insurance.exception.ResourceNotFoundException;
import com.healthdekho.insurance.repository.CustomerRepository;
import com.healthdekho.insurance.repository.StaffUserRepository;
import com.healthdekho.insurance.security.AppUserPrincipal;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.security.JwtTokenService;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService tokenService;
    private final CustomerRepository customers;
    private final StaffUserRepository staff;

    public AuthService(AuthenticationManager authenticationManager, JwtTokenService tokenService,
                       CustomerRepository customers, StaffUserRepository staff) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.customers = customers;
        this.staff = staff;
    }

    /** Throws BadCredentialsException / DisabledException, mapped to 401 by the global handler. */
    public LoginResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email().trim(), request.password()));
        AppUserPrincipal principal = (AppUserPrincipal) auth.getPrincipal();
        JwtTokenService.IssuedToken token = tokenService.issue(principal);
        return new LoginResponse(token.value(), token.expiresAt(), profileOf(principal.id(), principal.role()));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentProfile(AuthenticatedUser user) {
        return profileOf(user.id(), user.role());
    }

    private UserProfileResponse profileOf(Long id, Role role) {
        return switch (role) {
            case CUSTOMER -> customers.findById(id)
                    .map(c -> new UserProfileResponse(c.getId(), c.getFullName(), c.getEmail(), Role.CUSTOMER,
                            c.getDateOfBirth(), c.getPhone()))
                    .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
            case STAFF -> staff.findById(id)
                    .map(s -> new UserProfileResponse(s.getId(), s.getFullName(), s.getEmail(), Role.STAFF,
                            null, null))
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        };
    }
}
