/*
 * OptionalText
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Allow unknown nullable text while rejecting empty supplied input.
 */
package com.equitransit.backend.dto.validation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;


/** Nullable alternative to NotBlank without a whitespace regex. */
@Target({ElementType.FIELD, ElementType.PARAMETER,
        ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = OptionalTextValidator.class)
public @interface OptionalText {

    /** @return safe validation message */
    String message() default "Must be null or contain non-whitespace text.";

    /** @return validation groups */
    Class<?>[] groups() default {};

    /** @return validation payload metadata */
    Class<? extends Payload>[] payload() default {};
}
