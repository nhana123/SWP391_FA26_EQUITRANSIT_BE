/*
 * AccountRoleRepository
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Read account roles.
 */
package com.equitransit.backend.repository.auth;


import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.equitransit.backend.entity.auth.AccountRole;


/** Reads configured account roles. */
public interface AccountRoleRepository
        extends JpaRepository<AccountRole, Long> {

    /**
     * Finds a configured role.
     * @param roleCode the role code
     * @return the matching role, if present
     */
    Optional<AccountRole> findByRoleCode(String roleCode);
}
