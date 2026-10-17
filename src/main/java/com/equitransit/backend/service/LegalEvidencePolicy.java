/*
 * LegalEvidencePolicy
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.service;


import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import com.equitransit.backend.exception.OrderException;
import org.springframework.http.HttpStatus;


/** Pure validation shared by API mutations and gate evaluation. */
public final class LegalEvidencePolicy {

    private LegalEvidencePolicy() {
    }

    /** @param condition invariant @param code safe public blocker */
    public static void require(boolean condition, String code) {
        if (!condition) {
            throw new OrderException(HttpStatus.CONFLICT, code,
                    "Legal evidence is incomplete, stale or unavailable.");
        }
    }

    /** @param value sourced URL, never fetched @return valid HTTPS URL */
    public static boolean sourceUrl(String value) {
        try {
            var uri = URI.create(value);

            return "https".equals(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    /**
     * Expiry dates include the whole UTC calendar day.
     * @param issued issue date
     * @param expires expiry date
     * @param revoked whether revoked
     * @param latest whether the submitted immutable file is still current
     * @param approved exact latest submission review
     * @param asOf explicit gate instant
     * @return valid evidence at that instant
     */
    public static boolean documentValid(LocalDate issued, LocalDate expires,
            boolean revoked, boolean latest, boolean approved,
            OffsetDateTime asOf) {
        return issued != null && expires != null && !revoked && latest
                && approved && !issued.isAfter(asOf.withOffsetSameInstant(
                        ZoneOffset.UTC).toLocalDate())
                && expires.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                        .isAfter(asOf);
    }
}
