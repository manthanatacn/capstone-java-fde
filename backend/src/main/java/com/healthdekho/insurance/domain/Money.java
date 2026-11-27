package com.healthdekho.insurance.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupee amounts: always two decimals, half-up rounding. No double arithmetic anywhere. */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Money() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal percentOf(BigDecimal amount, int percent) {
        return normalize(amount.multiply(BigDecimal.valueOf(percent)).divide(HUNDRED, SCALE + 2, ROUNDING));
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    public static boolean isZero(BigDecimal amount) {
        return amount.signum() == 0;
    }
}
