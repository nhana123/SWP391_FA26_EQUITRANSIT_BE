/*
 * CommercialReadiness
 * Author: EquiTransit development team
 * Date: 2026-10-16
 * Copyright: EquiTransit project contributors.
 * Purpose: Share quotation commitment without asserting payment or departure.
 */
package com.equitransit.backend.entity.quote;


import java.math.BigDecimal;
import java.time.OffsetDateTime;


/**
 * One accepted schedule, including schedules with no PaymentRequest yet.
 * @param scheduleId immutable schedule key
 * @param amountDue total duty, not merely a generated request
 * @param paidAmount net reconciled allocation for this schedule
 * @param dueAt null means unresolved, never an automatically passing gate
 */
public record FinancialObligation(long scheduleId, BigDecimal amountDue,
        BigDecimal paidAmount, OffsetDateTime dueAt) {
}
