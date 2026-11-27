package com.healthdekho.insurance.dto.policy;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * The start-date window depends on "today" from the application clock, so it is checked in the
 * service rather than with @FutureOrPresent (which reads the system clock).
 */
public record RegisterPolicyRequest(
        @NotBlank(message = "Product code is required") String productCode,
        @NotNull(message = "Start date is required") LocalDate startDate,
        Long recommendationItemId) {
}
