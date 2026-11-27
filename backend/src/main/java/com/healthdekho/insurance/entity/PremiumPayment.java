package com.healthdekho.insurance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import com.healthdekho.insurance.domain.PaymentMethod;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One payment transaction. Ledger rows are written once and never updated. */
@Entity
@Immutable
@Table(name = "premium_payment")
public class PremiumPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "installment_id", nullable = false)
    private PremiumInstallment installment;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 30)
    private String transactionReference;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid;

    @Column(name = "penalty_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PremiumPayment() {
    }

    public PremiumPayment(PremiumInstallment installment, String transactionReference, LocalDate paymentDate,
                          BigDecimal amountPaid, BigDecimal penaltyAmount, PaymentMethod paymentMethod) {
        this.installment = installment;
        this.transactionReference = transactionReference;
        this.paymentDate = paymentDate;
        this.amountPaid = amountPaid;
        this.penaltyAmount = penaltyAmount;
        this.paymentMethod = paymentMethod;
    }

    public Long getId() { return id; }
    public PremiumInstallment getInstallment() { return installment; }
    public String getTransactionReference() { return transactionReference; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
