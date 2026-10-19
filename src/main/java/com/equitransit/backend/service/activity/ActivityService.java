/*
 * ActivityService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Scoped dashboards, durable notifications and redacted audit.
 */
package com.equitransit.backend.service.activity;


import com.equitransit.backend.dto.activity.AuditPage;
import com.equitransit.backend.dto.activity.TaskPage;
import com.equitransit.backend.dto.activity.TaskView;
import com.equitransit.backend.dto.activity.AuditView;
import com.equitransit.backend.dto.activity.DashboardOrder;
import com.equitransit.backend.dto.activity.DashboardResponse;
import com.equitransit.backend.dto.activity.StatusCount;
import com.equitransit.backend.dto.activity.NotificationPage;
import com.equitransit.backend.dto.activity.NotificationView;
import com.equitransit.backend.dto.activity.TimelinePoint;
import com.equitransit.backend.dto.activity.TimelineResponse;
import com.equitransit.backend.dto.activity.FinancialReport;
import com.equitransit.backend.dto.activity.EmailPreference;
import com.equitransit.backend.entity.order.OrderStatus;
import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.repository.activity.ActivityRepository;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.exception.order.OrderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/** Trusted role and ownership predicates apply to every operation. */
@Service
@PreAuthorize("isAuthenticated()")
@Transactional(timeout = 15)
public class ActivityService {

    private final ActivityRepository repository;
    private final AssessmentRepository staff;
    private final CurrentActorService actors;
    private final AuditRedactor redactor;
    private final ObjectMapper mapper;

    /**
     * @param repository bounded set-based queries
     * @param staff live active staff validation
     * @param actors authenticated identity
     * @param redactor also protects legacy audit rows on read
     * @param mapper strict JSON parser
     */
    public ActivityService(ActivityRepository repository,
            AssessmentRepository staff, CurrentActorService actors,
            AuditRedactor redactor, ObjectMapper mapper) {
        this.repository = repository;
        this.staff = staff;
        this.actors = actors;
        this.redactor = redactor;
        this.mapper = mapper;
    }

    /**
     * @param page zero-based page
     * @param size bounded size
     * @return only the current actor's orders/tasks; no cost DTO for customers
     */
    public DashboardResponse dashboard(int page, int size) {
        var args = scope(page, size);
        var orders = repository.rows("orders", args).stream().map(row ->
                new DashboardOrder(id(row, "OrderID"),
                        row.get("OrderNumber").toString(), OrderStatus.valueOf(
                                row.get("OrderStatus").toString()))).toList();
        var counts = repository.rows("counts", args).stream().map(row ->
                new StatusCount(OrderStatus.valueOf(row.get("OrderStatus")
                        .toString()), id(row, "Total"))).toList();

        return new DashboardResponse(args.get("role").toString(), counts,
                orders, repository.scalar("orderCount", args).longValueExact(),
                page, size);
    }

    /**
     * @param page bounded task page
     * @param size per-page limit
     * @return active case or driver-leg assignments for the live staff key
     */
    public TaskPage tasks(int page, int size) {
        var args = scope(page, size);
        var rows = repository.rows("tasks", args).stream().map(row ->
                new TaskView(id(row, "AssignmentID"), id(row, "OrderID"),
                        row.get("LegID") == null ? null : id(row, "LegID"),
                        row.get("RoleCode").toString(), at(row, "DueAt")))
                .toList();

        return new TaskPage(rows, repository.scalar("taskCount", args)
                .longValueExact(), page, size);
    }

    /**
     * @param page recipient-scoped page
     * @param size bounded size
     * @return no access to another UserID's messages
     */
    public NotificationPage notifications(int page, int size) {
        var args = scope(page, size);
        var rows = repository.rows("notifications", args).stream()
                .map(this::notification).toList();

        return new NotificationPage(rows, repository.scalar(
                "notificationCount", args).longValueExact(), page, size);
    }

    /**
     * @param id notification key; current actor is the only allowed recipient
     * @return idempotently marked read; CreatedAt/content remain unchanged
     */
    public NotificationView read(long id) {
        var args = scope(0, 1);

        args.put("id", id);
        if (repository.update("read", args) != 1) {
            throw missing();
        }
        return notification(repository.rows("notification", args).getFirst());
    }

    /**
     * @param order authorized order
     * @param page bounded timeline page
     * @param size bounded timeline size
     * @return observed milestones, not inferred operational authorization
     */
    public TimelineResponse timeline(long order, int page, int size) {
        var args = scope(page, size);

        args.put("order", order);
        if (repository.scalar("orderAccess", args).signum() == 0) {
            throw missing();
        }
        return new TimelineResponse(order, repository.rows("milestones", args)
                .stream().map(row -> new TimelinePoint(ActivityCode.valueOf(
                        row.get("Code").toString()), at(row, "OccurredAt")))
                .toList(), page, size);
    }

    /**
     * @param page newest first
     * @param size bounded size
     * @return Manager-only redacted history, including legacy snapshots
     */
    @PreAuthorize("hasRole('MANAGER')")
    public AuditPage audit(int page, int size) {
        var args = scope(page, size);

        manager(args);
        var rows = repository.rows("audits", args).stream().map(row ->
                new AuditView(id(row, "EventID"), row.get("UserID") == null
                        ? null : id(row, "UserID"),
                        row.get("EntityType").toString(), id(row, "EntityID"),
                        row.get("ActionCode").toString(), at(row, "OccurredAt"),
                        snapshot(row.get("ChangeSnapshot")))).toList();

        return new AuditPage(rows, repository.scalar("auditCount", args)
                .longValueExact(), page, size);
    }

    /** @return separate USD quote, claim and reconciled money aggregates */
    @PreAuthorize("hasRole('MANAGER')")
    public FinancialReport financialReport() {
        var args = scope(0, 1);

        manager(args);
        var row = repository.rows("financialReport", args).getFirst();

        return new FinancialReport("USD", money(row, "Quoted"),
                money(row, "Reported"), money(row, "BankNet"),
                money(row, "AllocatedNet"), money(row, "ActualCost"),
                id(row, "OperationalCount"), id(row, "ClosedCount"));
    }

    /** @return opt-in flag; null legacy values mean ordinary mail disabled */
    @PreAuthorize("hasRole('CUSTOMER')")
    public EmailPreference preference() {
        var args = scope(0, 1);

        customer(args);
        var rows = repository.rows("preference", args);

        return new EmailPreference(!rows.isEmpty() && Boolean.TRUE.equals(
                rows.getFirst().get("EmailNotificationsEnabled")));
    }

    /**
     * @param value explicit ordinary-email opt-in; security mail unaffected
     * @return persisted idempotent boolean preference
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    public EmailPreference preference(EmailPreference value) {
        var args = scope(0, 1);

        customer(args);
        args.put("enabled", value.enabled());
        if (repository.update("preferencePut", args) != 1) {
            throw missing();
        }
        return value;
    }

    private Map<String, Object> scope(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "ACTIVITY_PAGE_INVALID", "Invalid page or size.");
        }
        if (!repository.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ACTIVITY_SCHEMA_PENDING", "Deploy activity migration008.");
        }
        var actor = actors.current();

        if (!"CUSTOMER".equals(actor.roleCode())
                && (actor.staffId() == null
                        || !staff.activeStaff(actor.staffId(),
                                actor.roleCode()))) {
            throw new OrderException(HttpStatus.FORBIDDEN,
                    "ACTIVITY_FORBIDDEN", "Active staff required.");
        }
        var args = new HashMap<String, Object>();

        args.put("user", actor.userId());
        args.put("customer", actor.customerId());
        args.put("staff", actor.staffId());
        args.put("role", actor.roleCode());
        args.put("offset", (long) page * size);
        args.put("size", size);
        return args;
    }

    private void manager(Map<String, Object> args) {
        if (!"MANAGER".equals(args.get("role"))) {
            throw new OrderException(HttpStatus.FORBIDDEN,
                    "ACTIVITY_FORBIDDEN", "Manager required.");
        }
    }

    private void customer(Map<String, Object> args) {
        if (!"CUSTOMER".equals(args.get("role"))) {
            throw new OrderException(HttpStatus.FORBIDDEN,
                    "ACTIVITY_FORBIDDEN", "Customer required.");
        }
    }

    private NotificationView notification(Map<String, Object> row) {
        return new NotificationView(id(row, "NotificationID"),
                row.get("OrderID") == null ? null : id(row, "OrderID"),
                row.get("Title").toString(), row.get("Message").toString(),
                at(row, "CreatedAt"), at(row, "ReadAt"));
    }

    private String snapshot(Object value) {
        try {
            return redactor.redact(value == null ? null
                    : mapper.readTree(value.toString())).toString();
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            return "[REDACTED]";
        }
    }

    private String money(Map<String, Object> row, String key) {
        return ((BigDecimal) row.get(key)).setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private long id(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private OffsetDateTime at(Map<String, Object> row, String key) {
        var time = (Timestamp) row.get(key);

        return time == null ? null : time.toInstant().atOffset(ZoneOffset.UTC);
    }

    private OrderException missing() {
        return new OrderException(HttpStatus.NOT_FOUND,
                "ACTIVITY_NOT_FOUND", "Activity is unavailable.");
    }
}
