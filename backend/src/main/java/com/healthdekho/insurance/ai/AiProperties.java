package com.healthdekho.insurance.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(@NotBlank String promptVersion, @Min(1) int maxRecommendations) {
}
