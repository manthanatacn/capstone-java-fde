package com.healthdekho.insurance.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.healthdekho.insurance.dto.policy.RegisterPolicyRequest;
import com.healthdekho.insurance.dto.policy.RegistrationPreviewResponse;
import com.healthdekho.insurance.dto.policy.RegistrationResultResponse;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.service.PolicyRegistrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/policies")
public class PolicyRegistrationController {

    private final PolicyRegistrationService registrationService;

    public PolicyRegistrationController(PolicyRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    /** startDate defaults to today when omitted. */
    @GetMapping("/registration-preview")
    public RegistrationPreviewResponse preview(
            AuthenticatedUser user,
            @RequestParam String productCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return registrationService.preview(user, productCode, startDate);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResultResponse register(AuthenticatedUser user,
                                               @Valid @RequestBody RegisterPolicyRequest request) {
        return registrationService.register(user, request);
    }
}
