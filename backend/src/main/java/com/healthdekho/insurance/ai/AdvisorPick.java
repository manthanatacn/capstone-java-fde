package com.healthdekho.insurance.ai;

import com.healthdekho.insurance.domain.Suitability;

/** One ranked suggestion, as produced by the model or the rule-based ranker. Not trusted until validated. */
public record AdvisorPick(String productCode, String reason, Suitability suitability) {
}
