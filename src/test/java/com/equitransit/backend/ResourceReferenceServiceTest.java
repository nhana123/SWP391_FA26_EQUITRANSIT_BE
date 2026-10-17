/*
 * ResourceReferenceServiceTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete scoped resource and fit workflows.
 */
package com.equitransit.backend;

import com.equitransit.backend.dto.resource.ResourceModels.AssetState;
import com.equitransit.backend.dto.resource.ResourceModels.
        ResourceAssignedVehicle;
import com.equitransit.backend.repository.AssessmentRepository;
import com.equitransit.backend.repository.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.ResourceReferenceService;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Read roles and actual assignment lookups remain separate from writes. */
class ResourceReferenceServiceTest {
    private final ResourceRepository repository =
            mock(ResourceRepository.class);
    private final AssessmentRepository staff = mock(AssessmentRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final ResourceReferenceService service =
            new ResourceReferenceService(repository, staff, actors,
                    Clock.systemUTC());

    @Test
    void driverReadsOnlyAnActualAssignedVehicle() {
        actor("DRIVER");
        var vehicle = new ResourceAssignedVehicle(11, 2, "VEH-2", "TEST-2",
                2, "VENTILATED", AssetState.ACTIVE);
        when(repository.assignedVehicle(eq(11L), eq(3L), any()))
                .thenReturn(Optional.of(vehicle));
        assertThat(service.vehicle(11)).isEqualTo(vehicle);
        assertThatThrownBy(() -> service.vehicle(12))
                .hasMessageContaining("assigned vehicle was not found");
    }

    @Test
    void customerAndCoordinatorCannotReadSpecialistSources() {
        for (String role : java.util.List.of("CUSTOMER", "COORDINATOR")) {
            actor(role);
            assertThatThrownBy(() -> service.policies(0, 20))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void specialistHasReadAccessButNoDriverEntitlement() {
        actor("SPECIALIST");
        assertThat(service.policies(0, 20).items()).isEmpty();
        assertThatThrownBy(() -> service.vehicle(11))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revokedStaffCannotUseAnOtherwiseValidRole() {
        actor("DRIVER");
        when(staff.activeStaff(3, "DRIVER")).thenReturn(false);
        assertThatThrownBy(() -> service.vehicle(11))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void actor(String role) {
        when(actors.current()).thenReturn(new CurrentActor(1L, role,
                null, 3L));
        when(staff.activeStaff(3, role)).thenReturn(true);
        when(repository.ready()).thenReturn(true);
    }
}
