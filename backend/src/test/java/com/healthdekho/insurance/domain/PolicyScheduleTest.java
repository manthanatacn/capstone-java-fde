package com.healthdekho.insurance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.healthdekho.insurance.domain.PolicySchedule.Installment;

class PolicyScheduleTest {

    @Test
    void specSampleMonthlyTwentyYears() {
        PolicySchedule schedule = PolicySchedule.of(LocalDate.of(2026, 11, 20), 240, PremiumFrequency.MONTHLY,
                new BigDecimal("1250.00"));

        assertThat(schedule.maturityDate()).isEqualTo(LocalDate.of(2046, 11, 19));
        assertThat(schedule.totalPremiumPayable()).isEqualByComparingTo("300000.00");
        assertThat(schedule.totalPremiumPayable().scale()).isEqualTo(2);
        assertThat(schedule.installmentCount()).isEqualTo(240);

        Installment first = schedule.installments().getFirst();
        assertThat(first).isEqualTo(new Installment(1, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20),
                new BigDecimal("1250.00")));
        Installment last = schedule.installments().getLast();
        assertThat(last.installmentNumber()).isEqualTo(240);
        assertThat(last.dueDate()).isEqualTo(LocalDate.of(2046, 10, 20));
        assertThat(last.gracePeriodEndDate()).isEqualTo(LocalDate.of(2046, 11, 20));
    }

    @Test
    void yearlyTwelveMonthTermHasOneInstallment() {
        PolicySchedule schedule = PolicySchedule.of(LocalDate.of(2026, 11, 20), 12, PremiumFrequency.YEARLY,
                new BigDecimal("18000"));

        assertThat(schedule.maturityDate()).isEqualTo(LocalDate.of(2027, 11, 19));
        assertThat(schedule.installments()).containsExactly(new Installment(1, LocalDate.of(2026, 11, 20),
                LocalDate.of(2026, 12, 20), new BigDecimal("18000.00")));
        assertThat(schedule.totalPremiumPayable()).isEqualTo(new BigDecimal("18000.00"));
    }

    @Test
    void quarterlyTenYearsHasFortyInstallmentsThreeMonthsApart() {
        PolicySchedule schedule = PolicySchedule.of(LocalDate.of(2026, 6, 15), 120, PremiumFrequency.QUARTERLY,
                new BigDecimal("7500.00"));

        assertThat(schedule.installmentCount()).isEqualTo(40);
        assertThat(schedule.maturityDate()).isEqualTo(LocalDate.of(2036, 6, 14));
        assertThat(schedule.totalPremiumPayable()).isEqualTo(new BigDecimal("300000.00"));
        assertThat(schedule.installments().get(1).dueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(schedule.installments().getLast().dueDate()).isEqualTo(LocalDate.of(2036, 3, 15));
    }

    @Test
    void monthEndStartDateDoesNotDriftAfterFebruary() {
        PolicySchedule schedule = PolicySchedule.of(LocalDate.of(2026, 1, 31), 12, PremiumFrequency.MONTHLY,
                new BigDecimal("100.00"));

        List<LocalDate> dueDates = schedule.installments().stream().map(Installment::dueDate).toList();
        assertThat(dueDates).startsWith(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 31));
        assertThat(dueDates.getLast()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(schedule.installments().getFirst().gracePeriodEndDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(schedule.maturityDate()).isEqualTo(LocalDate.of(2027, 1, 30));
    }

    @Test
    void termThatDoesNotSplitIntoInstallmentsIsRejected() {
        assertThatThrownBy(() -> PolicySchedule.of(LocalDate.of(2026, 11, 20), 10, PremiumFrequency.QUARTERLY,
                new BigDecimal("100.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
