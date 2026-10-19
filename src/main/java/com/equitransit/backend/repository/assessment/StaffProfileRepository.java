/*
 * StaffProfileRepository
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: EquiTransit project contributors.
 * Purpose: Resolve staff primary keys from authenticated account identity.
 */
package com.equitransit.backend.repository.assessment;


import java.util.Optional;
import org.springframework.data.repository.Repository;
import com.equitransit.backend.entity.assessment.StaffProfile;


/** Exposes only the lookup needed by the shared actor resolver. */
public interface StaffProfileRepository
        extends Repository<StaffProfile, Long> {

    /**
     * Finds a staff profile linked to a server-validated account.
     * @param userId the authenticated account identifier
     * @return its staff profile, if present
     */
    Optional<StaffProfile> findByUserUserId(Long userId);
}
