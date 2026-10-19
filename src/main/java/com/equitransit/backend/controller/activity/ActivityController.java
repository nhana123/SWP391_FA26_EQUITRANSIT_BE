/*
 * ActivityController
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.controller.activity;


import com.equitransit.backend.dto.activity.AuditPage;
import com.equitransit.backend.dto.activity.TaskPage;
import com.equitransit.backend.dto.activity.DashboardResponse;
import com.equitransit.backend.dto.activity.NotificationPage;
import com.equitransit.backend.dto.activity.NotificationView;
import com.equitransit.backend.dto.activity.TimelineResponse;
import com.equitransit.backend.dto.activity.FinancialReport;
import com.equitransit.backend.dto.activity.EmailPreference;
import com.equitransit.backend.service.activity.ActivityService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;


/** Thin role-isolated activity transport; no public audit writer endpoint. */
@RestController
@SecurityRequirement(name = "bearerAuth")
@Validated
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class ActivityController {

    private final ActivityService service;

    /** @param service actor-scoped activity facade */
    public ActivityController(ActivityService service) {
        this.service = service;
    }

    /**
     * @param page zero-based page
     * @param size bounded page size
     * @return authorized, bounded dashboard records
     */
    @GetMapping("/api/dashboard")
    public DashboardResponse dashboard(
            @Parameter(schema = @Schema(type = "integer", example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(schema = @Schema(type = "integer", example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.dashboard(page, size);
    }

    /**
     * @param page zero-based page
     * @param size bounded page size
     * @return authorized, bounded notifications records
     */
    @GetMapping("/api/notifications")
    public NotificationPage notifications(
            @Parameter(schema = @Schema(type = "integer", example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(schema = @Schema(type = "integer", example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.notifications(page, size);
    }

    /**
     * @param page zero-based page
     * @param size bounded page size
     * @return authorized, bounded audit records
     */
    @GetMapping("/api/manager/audit-events")
    public AuditPage audit(
            @Parameter(schema = @Schema(type = "integer", example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(schema = @Schema(type = "integer", example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.audit(page, size);
    }

    /**
     * @param page zero-based page
     * @param size bounded task count
     * @return current staff case/leg tasks
     */
    @GetMapping("/api/dashboard/tasks")
    public TaskPage tasks(
            @Parameter(schema = @Schema(type = "integer", example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(schema = @Schema(type = "integer", example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.tasks(page, size);
    }

    /**
     * @param id recipient-owned notification
     * @return idempotently marked read
     */
    @PutMapping("/api/notifications/{id}/read")
    public NotificationView read(@PathVariable @Positive long id) {
        return service.read(id);
    }

    /**
     * @param orderId owned/actively assigned/managed parent
     * @param page zero-based page
     * @param size bounded page size
     * @return evidence-backed timeline, without internal costs or notes
     */
    @GetMapping("/api/activity/orders/{orderId}/timeline")
    public TimelineResponse timeline(@PathVariable @Positive long orderId,
            @Parameter(schema = @Schema(type = "integer", example = "0"))
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(schema = @Schema(type = "integer", example = "20"))
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.timeline(orderId, page, size);
    }

    /** @return Manager-only quote/claim/cash/allocation/cost aggregates */
    @GetMapping("/api/manager/reports/financial")
    public FinancialReport financial() {
        return service.financialReport();
    }

    /** @return current customer's ordinary mail opt-in only */
    @GetMapping("/api/customers/me/notification-preferences")
    public EmailPreference preference() {
        return service.preference();
    }

    /**
     * @param body explicit opt-in flag; does not disable security mail
     * @return persisted preference; repeated PUT is idempotent
     */
    @PutMapping("/api/customers/me/notification-preferences")
    public EmailPreference preference(
            @RequestBody @Valid EmailPreference body) {
        return service.preference(body);
    }
}
