package com.healthdekho.insurance.domain;

import java.time.LocalDate;
import java.time.Period;

public final class Ages {

    private Ages() {
    }

    /** Completed years on the given date (age last birthday), as insurers use for entry age. */
    public static int ageOn(LocalDate dateOfBirth, LocalDate onDate) {
        if (dateOfBirth.isAfter(onDate)) {
            throw new IllegalArgumentException("Date of birth is after " + onDate);
        }
        return Period.between(dateOfBirth, onDate).getYears();
    }
}
