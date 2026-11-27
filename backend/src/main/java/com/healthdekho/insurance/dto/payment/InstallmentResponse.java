package com.healthdekho.insurance.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import com.healthdekho.insurance.domain.PaymentStatus;

/** penaltyIfPaidToday lets the UI show the late penalty before the user confirms a payment. */
public record InstallmentResponse(
        Long installmentId,
        int installmentNumber,
        YearMonth premiumPeriod,
        LocalDate dueDate,
        LocalDate gracePeriodEndDate,
        BigDecimal scheduledAmount,
        BigDecimal penaltyAmount,
        BigDecimal paidAmount,
        BigDecimal outstandingAmount,
        PaymentStatus status,
        boolean payable,
        BigDecimal penaltyIfPaidToday) {
}
