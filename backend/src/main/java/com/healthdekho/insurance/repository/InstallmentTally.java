package com.healthdekho.insurance.repository;

import java.math.BigDecimal;

import com.healthdekho.insurance.domain.PaymentStatus;

/** Projection: the installments of one policy that share a status today, with their penalties and payments. */
public record InstallmentTally(Long policyId, PaymentStatus status, BigDecimal latePenalties, BigDecimal paid) {
}
