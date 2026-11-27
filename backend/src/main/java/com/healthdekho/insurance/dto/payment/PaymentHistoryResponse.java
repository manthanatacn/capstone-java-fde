package com.healthdekho.insurance.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import com.healthdekho.insurance.domain.PaymentMethod;

public record PaymentHistoryResponse(
        String transactionReference,
        int installmentNumber,
        YearMonth premiumPeriod,
        LocalDate paymentDate,
        BigDecimal amountPaid,
        BigDecimal penaltyAmount,
        PaymentMethod paymentMethod) {
}
