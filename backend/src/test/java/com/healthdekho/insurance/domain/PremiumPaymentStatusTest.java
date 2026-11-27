package com.healthdekho.insurance.domain;

import static com.healthdekho.insurance.domain.PaymentStatus.OVERDUE;
import static com.healthdekho.insurance.domain.PaymentStatus.PAID;
import static com.healthdekho.insurance.domain.PaymentStatus.PARTIALLY_PAID;
import static com.healthdekho.insurance.domain.PaymentStatus.PENDING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class PremiumPaymentStatusTest {

    @Test
    void everyInstallmentPaidIsFullyPaid() {
        assertThat(PremiumPaymentStatus.of(List.of(PAID))).isEqualTo(PremiumPaymentStatus.FULLY_PAID);
        assertThat(PremiumPaymentStatus.of(List.of(PAID, PAID, PAID))).isEqualTo(PremiumPaymentStatus.FULLY_PAID);
    }

    @Test
    void anyOverdueInstallmentMakesThePolicyOverdue() {
        assertThat(PremiumPaymentStatus.of(List.of(OVERDUE))).isEqualTo(PremiumPaymentStatus.OVERDUE);
        assertThat(PremiumPaymentStatus.of(List.of(PAID, OVERDUE, PARTIALLY_PAID, PENDING)))
                .isEqualTo(PremiumPaymentStatus.OVERDUE);
    }

    @Test
    void partPaidInstallmentWithoutAnOverdueOneIsPartiallyPaid() {
        // Aarav's policy 3001 in the spec sample: #1 paid, #2 part paid, the rest pending
        assertThat(PremiumPaymentStatus.of(List.of(PAID, PARTIALLY_PAID, PENDING)))
                .isEqualTo(PremiumPaymentStatus.PARTIALLY_PAID);
        assertThat(PremiumPaymentStatus.of(List.of(PARTIALLY_PAID))).isEqualTo(PremiumPaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void otherwiseAnUnpaidInstallmentLeavesThePolicyPending() {
        assertThat(PremiumPaymentStatus.of(List.of(PENDING))).isEqualTo(PremiumPaymentStatus.PENDING);
        assertThat(PremiumPaymentStatus.of(List.of(PAID, PAID, PENDING))).isEqualTo(PremiumPaymentStatus.PENDING);
    }

    @Test
    void orderOfTheInstallmentsDoesNotMatter() {
        assertThat(PremiumPaymentStatus.of(List.of(PENDING, PARTIALLY_PAID, PAID)))
                .isEqualTo(PremiumPaymentStatus.of(List.of(PAID, PARTIALLY_PAID, PENDING)));
    }

    @Test
    void aPolicyWithoutInstallmentsIsAnError() {
        // registration always generates a schedule, so this can only be a bug
        assertThatThrownBy(() -> PremiumPaymentStatus.of(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
