package com.healthdekho.insurance.exception;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import jakarta.validation.ConstraintViolationException;

/**
 * Every error leaves the API as RFC 9457 problem+json. Business errors add a "code";
 * validation errors add "errors" as field -> message.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException ex) {
        ProblemDetail problem = problem(ex.getStatus(), ex.getMessage());
        problem.setProperty("code", ex.getCode());
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    ProblemDetail badCredentials(BadCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    @ExceptionHandler(DisabledException.class)
    ProblemDetail disabled(DisabledException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "This account is inactive. Please contact support.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(lastNode(v.getPropertyPath().toString()), v.getMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Some fields are invalid.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid value for '%s'.".formatted(ex.getName()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT,
                "This record was updated by another request. Refresh and try again.");
        problem.setProperty("code", "CONCURRENT_UPDATE");
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        ProblemDetail problem = problem(HttpStatus.CONFLICT, "The request conflicts with existing data.");
        problem.setProperty("code", "DATA_CONFLICT");
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail database(DataAccessException ex) {
        log.error("Database failure", ex);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "The service is temporarily unavailable. Please try again.");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side.");
    }

    /** Unreadable JSON values (bad date, unknown enum, fraction where a whole number is expected). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        MismatchedInputException mismatch = findCause(ex, MismatchedInputException.class);
        if (mismatch != null && !mismatch.getPath().isEmpty()) {
            String field = mismatch.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining(".")).replace(".[", "[");
            ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Some fields are invalid.");
            problem.setProperty("errors", Map.of(field, unreadableValueMessage(mismatch.getTargetType())));
            return ResponseEntity.badRequest().body(problem);
        }
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "The request body could not be read."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.isBindingFailure()
                        ? unreadableValueMessage(ex.getBindingResult().getFieldType(fe.getField()))
                        : fe.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Some fields are invalid.");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatusCode status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }

    private static <T extends Throwable> T findCause(Throwable ex, Class<T> type) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    private static String unreadableValueMessage(Class<?> type) {
        if (type == null) {
            return "Enter a valid value.";
        }
        if (LocalDate.class.equals(type)) {
            return "Enter a date as yyyy-mm-dd.";
        }
        if (type.isEnum()) {
            return "Choose one of: " + Arrays.stream(type.getEnumConstants()).map(Object::toString)
                    .collect(Collectors.joining(", ")) + ".";
        }
        if (Integer.class.equals(type) || int.class.equals(type) || Long.class.equals(type) || long.class.equals(type)) {
            return "Enter a whole number.";
        }
        if (Number.class.isAssignableFrom(type)) {
            return "Enter a number.";
        }
        return "Enter a valid value.";
    }

    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
