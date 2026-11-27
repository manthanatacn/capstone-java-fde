package com.healthdekho.insurance.dto.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.healthdekho.insurance.domain.PolicyStatus;
import com.healthdekho.insurance.domain.PolicyType;
import com.healthdekho.insurance.domain.PremiumFrequency;
import com.healthdekho.insurance.entity.CustomerPolicy;

/** totalPremiumPayable is the contract total fixed at registration. */
public record PolicySummaryResponse(String policyNumber, String productCode, String policyName,
                                    PolicyType policyType, int termMonths, LocalDate startDate,
                                    LocalDate maturityDate, PremiumFrequency premiumFrequency,
                                    BigDecimal premiumAmount, BigDecimal sumInsured, BigDecimal totalPremiumPayable,
                                    PolicyStatus status) {

    public static PolicySummaryResponse from(CustomerPolicy policy, LocalDate today) {
        return new PolicySummaryResponse(policy.getPolicyNumber(), policy.getProduct().getProductCode(),
                policy.getProduct().getPolicyName(), policy.getProduct().getPolicyType(), policy.getTermMonths(),
                policy.getStartDate(), policy.getMaturityDate(), policy.getPremiumFrequency(),
                policy.getPremiumAmount(), policy.getSumInsured(), policy.getTotalPremiumPayable(),
                policy.effectiveStatus(today));
    }
}
