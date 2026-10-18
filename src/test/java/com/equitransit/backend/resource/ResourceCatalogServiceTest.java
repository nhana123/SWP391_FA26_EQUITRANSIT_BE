/*
 * ResourceCatalogServiceTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Complete scoped resource and fit workflows.
 */
package com.equitransit.backend.resource;

import com.equitransit.backend.dto.resource.ResourceModels.PolicyState;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceCommand;
import com.equitransit.backend.dto.resource.ResourceModels.ResourceKind;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyData;
import com.equitransit.backend.dto.resource.ResourceModels.ResourcePolicyView;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.resource.ResourceRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.resource.ResourceCatalogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Manager approval, immutable families and retries use real service rules. */
class ResourceCatalogServiceTest {
    private final ResourceRepository repository =
            mock(ResourceRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final AssessmentRepository staff = mock(AssessmentRepository.class);
    private final OffsetDateTime now =
            OffsetDateTime.parse("2027-01-01T00:00:00Z");
    private ResourceCatalogService service;
    private final ResourceCommand create = new ResourceCommand(
            UUID.randomUUID(), null);

    @BeforeEach
    void setup() {
        var mapper = new ObjectMapper().findAndRegisterModules();
        service = new ResourceCatalogService(repository, actors, staff,
                new OrderJsonCodec(mapper), mapper,
                Clock.fixed(now.toInstant(), ZoneOffset.UTC));
        when(repository.ready()).thenReturn(true);
        when(actors.current()).thenReturn(new CurrentActor(1L, "MANAGER",
                null, 3L));
        when(staff.activeStaff(3, "MANAGER")).thenReturn(true);
        when(repository.activeSupplier(any())).thenReturn(true);
        when(repository.operation(anyLong(), any())).thenReturn(List.of());
    }

    @Test
    void approvalRejectsMissingSourceOrNonpositiveLimits() {
        for (var data : List.of(policy("FAMILY", "0", "https://example.com",
                PolicyState.APPROVED), policy("FAMILY", "700", null,
                        PolicyState.APPROVED))) {
            assertThatThrownBy(() -> service.write(ResourceKind.POLICY, 0,
                    create, data, ResourcePolicyView.class))
                    .hasMessageContaining("Approval needs positive limits");
        }
        verify(repository, never()).save(any(), anyLong(), any(), anyInt(),
                any(), any());
    }

    @Test
    void approvalTakesReviewerFromLiveManagerAndWritesAuditReceipt() {
        var data = policy("FAMILY", "700", "https://example.com",
                PolicyState.APPROVED);
        var receipt = new ResourcePolicyView(11, "0000000000000001", 2,
                3L, now, data);
        when(repository.nextVersion("FAMILY")).thenReturn(2);
        when(repository.save(ResourceKind.POLICY, 0, data, 2, 3L, now))
                .thenReturn(11L);
        when(repository.get(ResourceKind.POLICY, 11, ResourcePolicyView.class))
                .thenReturn(receipt);
        service.write(ResourceKind.POLICY, 0, create, data,
                ResourcePolicyView.class);
        verify(repository).retirePolicies(11, "FAMILY");
        verify(repository).finish(eq(1L), eq(create.operationId()), any(),
                eq(receipt), eq(ResourceKind.POLICY), eq(11L));
    }

    @Test
    void draftPolicyCannotChangeFamilyOrBypassVersionCheck() {
        var old = policy("FAMILY", "700", null, PolicyState.DRAFT);
        when(repository.get(ResourceKind.POLICY, 11, ResourcePolicyView.class))
                .thenReturn(new ResourcePolicyView(11, "0000000000000001",
                        2, null, null, old));
        assertThatThrownBy(() -> service.write(ResourceKind.POLICY, 11,
                new ResourceCommand(UUID.randomUUID(), "0000000000000001"),
                policy("OTHER", "700", null, PolicyState.DRAFT),
                ResourcePolicyView.class)).hasMessageContaining("new family");
        assertThatThrownBy(() -> service.write(ResourceKind.POLICY, 11,
                create, old, ResourcePolicyView.class))
                .hasMessageContaining("resource version");
    }

    @Test
    void approvedContentsRemainImmutableWhenArchiving() {
        var old = policy("FAMILY", "700", "https://example.com",
                PolicyState.APPROVED);
        when(repository.get(ResourceKind.POLICY, 11, ResourcePolicyView.class))
                .thenReturn(new ResourcePolicyView(11, "0000000000000001",
                        1, 3L, now, old));
        assertThatThrownBy(() -> service.write(ResourceKind.POLICY, 11,
                new ResourceCommand(UUID.randomUUID(), "0000000000000001"),
                policy("FAMILY", "900", "https://example.com",
                        PolicyState.INACTIVE), ResourcePolicyView.class))
                .hasMessageContaining("do not edit approval");
    }

    private ResourcePolicyData policy(String code, String weight, String url,
            PolicyState state) {
        return new ResourcePolicyData(code, null, null, BigDecimal.ONE,
                new BigDecimal(weight), new BigDecimal("190"),
                new BigDecimal("90"), now, now.plusYears(1), url, null,
                state);
    }
}
