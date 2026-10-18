/*
 * StaffModels
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Define private Manager staff administration contracts.
 */
package com.equitransit.backend.dto.staff;

import com.equitransit.backend.dto.validation.OptionalText;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** StaffID and UserID are distinct; creation never creates a Customer. */
public final class StaffModels {
    private StaffModels() { }

    /** The only provisionable internal account roles. */
    public enum StaffRole { MANAGER, SPECIALIST, COORDINATOR, DRIVER }

    /** Account and staff states are changed together by the status command. */
    public enum StaffStatus { ACTIVE, INACTIVE, LOCKED }

    /** Append-only staff audit actions, never customer timeline milestones. */
    public enum StaffAction {
        STAFF_CREATED, STAFF_UPDATED, STAFF_ROLE_CHANGED, STAFF_STATUS_CHANGED
    }

    /** Create checks the acting Manager version; edits check target version. */
    public record StaffCommand(
            @Schema(example = "000000000001A2B3")
            @NotBlank @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion,
            @Schema(example = "e4324ce2-f726-44e4-a655-a692e50405bd")
            @NotNull UUID operationId) { }

    /** Full replacement of editable profile and internal role. */
    public record StaffProfileInput(
            @Schema(example = "specialist@equitransit.example")
            @NotBlank @Email @Size(min = 1, max = 255) String email,
            @Schema(example = "Alex Nguyen")
            @NotBlank @Size(min = 1, max = 150) String fullName,
            @Schema(example = "+84901234567")
            @Size(min = 1, max = 30) @OptionalText
            @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phone,
            @Schema(example = "SPECIALIST") @NotNull StaffRole role,
            @Schema(example = "EMP-2026-001")
            @NotBlank @Size(min = 1, max = 50) String employeeCode,
            @Schema(example = "Compliance")
            @Size(min = 1, max = 100) @OptionalText String department,
            @Schema(example = "Equine export documentation")
            @Size(min = 1, max = 200) @OptionalText String specialization) { }

    /** Initial password is input-only; it is never returned or audited raw. */
    public record StaffCreateRequest(
            @Schema(example = StaffExamples.COMMAND)
            @NotNull @Valid StaffCommand command,
            @Schema(example = StaffExamples.PROFILE)
            @NotNull @Valid StaffProfileInput profile,
            @Schema(example = "ExamplePass!2026", format = "password",
                    accessMode = Schema.AccessMode.WRITE_ONLY)
            @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            @NotBlank @Size(min = 12, max = 72) String initialPassword) {
        /** @return no secret material, even in accidental diagnostic text */
        @Override
        public String toString() {
            return "StaffCreateRequest[REDACTED]";
        }
    }

    /** Profile update does not reset passwords or activate accounts. */
    public record StaffUpdateRequest(
            @Schema(example = StaffExamples.COMMAND)
            @NotNull @Valid StaffCommand command,
            @Schema(example = StaffExamples.PROFILE)
            @NotNull @Valid StaffProfileInput profile) { }

    /** State change retains history and never revives ended assignments. */
    public record StaffStatusRequest(
            @Schema(example = StaffExamples.COMMAND)
            @NotNull @Valid StaffCommand command,
            @Schema(example = "INACTIVE") @NotNull StaffStatus status,
            @Schema(example = "Staff member transferred to another team.")
            @NotBlank @Size(min = 1, max = 500) String reason) { }

    /** Private staff view; no credentials or proof contents are exposed. */
    public record StaffView(
            @Schema(example = "31") long staffId,
            @Schema(example = "21") long userId,
            @Schema(example = "specialist@equitransit.example")
            @Size(min = 1, max = 255) String email,
            @Schema(example = "Alex Nguyen")
            @Size(min = 1, max = 150) String fullName,
            @Schema(example = "+84901234567")
            @Size(min = 1, max = 30) String phone,
            @Schema(example = "SPECIALIST") StaffRole role,
            @Schema(example = "ACTIVE") StaffStatus accountStatus,
            @Schema(example = "ACTIVE") StaffStatus staffStatus,
            @Schema(example = "EMP-2026-001")
            @Size(min = 1, max = 50) String employeeCode,
            @Schema(example = "Compliance")
            @Size(min = 1, max = 100) String department,
            @Schema(example = "Equine export documentation")
            @Size(min = 1, max = 200) String specialization,
            @Schema(example = "000000000001A2B3")
            @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String recordVersion,
            @Schema(example = "1") long activeOrderAssignments,
            @Schema(example = "0") long activeTripAssignments,
            @Schema(example = "1") long activeAssignments) { }

    /** Creation uses actorRecordVersion, not an invented target version. */
    public record StaffPage(
            @Schema(example = "[]") List<StaffView> items,
            @Schema(example = "11") long actorStaffId,
            @Schema(example = "000000000001A2B3")
            @Size(min = 16, max = 16)
            @Pattern(regexp = "^[0-9a-fA-F]{16}$") String actorRecordVersion,
            @Schema(example = "0") int page,
            @Schema(example = "20") int size,
            @Schema(example = "1") long totalElements) { }
}
