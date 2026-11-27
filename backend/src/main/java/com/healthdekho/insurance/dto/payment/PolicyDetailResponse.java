package com.healthdekho.insurance.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.healthdekho.insurance.domain.PolicyStatus;
import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;
import com.healthdekho.insurance.domain.PremiumPaymentStatus;

/** fullyPaidOn is null until the last installment is paid. */
public record PolicyDetailResponse(
        String policyNumber,
        String customerName,
        LocalDate customerDateOfBirth,
        String productCode,
        String policyName,
        PolicyType policyType,
        int termMonths,
        LocalDate startDate,
        LocalDate maturityDate,
        PremiumFrequency premiumFrequency,
        BigDecimal premiumAmount,
        BigDecimal sumInsured,
        BigDecimal totalPremiumPayable,
        BigDecimal latePenalties,
        BigDecimal totalPremiumPaid,
        BigDecimal remainingPremium,
        int paidInstallments,
        int totalInstallments,
        PolicyStatus status,
        PremiumPaymentStatus premiumPaymentStatus,
        LocalDate fullyPaidOn) {
}
