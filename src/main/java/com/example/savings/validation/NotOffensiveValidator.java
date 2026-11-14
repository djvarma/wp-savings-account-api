package com.example.savings.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NotOffensiveValidator implements ConstraintValidator<NotOffensive, String> {

	private final OffensiveWordFilter wordFilter;

	// Spring creates constraint validators through the bean factory, so this gets injected
	public NotOffensiveValidator(OffensiveWordFilter wordFilter) {
		this.wordFilter = wordFilter;
	}

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		return value == null || !wordFilter.isOffensive(value);
	}
}
