package com.healthdekho.insurance.ai;

import com.healthdekho.insurance.domain.PolicyType;

/** What the ranking sees about the customer. goals, preferredTermYears and policyType may be null. */
public record CustomerNeeds(int age, String goals, Integer preferredTermYears, PolicyType policyType) {
}
