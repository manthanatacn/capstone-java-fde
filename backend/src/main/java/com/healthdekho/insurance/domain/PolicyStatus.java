package com.healthdekho.insurance.domain;

/** State of cover. Whether the premiums are paid is a separate matter: see {@link PremiumPaymentStatus}. */
public enum PolicyStatus {
    ACTIVE, MATURED
}
