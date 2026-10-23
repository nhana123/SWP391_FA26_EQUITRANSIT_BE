/* Internal F14 clearance lookup; never exposes F08 documents. */
package com.equitransit.backend.service;

import com.equitransit.backend.repository.ComplianceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The calling handover service owns order and assignment authorization. */
@Service
public class QuarantineReleaseGate {

    private final ComplianceRepository data;

    /** @param data internal legal evidence adapter */
    public QuarantineReleaseGate(ComplianceRepository data) {
        this.data = data;
    }

    /** @param orderId authorized order @return current, unexpired clearance */
    @Transactional(readOnly = true)
    public boolean passed(long orderId) {
        return data.quarantineReleasePassed(orderId);
    }
}
