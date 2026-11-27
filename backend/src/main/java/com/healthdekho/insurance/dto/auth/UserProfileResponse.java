package com.healthdekho.insurance.dto.auth;

import java.time.LocalDate;

import com.healthdekho.insurance.domain.Role;

/** dateOfBirth and phone are null for staff users. */
public record UserProfileResponse(Long id, String fullName, String email, Role role, LocalDate dateOfBirth,
                                  String phone) {
}
