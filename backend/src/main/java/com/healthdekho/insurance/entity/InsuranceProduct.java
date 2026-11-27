package com.healthdekho.insurance.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Product master. Read-only from the application's point of view in the current scope. */
@Entity
@Table(name = "insurance_product")
public class InsuranceProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "product_code", nullable = false, unique = true, length = 30)
    private String productCode;

    @Column(name = "policy_name", nullable = false, length = 120)
    private String policyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "policy_type", nullable = false, length = 20)
    private PolicyType policyType;

    @Column(length = 500)
    private String description;

    @Column(name = "term_months", nullable = false)
    private int termMonths;

    @Enumerated(EnumType.STRING)
    @Column(name = "premium_frequency", nullable = false, length = 20)
    private PremiumFrequency premiumFrequency;

    @Column(name = "premium_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "sum_insured", nullable = false, precision = 19, scale = 2)
    private BigDecimal sumInsured;

    @Column(name = "min_entry_age", nullable = false)
    private int minEntryAge;

    @Column(name = "max_entry_age", nullable = false)
    private int maxEntryAge;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected InsuranceProduct() {
    }

    public InsuranceProduct(String productCode, String policyName, PolicyType policyType, String description,
                            int termMonths, PremiumFrequency premiumFrequency, BigDecimal premiumAmount,
                            BigDecimal sumInsured, int minEntryAge, int maxEntryAge) {
        this.productCode = productCode;
        this.policyName = policyName;
        this.policyType = policyType;
        this.description = description;
        this.termMonths = termMonths;
        this.premiumFrequency = premiumFrequency;
        this.premiumAmount = premiumAmount;
        this.sumInsured = sumInsured;
        this.minEntryAge = minEntryAge;
        this.maxEntryAge = maxEntryAge;
    }

    public Long getId() { return id; }
    public String getProductCode() { return productCode; }
    public String getPolicyName() { return policyName; }
    public PolicyType getPolicyType() { return policyType; }
    public String getDescription() { return description; }
    public int getTermMonths() { return termMonths; }
    public PremiumFrequency getPremiumFrequency() { return premiumFrequency; }
    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public BigDecimal getSumInsured() { return sumInsured; }
    public int getMinEntryAge() { return minEntryAge; }
    public int getMaxEntryAge() { return maxEntryAge; }
    public boolean isActive() { return active; }

    public boolean acceptsEntryAge(int age) {
        return age >= minEntryAge && age <= maxEntryAge;
    }
}
