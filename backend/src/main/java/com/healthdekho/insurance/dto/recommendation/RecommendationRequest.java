package com.healthdekho.insurance.dto.recommendation;

import java.time.LocalDate;

import com.healthdekho.insurance.domain.PolicyType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Every field is optional, but at least one must be given. dateOfBirth defaults to the profile value. */
@AtLeastOneInput
public record RecommendationRequest(
        @PastOrPresent(message = "Date of birth cannot be in the future") LocalDate dateOfBirth,
        @Size(max = 500, message = "Goals must be 500 characters or fewer") String goals,
        @Min(value = 1, message = "Preferred term must be between 1 and 40 years")
        @Max(value = 40, message = "Preferred term must be between 1 and 40 years") Integer preferredTermYears,
        PolicyType policyType) {

    public boolean hasAnyInput() {
        return dateOfBirth != null || (goals != null && !goals.isBlank()) || preferredTermYears != null
                || policyType != null;
    }
}
