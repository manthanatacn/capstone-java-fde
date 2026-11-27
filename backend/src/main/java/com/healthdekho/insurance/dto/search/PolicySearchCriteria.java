package com.healthdekho.insurance.dto.search;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/** Bound from query parameters; every field is optional and the service checks the combination. */
public record PolicySearchCriteria(
        String policyNumber,
        String customerName,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth) {
}
