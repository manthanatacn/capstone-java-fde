package com.healthdekho.insurance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import com.healthdekho.insurance.domain.PaymentStatus;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "premium_installment")
public class PremiumInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "installment_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_id", nullable = false)
    private CustomerPolicy policy;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "grace_period_end_date", nullable = false)
    private LocalDate gracePeriodEndDate;

    @Column(name = "scheduled_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal scheduledAmount;

    @Column(name = "penalty_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /** Optimistic lock: two concurrent payments on the same installment cannot both succeed. */
    @Version
    @Column(nullable = false)
    private long version;

    protected PremiumInstallment() {
    }

    public PremiumInstallment(int installmentNumber, LocalDate dueDate, LocalDate gracePeriodEndDate,
                              BigDecimal scheduledAmount) {
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
        this.gracePeriodEndDate = gracePeriodEndDate;
        this.scheduledAmount = scheduledAmount;
    }

    void attachTo(CustomerPolicy policy) {
        this.policy = policy;
    }

    /** Amount still owed on this installment, penalty included. */
    public BigDecimal getOutstandingAmount() {
        return scheduledAmount.add(penaltyAmount).subtract(paidAmount);
    }

    /** The premium period this installment covers, e.g. 2026-11. */
    public YearMonth getPremiumPeriod() {
        return YearMonth.from(dueDate);
    }

    public void applyPenalty(BigDecimal penalty) {
        this.penaltyAmount = this.penaltyAmount.add(penalty);
    }

    public void recordPayment(BigDecimal amount) {
        this.paidAmount = this.paidAmount.add(amount);
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Long getId() { return id; }
    public CustomerPolicy getPolicy() { return policy; }
    public int getInstallmentNumber() { return installmentNumber; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getGracePeriodEndDate() { return gracePeriodEndDate; }
    public BigDecimal getScheduledAmount() { return scheduledAmount; }
    public BigDecimal getPenaltyAmount() { return penaltyAmount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public long getVersion() { return version; }
}
