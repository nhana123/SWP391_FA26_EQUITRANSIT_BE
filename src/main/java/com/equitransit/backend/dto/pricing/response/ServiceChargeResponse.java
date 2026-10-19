/*
 * ServiceChargeResponse
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement sourced service selection and transparent estimates.
 */
package com.equitransit.backend.dto.pricing.response;


import io.swagger.v3.oas.annotations.media.Schema;
import com.equitransit.backend.dto.common.ApiExamples;
import com.equitransit.backend.dto.common.ApiPatterns;
import java.util.List;


/**
 * Explicit customer service contract, no supplier costs or secrets.
 * @param code unique charge identity
 * @param uiSection one of seven booking UI sections
 * @param costGroup physical cost group
 * @param label customer display label
 * @param mandatory whether this is mandatory when applicable
 * @param selected explicit request or mandatory disclosure
 * @param unit actual unit
 * @param quantity known source quantity or null
 * @param unitRate published USD unit rate or null
 * @param amount known estimate or null
 * @param pricingStatus explicit pending/included/estimate state
 * @param tariffId published source or null
 * @param tariffVersion published version or null
 * @param formula transparent calculation basis
 * @param blockers safe pending reasons
 */
@Schema(example = ApiExamples.CHARGE)
public record ServiceChargeResponse(
        @Schema(
                example = "AIR_STALL")
        String code,
        @Schema(
                example = "ADDITIONAL_CHARGES",
                allowableValues = {
                        "TRANSPORTATION",
                        "AIR_STALL",
                        "HORSE_CARE",
                        "VETERINARY",
                        "QUARANTINE",
                        "INSURANCE",
                        "ADDITIONAL_CHARGES"})
        String uiSection,
        @Schema(
                example = "DOCUMENTATION_QUARANTINE",
                allowableValues = {
                        "TRANSPORTATION",
                        "HORSE_ACCOMMODATION",
                        "HORSE_CARE",
                        "VETERINARY",
                        "DOCUMENTATION_QUARANTINE",
                        "AIRPORT_CUSTOMS",
                        "INSURANCE",
                        "ADDITIONAL_CHARGES"})
        String costGroup,
        @Schema(
                example = "Document coordination")
        String label,
        @Schema(
                example = "true")
        boolean mandatory,
        @Schema(
                example = "true")
        boolean selected,
        @Schema(
                example = "ROUTE_KM",
                allowableValues = {
                        "KM",
                        "SLOT_KM",
                        "SHIPMENT",
                        "INCLUDED",
                        "HORSE_KM",
                        "PERSON_DAY",
                        "HORSE_NIGHT",
                        "HORSE_VISIT",
                        "TEST_SAMPLE",
                        "DOSE",
                        "CERTIFICATE",
                        "HORSE_DAY",
                        "POLICY",
                        "ROUTE_KM",
                        "HORSE_AIRPORT",
                        "DECLARATION",
                        "ASSESSMENT",
                        "HOUR",
                        "PERSON_LEG"})
        String unit,
        @Schema(
                example = "1100.00",
                nullable = true,
                pattern = "^\\d+(\\.\\d{1,3})?$",
                type = "string")
        String quantity,
        @Schema(
                example = "0.50",
                nullable = true,
                pattern = ApiPatterns.DECIMAL,
                type = "string")
        String unitRate,
        @Schema(
                example = "550.00",
                nullable = true,
                pattern = ApiPatterns.DECIMAL,
                type = "string")
        String amount,
        @Schema(
                example = "ESTIMATED",
                allowableValues = {
                        "ESTIMATED",
                        "NOT_SELECTED",
                        "NOT_REQUIRED",
                        "INCLUDED",
                        "EXTERNAL_PROVIDER",
                        "EVENT_ONLY",
                        "RATE_PENDING",
                        "QUANTITY_PENDING",
                        "RATE_AND_QUANTITY_PENDING",
                        "AMOUNT_REVIEW_REQUIRED",
                        "SELECTION_REQUIRED",
                        "AIRFARE_PENDING",
                        "INSURER_TERMS_PENDING",
                        "AUTHORITY_ASSESSMENT_PENDING",
                        "BUNDLE_REVIEW_REQUIRED",
                        "SUPPLIER_CONFIRMATION_PENDING"})
        String pricingStatus,
        @Schema(
                example = "31",
                nullable = true,
                minimum = "1")
        Long tariffId,
        @Schema(
                example = "1",
                nullable = true,
                minimum = "1")
        Integer tariffVersion,
        @Schema(
                example = "(ROAD km + AIR km) × rate")
        String formula,
        @Schema(
                example = "[]")
        List<String> blockers) {
}
