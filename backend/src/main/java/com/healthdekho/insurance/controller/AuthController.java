package com.healthdekho.insurance.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healthdekho.insurance.dto.auth.LoginRequest;
import com.healthdekho.insurance.dto.auth.LoginResponse;
import com.healthdekho.insurance.dto.auth.UserProfileResponse;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserProfileResponse me(AuthenticatedUser user) {
        return authService.currentProfile(user);
    }
}
