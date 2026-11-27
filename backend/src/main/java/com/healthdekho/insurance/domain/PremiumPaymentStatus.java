package com.healthdekho.insurance.domain;

import java.util.Collection;
import java.util.Comparator;

/** Payment position of a whole policy, derived from its installments (architecture §5, AC-5B.2). */
public enum PremiumPaymentStatus {
    PENDING, PARTIALLY_PAID, OVERDUE, FULLY_PAID;

    /**
     * The policy takes the status of its most pressing installment: any overdue one makes it OVERDUE,
     * otherwise any part-paid one PARTIALLY_PAID, otherwise any unpaid one PENDING. It is FULLY_PAID only
     * when every installment is paid. The installment statuses must already be resolved for today.
     */
    public static PremiumPaymentStatus of(Collection<PaymentStatus> installmentStatuses) {
        return installmentStatuses.stream()
                .map(PremiumPaymentStatus::forInstallment)
                .max(Comparator.comparingInt(PremiumPaymentStatus::urgency))
                .orElseThrow(() -> new IllegalArgumentException("A policy always has at least one installment."));
    }

    private static PremiumPaymentStatus forInstallment(PaymentStatus status) {
        return switch (status) {
            case PAID -> FULLY_PAID;
            case PENDING -> PENDING;
            case PARTIALLY_PAID -> PARTIALLY_PAID;
            case OVERDUE -> OVERDUE;
        };
    }

    private int urgency() {
        return switch (this) {
            case FULLY_PAID -> 0;
            case PENDING -> 1;
            case PARTIALLY_PAID -> 2;
            case OVERDUE -> 3;
        };
    }
}
