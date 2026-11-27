package com.healthdekho.insurance.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.healthdekho.insurance.domain.PolicyStatus;
import com.healthdekho.insurance.domain.PremiumFrequency;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A policy registered by a customer. Premium, frequency and cover are copied from the product
 * at registration time: they are the contracted values and must not change if the product
 * master is edited later.
 */
@Entity
@Table(name = "customer_policy")
public class CustomerPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_id")
    private Long id;

    @Column(name = "policy_number", nullable = false, unique = true, length = 20)
    private String policyNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct product;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "premium_frequency", nullable = false, length = 20)
    private PremiumFrequency premiumFrequency;

    @Column(name = "premium_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "sum_insured", nullable = false, precision = 19, scale = 2)
    private BigDecimal sumInsured;

    /** The contract total at registration (PDF: premium × installments). Late penalties are tracked per installment. */
    @Column(name = "total_premium_payable", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPremiumPayable;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyStatus status = PolicyStatus.ACTIVE;

    @Column(name = "fully_paid_on")
    private LocalDate fullyPaidOn;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("installmentNumber ASC")
    private List<PremiumInstallment> installments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected CustomerPolicy() {
    }

    public CustomerPolicy(String policyNumber, Customer customer, InsuranceProduct product,
                          LocalDate startDate, LocalDate maturityDate, BigDecimal totalPremiumPayable) {
        this.policyNumber = policyNumber;
        this.customer = customer;
        this.product = product;
        this.startDate = startDate;
        this.maturityDate = maturityDate;
        this.premiumFrequency = product.getPremiumFrequency();
        this.premiumAmount = product.getPremiumAmount();
        this.sumInsured = product.getSumInsured();
        this.totalPremiumPayable = totalPremiumPayable;
    }

    public void addInstallment(PremiumInstallment installment) {
        installments.add(installment);
        installment.attachTo(this);
    }

    /** Records the day the last installment was paid. Cover is unaffected: the policy stays ACTIVE. */
    public void markFullyPaid(LocalDate paidOn) {
        if (fullyPaidOn == null) {
            this.fullyPaidOn = paidOn;
        }
    }

    /** No job writes MATURED, so maturity is read from the date: ADR-5 applies to cover as it does to payments. */
    public PolicyStatus effectiveStatus(LocalDate today) {
        return today.isAfter(maturityDate) ? PolicyStatus.MATURED : status;
    }

    public Long getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public Customer getCustomer() { return customer; }
    public InsuranceProduct getProduct() { return product; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public PremiumFrequency getPremiumFrequency() { return premiumFrequency; }
    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public BigDecimal getSumInsured() { return sumInsured; }
    public BigDecimal getTotalPremiumPayable() { return totalPremiumPayable; }
    public PolicyStatus getStatus() { return status; }
    public LocalDate getFullyPaidOn() { return fullyPaidOn; }
    public List<PremiumInstallment> getInstallments() { return installments; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    /** Term is not stored on the policy; it is a product rule (spec 5.1: no duplicated term rules). */
    public int getTermMonths() {
        return product.getTermMonths();
    }
}
