/*
 * CaseViews
 * Author: EquiTransit development team
 * Date: 2026-10-14
 * Copyright: EquiTransit project contributors.
 * Purpose: Map internal case projections to explicit HTTP contracts.
 */
package com.equitransit.backend.service;


import com.equitransit.backend.dto.response.CaseAssignmentResponse;
import com.equitransit.backend.dto.response.CaseDecisionResponse;
import com.equitransit.backend.dto.response.CaseStaffResponse;
import com.equitransit.backend.dto.response.CaseSummaryResponse;
import com.equitransit.backend.entity.CaseAssignmentRecord;
import com.equitransit.backend.entity.CaseDecisionRecord;
import com.equitransit.backend.entity.CaseStaffRecord;
import com.equitransit.backend.entity.CaseSummaryRecord;


/** Mapping stays in the service layer; repositories know no HTTP DTOs. */
public final class CaseViews {

    private CaseViews() {
    }

    /**
     * Maps an immutable decision projection.
     * @param record internal database projection
     * @return explicit assessment view
     */
    public static CaseDecisionResponse decision(CaseDecisionRecord record) {
        return new CaseDecisionResponse(record.decisionId(),
                record.revisionId(), record.managerId(), record.decision(),
                record.reason(), record.decidedAt());
    }

    /**
     * Maps historical responsibility metadata.
     * @param record internal database projection
     * @return explicit assignment view
     */
    public static CaseAssignmentResponse assignment(
            CaseAssignmentRecord record) {
        return new CaseAssignmentResponse(record.assignmentId(),
                record.staffId(), record.role(), record.assignedAt(),
                record.endedAt(), record.assignedBy(), record.dueAt(),
                record.assignmentNote());
    }

    /**
     * Maps queue metadata without customer private draft contents.
     * @param record internal database projection
     * @return explicit queue item
     */
    public static CaseSummaryResponse summary(CaseSummaryRecord record) {
        return new CaseSummaryResponse(record.orderId(), record.orderNumber(),
                record.orderStatus(), record.recordVersion(),
                record.currentRevisionId(), record.originCountryCode(),
                record.destinationCountryCode(), record.deliveryDeadline());
    }

    /**
     * Shows workload while distinguishing missing calendar configuration.
     * @param record active staff projection
     * @return eligible staff display, no account credentials
     */
    public static CaseStaffResponse staff(CaseStaffRecord record) {
        return new CaseStaffResponse(record.staffId(), record.fullName(),
                record.role(), record.activeCases(), "NOT_CONFIGURED");
    }
}
