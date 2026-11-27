package com.healthdekho.insurance.dto.payment;

import java.util.List;

import com.healthdekho.insurance.domain.PaymentMethod;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentRequest(
        @NotNull(message = "Payment method is required") PaymentMethod paymentMethod,
        @NotEmpty(message = "Select at least one installment")
        @Size(max = 24, message = "At most 24 installments can be paid at once")
        List<@Valid @NotNull PaymentItem> items) {
}
