package com.healthdekho.insurance.dto.recommendation;

import java.math.BigDecimal;

import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;
import com.healthdekho.insurance.domain.Suitability;

public record RecommendedProductResponse(Long itemId, int rank, String productCode, String policyName,
                                         PolicyType policyType, int termMonths, BigDecimal sumInsured,
                                         PremiumFrequency premiumFrequency, BigDecimal premiumAmount,
                                         String reason, Suitability suitability) {
}
