package com.healthdekho.insurance.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** Maturity date, total premium and installment rows for a policy starting on a given date. */
public record PolicySchedule(LocalDate maturityDate, BigDecimal totalPremiumPayable, List<Installment> installments) {

    public record Installment(int installmentNumber, LocalDate dueDate, LocalDate gracePeriodEndDate,
                              BigDecimal amount) {
    }

    public static PolicySchedule of(LocalDate startDate, int termMonths, PremiumFrequency frequency,
                                    BigDecimal premiumAmount) {
        int count = frequency.installmentsFor(termMonths);
        int monthsPerInstallment = frequency.monthsPerInstallment();
        BigDecimal amount = Money.normalize(premiumAmount);

        List<Installment> installments = IntStream.rangeClosed(1, count)
                .mapToObj(n -> {
                    // offset from the start date every time; chaining plusMonths would stick at the 28th
                    // after February for a policy that starts on the 31st
                    LocalDate due = startDate.plusMonths((long) (n - 1) * monthsPerInstallment);
                    return new Installment(n, due, due.plusMonths(1), amount);
                })
                .toList();

        return new PolicySchedule(
                startDate.plusMonths(termMonths).minusDays(1),
                Money.normalize(amount.multiply(BigDecimal.valueOf(count))),
                installments);
    }

    public int installmentCount() {
        return installments.size();
    }
}
