/*
 * ApiPatterns
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Share anchored request and OpenAPI patterns.
 */
package com.equitransit.backend.dto.common;


/** Format bounds; business and source checks remain in services. */
public final class ApiPatterns {

    /** Anchored version format. */
    public static final String VERSION =
            "^[0-9a-fA-F]{16}$";

    /** Anchored phone format. */
    public static final String PHONE =
            "^\\+?[1-9]\\d{7,14}$";

    /** Anchored country format. */
    public static final String COUNTRY =
            "^[A-Z]{2}$";

    /** Anchored airport format. */
    public static final String AIRPORT =
            "^[A-Z]{3}$";

    /** Anchored timezone format. */
    public static final String TIMEZONE =
            "^[A-Za-z_]+/[A-Za-z_]+(/[A-Za-z_]+)?$";

    /** Anchored currency format. */
    public static final String CURRENCY =
            "^[A-Z]{3}$";

    /** Anchored decimal format. */
    public static final String DECIMAL =
            "^\\d+(\\.\\d{1,2})?$";

    /** Anchored microchip format. */
    public static final String MICROCHIP =
            "^[A-Z0-9-]{9,15}$";

    /** Anchored passport format. */
    public static final String PASSPORT =
            "^[A-Z0-9-]{5,20}$";

    /** Anchored hash format. */
    public static final String HASH =
            "^[0-9a-f]{64}$";

    private ApiPatterns() {
    }
}
