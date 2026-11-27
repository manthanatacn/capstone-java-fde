package com.healthdekho.insurance.dto.auth;

import java.time.Instant;

public record LoginResponse(String token, Instant expiresAt, UserProfileResponse user) {
}
