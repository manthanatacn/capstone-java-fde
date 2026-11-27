package com.healthdekho.insurance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class FormatsTest {

    @Test
    void rupeesUseLakhGrouping() {
        assertThat(Formats.rupees(new BigDecimal("285000.5"))).isEqualTo("₹2,85,000.50");
        assertThat(Formats.rupees(new BigDecimal("5000000"))).isEqualTo("₹50,00,000.00");
        assertThat(Formats.rupees(new BigDecimal("1234567.891"))).isEqualTo("₹12,34,567.89");
        assertThat(Formats.rupees(new BigDecimal("750"))).isEqualTo("₹750.00");
        assertThat(Formats.rupees(new BigDecimal("0"))).isEqualTo("₹0.00");
    }

    @Test
    void datesMatchTheWebApp() {
        assertThat(Formats.date(LocalDate.of(2026, 9, 5))).isEqualTo("05 Sep 2026");
    }

    @Test
    void yearsReadLikeTheSpecSample() {
        assertThat(Formats.years(1)).isEqualTo("1 year");
        assertThat(Formats.years(20)).isEqualTo("20 years");
    }
}
