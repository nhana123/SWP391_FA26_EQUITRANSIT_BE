/*
 * ValidationLimits
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Share validation limits across authentication requests.
 */
package com.equitransit.backend.dto;





/** Defines request limits matching the live database. */
public final class ValidationLimits {

    public static final int EMAIL_MAX_LENGTH = 255;
    public static final int NAME_MAX_LENGTH = 150;
    public static final int PHONE_MAX_LENGTH = 30;
    public static final int ADDRESS_MAX_LENGTH = 500;
    public static final int PASSWORD_MIN_LENGTH = 12;
    public static final int PASSWORD_MAX_LENGTH = 72;
    public static final int TOKEN_MAX_LENGTH = 200;

    private ValidationLimits() {
    }
}
