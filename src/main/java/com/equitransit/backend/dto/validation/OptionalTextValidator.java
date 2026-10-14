/*
 * OptionalTextValidator
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Validate optional text without requiring an unknown value.
 */
package com.equitransit.backend.dto.validation;


import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


/** Rejects whitespace-only values while preserving nullable intent. */
public class OptionalTextValidator
        implements ConstraintValidator<OptionalText, CharSequence> {

    /**
     * Validates supplied text without normalizing its content.
     * @param value optional input
     * @param context validation metadata
     * @return true for null or nonblank text
     */
    @Override
    public boolean isValid(CharSequence value,
            ConstraintValidatorContext context) {
        return value == null || !value.toString().isBlank();
    }
}
