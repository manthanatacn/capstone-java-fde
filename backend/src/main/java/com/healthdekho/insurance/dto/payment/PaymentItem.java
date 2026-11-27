package com.healthdekho.insurance.dto.payment;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PaymentItem(
        @NotNull(message = "Installment is required") Long installmentId,
        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 12, fraction = 2, message = "Enter an amount up to 12 digits with at most 2 decimal places")
        BigDecimal amount) {
}
