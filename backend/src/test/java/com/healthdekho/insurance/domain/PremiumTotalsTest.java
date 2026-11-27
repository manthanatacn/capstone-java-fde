package com.healthdekho.insurance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PremiumTotalsTest {

    @Test
    void payableStaysTheContractTotalAndPenaltiesAreAddedToWhatRemains() {
        // Meera's 3003 after paying the overdue installment: 7,500 on time, then 7,500 + 750 penalty
        PremiumTotals totals = PremiumTotals.of(new BigDecimal("300000.00"), new BigDecimal("750"),
                new BigDecimal("15750"));

        assertThat(totals.totalPremiumPayable()).isEqualTo(new BigDecimal("300000.00"));
        assertThat(totals.latePenalties()).isEqualTo(new BigDecimal("750.00"));
        assertThat(totals.totalPremiumPaid()).isEqualTo(new BigDecimal("15750.00"));
        assertThat(totals.remainingPremium()).isEqualTo(new BigDecimal("285000.00"));
    }

    @Test
    void withoutPenaltiesPayableIsTheContractTotal() {
        PremiumTotals totals = PremiumTotals.of(new BigDecimal("18000.00"), BigDecimal.ZERO, new BigDecimal("18000.00"));

        assertThat(totals.totalPremiumPayable()).isEqualTo(new BigDecimal("18000.00"));
        assertThat(totals.remainingPremium()).isEqualTo(new BigDecimal("0.00"));
    }
}
