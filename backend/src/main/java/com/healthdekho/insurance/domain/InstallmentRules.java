package com.healthdekho.insurance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Status, payability and late-penalty rules for one premium installment (architecture §5).
 * Works on plain values so the same rules apply on read and on write.
 */
public final class InstallmentRules {

    public static final int LATE_PENALTY_PERCENT = 10;
    public static final int PAYABLE_MONTHS_AHEAD = 1;

    private InstallmentRules() {
    }

    public static PaymentStatus resolveStatus(BigDecimal outstanding, BigDecimal paid, LocalDate gracePeriodEndDate,
                                              LocalDate today) {
        if (outstanding.signum() <= 0) {
            return PaymentStatus.PAID;
        }
        if (today.isAfter(gracePeriodEndDate)) {
            return PaymentStatus.OVERDUE;
        }
        return Money.isPositive(paid) ? PaymentStatus.PARTIALLY_PAID : PaymentStatus.PENDING;
    }

    /** {@code status} must already be resolved for {@code today}. */
    public static boolean isPayable(PaymentStatus status, LocalDate dueDate, LocalDate today) {
        return switch (status) {
            case PAID -> false;
            // overdue means the grace period has ended, so the due date is already behind us
            case OVERDUE -> true;
            case PENDING, PARTIALLY_PAID -> !dueDate.isAfter(today.plusMonths(PAYABLE_MONTHS_AHEAD));
        };
    }

    /**
     * Penalty a payment made on {@code paymentDate} would add: 10% of what is outstanding before that
     * payment, once per installment (PRD A-8). Zero when on time or when a penalty is already applied.
     */
    public static BigDecimal penaltyIfPaidOn(LocalDate paymentDate, LocalDate gracePeriodEndDate,
                                             BigDecimal penaltyAlreadyApplied, BigDecimal outstanding) {
        if (!paymentDate.isAfter(gracePeriodEndDate) || Money.isPositive(penaltyAlreadyApplied)) {
            return Money.normalize(BigDecimal.ZERO);
        }
        return Money.percentOf(outstanding, LATE_PENALTY_PERCENT);
    }
}
