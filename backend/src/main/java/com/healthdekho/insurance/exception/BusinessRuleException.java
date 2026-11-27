package com.healthdekho.insurance.exception;

import org.springframework.http.HttpStatus;

/**
 * A request that is well-formed but breaks an insurance rule (age outside entry band,
 * paying more than is owed, ...). The code is stable and meant for the UI and tests.
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public BusinessRuleException(String code, String message) {
        this(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }

    protected BusinessRuleException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
