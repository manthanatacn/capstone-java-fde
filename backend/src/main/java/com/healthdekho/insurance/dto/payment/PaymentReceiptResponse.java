package com.healthdekho.insurance.dto.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.healthdekho.insurance.domain.PaymentMethod;
import com.healthdekho.insurance.domain.PaymentStatus;
import com.healthdekho.insurance.domain.PremiumPaymentStatus;

public record PaymentReceiptResponse(
        String policyNumber,
        LocalDate paymentDate,
        PaymentMethod paymentMethod,
        BigDecimal totalPaid,
        BigDecimal totalPenalty,
        PremiumPaymentStatus premiumPaymentStatus,
        BigDecimal remainingPremium,
        List<Line> lines) {

    public record Line(
            String transactionReference,
            int installmentNumber,
            BigDecimal amountPaid,
            BigDecimal penaltyApplied,
            PaymentStatus installmentStatus,
            BigDecimal installmentOutstanding) {
    }
}
