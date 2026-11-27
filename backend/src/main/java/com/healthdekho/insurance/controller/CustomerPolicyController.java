package com.healthdekho.insurance.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.healthdekho.insurance.dto.policy.PolicySummaryResponse;
import com.healthdekho.insurance.security.AuthenticatedUser;
import com.healthdekho.insurance.service.PolicyRegistrationService;

@RestController
@RequestMapping("/api/customers/me/policies")
public class CustomerPolicyController {

    private final PolicyRegistrationService registrationService;

    public CustomerPolicyController(PolicyRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping
    public List<PolicySummaryResponse> myPolicies(AuthenticatedUser user) {
        return registrationService.myPolicies(user);
    }
}
