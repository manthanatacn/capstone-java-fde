package com.healthdekho.insurance.dto.search;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumPaymentStatus;

/** The FR-5 B columns, plus latePenalties so the UI can explain a payable above the contract total. */
public record PolicySearchResult(
        String policyNumber,
        String customerName,
        LocalDate dateOfBirth,
        String policyName,
        PolicyType policyType,
        LocalDate startDate,
        LocalDate maturityDate,
        BigDecimal totalPremiumPayable,
        PremiumPaymentStatus premiumPaymentStatus,
        BigDecimal totalPremiumPaid,
        BigDecimal remainingPremium,
        BigDecimal latePenalties) {
}
