package com.healthdekho.insurance.exception;

import org.springframework.http.HttpStatus;

/** Maps to 400: the search form was filled in with a combination we don't support (AC-5.2). */
public class InvalidSearchCriteriaException extends BusinessRuleException {

    public InvalidSearchCriteriaException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_CRITERIA", message);
    }
}
