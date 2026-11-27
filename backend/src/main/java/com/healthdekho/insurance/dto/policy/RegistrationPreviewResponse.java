package com.healthdekho.insurance.dto.policy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** ineligibilityReason is null when eligible is true. */
public record RegistrationPreviewResponse(CustomerDetails customer, ProductResponse product, LocalDate startDate,
                                          LocalDate maturityDate, int installmentCount,
                                          BigDecimal totalPremiumPayable, int ageAtStart, boolean eligible,
                                          String ineligibilityReason) {

    public record CustomerDetails(String fullName, LocalDate dateOfBirth, String email, String phone) {
    }
}
