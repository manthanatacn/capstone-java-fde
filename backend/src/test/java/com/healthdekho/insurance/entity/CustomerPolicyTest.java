package com.healthdekho.insurance.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.healthdekho.insurance.domain.PolicyStatus;
import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;

class CustomerPolicyTest {

    private static final LocalDate START = LocalDate.of(2026, 11, 20);
    private static final LocalDate MATURITY = LocalDate.of(2027, 11, 19);

    private final CustomerPolicy policy = new CustomerPolicy("HDP-2026-0002",
            new Customer("meera.iyer@example.com", "hash", "Meera Iyer", LocalDate.of(1985, 9, 22), "9876501002"),
            new InsuranceProduct("HD-HEALTH-001", "HealthDekho Family Health", PolicyType.HEALTH, null, 12,
                    PremiumFrequency.YEARLY, new BigDecimal("18000.00"), new BigDecimal("1000000.00"), 18, 65),
            START, MATURITY, new BigDecimal("18000.00"));

    @Test
    void policyIsActiveUpToAndIncludingItsMaturityDate() {
        assertThat(policy.effectiveStatus(START)).isEqualTo(PolicyStatus.ACTIVE);
        assertThat(policy.effectiveStatus(MATURITY)).isEqualTo(PolicyStatus.ACTIVE);
    }

    @Test
    void policyHasMaturedAfterItsMaturityDate() {
        assertThat(policy.effectiveStatus(MATURITY.plusDays(1))).isEqualTo(PolicyStatus.MATURED);
    }

    @Test
    void beingFullyPaidDoesNotChangeTheStateOfCover() {
        policy.markFullyPaid(START);

        assertThat(policy.getFullyPaidOn()).isEqualTo(START);
        assertThat(policy.effectiveStatus(START)).isEqualTo(PolicyStatus.ACTIVE);
    }

    @Test
    void theFirstFullyPaidDateIsKept() {
        policy.markFullyPaid(START);
        policy.markFullyPaid(START.plusDays(3));

        assertThat(policy.getFullyPaidOn()).isEqualTo(START);
    }
}
