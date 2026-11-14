package com.example.savings.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The annotated string must not contain any word from the offensive word list.
 * {@code null} is considered valid.
 */
@Documented
@Constraint(validatedBy = NotOffensiveValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface NotOffensive {

	String message() default "nickname contains offensive language";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
