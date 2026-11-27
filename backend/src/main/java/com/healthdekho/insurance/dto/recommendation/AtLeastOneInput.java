package com.healthdekho.insurance.dto.recommendation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AtLeastOneInput.Validator.class)
public @interface AtLeastOneInput {

    String message() default "Enter at least one of date of birth, goals, preferred term or insurance type";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<AtLeastOneInput, RecommendationRequest> {

        @Override
        public boolean isValid(RecommendationRequest request, ConstraintValidatorContext context) {
            return request == null || request.hasAnyInput();
        }
    }
}
