/*
 * StaffAdministrationPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify staff command guards and secret handling without a DB.
 */
package com.equitransit.backend;

import com.equitransit.backend.dto.staff.StaffModels.StaffCommand;
import com.equitransit.backend.dto.staff.StaffModels.StaffCreateRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffProfileInput;
import com.equitransit.backend.dto.staff.StaffModels.StaffRole;
import com.equitransit.backend.dto.staff.StaffModels.StaffStatus;
import com.equitransit.backend.dto.staff.StaffModels.StaffStatusRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffUpdateRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffView;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.StaffAdministrationRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.AuditRedactor;
import com.equitransit.backend.service.AuditWriter;
import com.equitransit.backend.service.AuthService;
import com.equitransit.backend.service.StaffAdministrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/** Unit policies complement native concurrency and live bearer tests. */
public class StaffAdministrationPolicyTest {
    private final StaffAdministrationRepository repository =
            mock(StaffAdministrationRepository.class);
    private final CurrentActorService actors = mock(CurrentActorService.class);
    private final AuthService auth = mock(AuthService.class);
    private final AuditWriter audits = mock(AuditWriter.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private StaffAdministrationService service;

    /** Configure one active Manager and a current target rowversion. */
    @BeforeEach
    public void setup() {
        service = new StaffAdministrationService(repository, actors, auth,
                audits, mapper, new SecretKeySpec(new byte[32], "HmacSHA256"));
        when(repository.ready()).thenReturn(true);
        when(actors.current()).thenReturn(
                new CurrentActor(1L, "MANAGER", null, 11L));
        when(repository.manager(1)).thenReturn(11L);
        when(repository.get(11)).thenReturn(view(StaffRole.MANAGER));
        when(repository.operation(eq(1L), any())).thenReturn(List.of());
    }

    /** Last active Manager may neither be disabled nor demoted. */
    @Test
    public void lastManagerAndStaleVersionAreProtected() {
        var command = command();
        assertCode(() -> service.status(11, new StaffStatusRequest(command,
                StaffStatus.LOCKED, "Leaving team")),
                "LAST_ACTIVE_MANAGER_PROTECTED");
        assertCode(() -> service.update(11, new StaffUpdateRequest(command,
                profile(StaffRole.DRIVER))), "LAST_ACTIVE_MANAGER_PROTECTED");
        assertCode(() -> service.status(11, new StaffStatusRequest(
                new StaffCommand("000000000000FFFF", UUID.randomUUID()),
                StaffStatus.ACTIVE, "Activate")), "STAFF_VERSION_CONFLICT");
        verify(repository, never()).endAssignments(anyLong());
    }

    /** Assignment checks precede every destructive state transition. */
    @Test
    public void drivingAndIncompatibleRoleBlockChanges() {
        when(repository.get(11)).thenReturn(view(StaffRole.DRIVER));
        when(repository.driving(11)).thenReturn(true);
        assertCode(() -> service.status(11, new StaffStatusRequest(command(),
                StaffStatus.INACTIVE, "Leaving")), "DRIVER_IN_TRANSIT");
        when(repository.incompatible(11, StaffRole.SPECIALIST)).thenReturn(1L);
        assertCode(() -> service.update(11, new StaffUpdateRequest(command(),
                profile(StaffRole.SPECIALIST))),
                "STAFF_ASSIGNMENT_ROLE_CONFLICT");
    }

    /** A committed retry is returned before stale-version validation. */
    @Test
    public void replayDoesNotHashPasswordsAsOrdinaryShaOrRecreate()
            throws Exception {
        var body = new StaffCreateRequest(command(),
                profile(StaffRole.DRIVER), "ExamplePass!2026");
        when(repository.create(any(), anyString())).thenReturn(11L);
        when(auth.encodeStaffPassword(body.initialPassword()))
                .thenReturn("{bcrypt}adaptive");
        var hash = org.mockito.ArgumentCaptor.forClass(String.class);
        service.create(body);
        verify(repository).save(eq(1L), eq(body.command().operationId()),
                hash.capture(), anyString());
        when(repository.operation(1, body.command().operationId()))
                .thenReturn(List.of(Map.of("RequestHash", hash.getValue(),
                        "ReceiptJSON", mapper.writeValueAsString(
                                view(StaffRole.MANAGER)))));
        assertThat(service.create(body)).isEqualTo(view(StaffRole.MANAGER));
        assertCode(() -> service.create(new StaffCreateRequest(body.command(),
                body.profile(), "DifferentPass!2026")), "IDEMPOTENCY_CONFLICT");
        verify(repository).create(any(), eq("{bcrypt}adaptive"));
    }

    /** Optional text, E.164 and secret redaction stay strict. */
    @Test
    public void invalidTextAndCredentialExposureAreRejected() throws Exception {
        var invalid = new StaffProfileInput("bad", "", "0901234567",
                StaffRole.DRIVER, "", "", " ");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(invalid))
                    .extracting(v -> v.getPropertyPath().toString())
                    .contains("email", "fullName", "phone", "employeeCode",
                            "department", "specialization");
        }
        var body = new StaffCreateRequest(command(), profile(StaffRole.DRIVER),
                "ExamplePass!2026");
        assertThat(mapper.writeValueAsString(body)).doesNotContain(
                "ExamplePass!2026", "initialPassword");
        assertThat(body.toString()).doesNotContain("ExamplePass!2026");
        var redacted = new AuditRedactor(mapper).redact(mapper.readTree(
                "{\"password\":\"ExamplePass!2026\",\"role\":\"MANAGER\"}"));
        assertThat(redacted.toString()).contains("[REDACTED]", "MANAGER")
                .doesNotContain("ExamplePass!2026");
    }

    private StaffView view(StaffRole role) {
        return new StaffView(11, 1, "staff@example.invalid", "Test", null,
                role, StaffStatus.ACTIVE, StaffStatus.ACTIVE, "EMP-TEST",
                null, null, "0000000000000001", 0, 0, 0);
    }

    private StaffCommand command() {
        return new StaffCommand("0000000000000001", UUID.randomUUID());
    }

    private StaffProfileInput profile(StaffRole role) {
        return new StaffProfileInput("staff@example.invalid", "Test", null,
                role, "EMP-TEST", null, null);
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(
                OrderException.class, error ->
                        assertThat(error.getCode()).isEqualTo(code));
    }
}
