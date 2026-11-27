package com.healthdekho.insurance.dto.policy;

import java.math.BigDecimal;

import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;
import com.healthdekho.insurance.entity.InsuranceProduct;

public record ProductResponse(String productCode, String policyName, PolicyType policyType, String description,
                              int termMonths, PremiumFrequency premiumFrequency, BigDecimal premiumAmount,
                              BigDecimal sumInsured, int minEntryAge, int maxEntryAge) {

    public static ProductResponse from(InsuranceProduct product) {
        return new ProductResponse(product.getProductCode(), product.getPolicyName(), product.getPolicyType(),
                product.getDescription(), product.getTermMonths(), product.getPremiumFrequency(),
                product.getPremiumAmount(), product.getSumInsured(), product.getMinEntryAge(),
                product.getMaxEntryAge());
    }
}
