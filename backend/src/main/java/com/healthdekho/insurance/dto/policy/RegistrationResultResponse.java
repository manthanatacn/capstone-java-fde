package com.healthdekho.insurance.dto.policy;

public record RegistrationResultResponse(String policyNumber, String message, PolicySummaryResponse policy) {
}
