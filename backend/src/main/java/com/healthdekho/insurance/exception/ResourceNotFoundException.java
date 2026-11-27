package com.healthdekho.insurance.exception;

/** Maps to 404. Also used when a record exists but belongs to someone else, so ids cannot be probed. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
