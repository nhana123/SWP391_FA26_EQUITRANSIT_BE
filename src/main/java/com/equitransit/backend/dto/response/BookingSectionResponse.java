/*
 * BookingSectionResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Publish the seven booking sections without default selections.
 */
package com.equitransit.backend.dto.response;


import java.util.List;


/**
 * Stable section display contract; option codes are not selected implicitly.
 * @param code stable section key
 * @param title customer display heading
 * @param options allowed/requestable preference codes, not reservations
 */
public record BookingSectionResponse(String code, String title,
        List<String> options) {
}
