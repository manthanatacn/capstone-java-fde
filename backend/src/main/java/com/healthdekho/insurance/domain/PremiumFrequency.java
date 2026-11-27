package com.healthdekho.insurance.domain;

public enum PremiumFrequency {
    MONTHLY(1),
    QUARTERLY(3),
    HALF_YEARLY(6),
    YEARLY(12);

    private final int monthsPerInstallment;

    PremiumFrequency(int monthsPerInstallment) {
        this.monthsPerInstallment = monthsPerInstallment;
    }

    public int monthsPerInstallment() {
        return monthsPerInstallment;
    }

    /** Number of installments in a term; the term must be an exact multiple of the frequency. */
    public int installmentsFor(int termMonths) {
        if (termMonths <= 0 || termMonths % monthsPerInstallment != 0) {
            throw new IllegalArgumentException(
                    "Term of %d months does not split into %s installments".formatted(termMonths, name()));
        }
        return termMonths / monthsPerInstallment;
    }
}
