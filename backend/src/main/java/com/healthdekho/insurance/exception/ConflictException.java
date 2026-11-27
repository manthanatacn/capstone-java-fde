package com.healthdekho.insurance.exception;

import org.springframework.http.HttpStatus;

/** Maps to 409: the request clashes with existing state (duplicate registration, double payment). */
public class ConflictException extends BusinessRuleException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
