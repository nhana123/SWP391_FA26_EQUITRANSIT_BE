package com.equitransit.backend.dto.compliance.response;


import java.time.OffsetDateTime;
import java.util.List;


/**
 * Public F08 result without storage keys.
 * @param orderId the orderId value
 * @param revisionId the revisionId value
 * @param phaseCode the phaseCode value
 * @param asOf the asOf value
 * @param legalGatePass the legalGatePass value
 * @param blockerPhases the blockerPhases value
 * @param requiredQuarantineDays the requiredQuarantineDays value
 * @param blockerCodes the blockerCodes value
 */
public record ComplianceReadinessResponse(
        long orderId,
        long revisionId,
        String phaseCode,
        OffsetDateTime asOf,
        boolean legalGatePass,
        List<String> blockerPhases,
        Integer requiredQuarantineDays,
        List<String> blockerCodes) {
}
