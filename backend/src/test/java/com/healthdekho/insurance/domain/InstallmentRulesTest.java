package com.healthdekho.insurance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InstallmentRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);
    private static final LocalDate GRACE_END = LocalDate.of(2026, 12, 20);
    private static final LocalDate PAST_GRACE_END = LocalDate.of(2026, 10, 15);

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    @Test
    void nothingPaidWithinGraceIsPending() {
        assertThat(InstallmentRules.resolveStatus(amount("1250.00"), amount("0.00"), GRACE_END, TODAY))
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void somethingPaidWithinGraceIsPartiallyPaid() {
        assertThat(InstallmentRules.resolveStatus(amount("750.00"), amount("500.00"), GRACE_END, TODAY))
                .isEqualTo(PaymentStatus.PARTIALLY_PAID);
    }

    @Test
    void lastDayOfGraceIsNotOverdueYet() {
        assertThat(InstallmentRules.resolveStatus(amount("1250.00"), amount("0.00"), GRACE_END, GRACE_END))
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void dayAfterGraceIsOverdue() {
        assertThat(InstallmentRules.resolveStatus(amount("1250.00"), amount("0.00"), GRACE_END, GRACE_END.plusDays(1)))
                .isEqualTo(PaymentStatus.OVERDUE);
    }

    @Test
    void overdueTakesPrecedenceOverPartiallyPaid() {
        assertThat(InstallmentRules.resolveStatus(amount("750.00"), amount("500.00"), GRACE_END, GRACE_END.plusDays(1)))
                .isEqualTo(PaymentStatus.OVERDUE);
    }

    @Test
    void nothingOutstandingIsPaidEvenAfterGrace() {
        assertThat(InstallmentRules.resolveStatus(amount("0.00"), amount("1250.00"), GRACE_END, TODAY))
                .isEqualTo(PaymentStatus.PAID);
        assertThat(InstallmentRules.resolveStatus(amount("0.00"), amount("8250.00"), PAST_GRACE_END, TODAY))
                .isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void dueExactlyOneMonthAheadIsPayable() {
        assertThat(InstallmentRules.isPayable(PaymentStatus.PENDING, LocalDate.of(2026, 12, 25), TODAY)).isTrue();
    }

    @Test
    void dueOneMonthAndOneDayAheadIsNotPayable() {
        assertThat(InstallmentRules.isPayable(PaymentStatus.PENDING, LocalDate.of(2026, 12, 26), TODAY)).isFalse();
    }

    @Test
    void partiallyPaidInsideTheWindowIsPayable() {
        assertThat(InstallmentRules.isPayable(PaymentStatus.PARTIALLY_PAID, LocalDate.of(2026, 12, 20), TODAY)).isTrue();
    }

    @Test
    void pastDueAndOverdueArePayable() {
        assertThat(InstallmentRules.isPayable(PaymentStatus.PENDING, LocalDate.of(2026, 11, 1), TODAY)).isTrue();
        assertThat(InstallmentRules.isPayable(PaymentStatus.OVERDUE, LocalDate.of(2026, 9, 15), TODAY)).isTrue();
    }

    @Test
    void paidIsNeverPayable() {
        assertThat(InstallmentRules.isPayable(PaymentStatus.PAID, LocalDate.of(2026, 11, 20), TODAY)).isFalse();
    }

    @Test
    void noPenaltyOnTheLastDayOfGrace() {
        assertThat(InstallmentRules.penaltyIfPaidOn(PAST_GRACE_END, PAST_GRACE_END, amount("0.00"), amount("7500.00")))
                .isEqualTo(amount("0.00"));
    }

    @ParameterizedTest
    @CsvSource({"7500.00, 750.00", "750.00, 75.00", "1234.55, 123.46", "1234.54, 123.45", "0.04, 0.00"})
    void penaltyIsTenPercentOfOutstandingRoundedHalfUp(String outstanding, String expected) {
        BigDecimal penalty = InstallmentRules.penaltyIfPaidOn(TODAY, PAST_GRACE_END, amount("0.00"), amount(outstanding));
        assertThat(penalty).isEqualTo(amount(expected));
    }

    @Test
    void penaltyIsAppliedOnceOnly() {
        assertThat(InstallmentRules.penaltyIfPaidOn(TODAY, PAST_GRACE_END, amount("750.00"), amount("4250.00")))
                .isEqualTo(amount("0.00"));
    }
}
