package com.healthdekho.insurance.domain;

import java.math.BigDecimal;

/**
 * Premium totals of one policy as FR-4 A and FR-5 B show them. Total premium payable is the contract
 * total the PDF defines (premium × installments); late penalties are reported on their own, so
 * remaining = payable + penalties − paid (PRD A-17).
 */
public record PremiumTotals(BigDecimal totalPremiumPayable, BigDecimal latePenalties, BigDecimal totalPremiumPaid,
                            BigDecimal remainingPremium) {

    public static PremiumTotals of(BigDecimal contractPremium, BigDecimal latePenalties, BigDecimal paid) {
        BigDecimal remaining = contractPremium.add(latePenalties).subtract(paid);
        return new PremiumTotals(Money.normalize(contractPremium), Money.normalize(latePenalties),
                Money.normalize(paid), Money.normalize(remaining));
    }
}
