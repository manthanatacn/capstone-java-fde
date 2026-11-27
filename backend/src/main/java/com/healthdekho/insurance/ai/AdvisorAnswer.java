package com.healthdekho.insurance.ai;

import java.util.List;

/** Structured-output shape the model is asked to return, best pick first. */
public record AdvisorAnswer(List<AdvisorPick> picks) {
}
