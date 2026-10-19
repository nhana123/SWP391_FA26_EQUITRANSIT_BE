/*
 * StaffAdministrationService
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Govern staff lifecycle, assignment safety and idempotent writes.
 */
package com.equitransit.backend.service.staff;

import com.equitransit.backend.dto.staff.StaffModels.StaffAction;
import com.equitransit.backend.dto.staff.StaffModels.StaffCommand;
import com.equitransit.backend.dto.staff.StaffModels.StaffCreateRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffPage;
import com.equitransit.backend.dto.staff.StaffModels.StaffProfileInput;
import com.equitransit.backend.dto.staff.StaffModels.StaffRole;
import com.equitransit.backend.dto.staff.StaffModels.StaffStatus;
import com.equitransit.backend.dto.staff.StaffModels.StaffStatusRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffUpdateRequest;
import com.equitransit.backend.dto.staff.StaffModels.StaffView;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.staff.StaffAdministrationRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import com.equitransit.backend.service.auth.AuthService;
import com.equitransit.backend.service.activity.AuditWriter;

/** All staff writes share one transaction-owned global administration lock. */
@Service
@Validated
@PreAuthorize("hasRole('MANAGER')")
public class StaffAdministrationService {
    private final StaffAdministrationRepository repository;
    private final CurrentActorService actors;
    private final AuthService auth;
    private final AuditWriter audits;
    private final ObjectMapper mapper;
    private final SecretKey fingerprintKey;

    /** @param repository SQL adapter @param actors trusted actor resolver
     * @param auth adaptive password encoder @param audits redacting writer
     * @param mapper receipt codec @param key private HMAC key */
    public StaffAdministrationService(StaffAdministrationRepository repository,
            CurrentActorService actors, AuthService auth, AuditWriter audits,
            ObjectMapper mapper, SecretKey key) {
        this.repository = repository;
        this.actors = actors;
        this.auth = auth;
        this.audits = audits;
        this.mapper = mapper;
        fingerprintKey = key;
    }

    /** @param role filter @param status account filter @param page zero based
     * @param size bounded @return profile page and creation CAS version */
    @Transactional(readOnly = true)
    public StaffPage list(StaffRole role, StaffStatus status, int page,
            int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "INVALID_PAGINATION", "Use page >= 0 and size 1..100.");
        }
        CurrentActor actor = authorize(false);
        var manager = repository.get(actor.staffId());
        return new StaffPage(repository.list(role, status, page, size),
                manager.staffId(), manager.recordVersion(), page, size,
                repository.count(role, status));
    }

    /** @param staff domain key @return private Manager view */
    @Transactional(readOnly = true)
    public StaffView get(long staff) {
        authorize(false);
        return repository.get(staff);
    }

    /** @param body Manager version, credentials and profile @return receipt */
    @Transactional(timeout = 20)
    public StaffView create(@Valid StaffCreateRequest body) {
        var actor = authorize(true);
        var hash = fingerprint("CREATE:0", body, body.initialPassword());
        var replay = replay(actor, body.command(), hash);
        if (replay != null) {
            return replay;
        }
        version(repository.get(actor.staffId()), body.command());
        duplicate(body.profile(), 0);
        long staff;
        try {
            staff = repository.create(body.profile(),
                    auth.encodeStaffPassword(body.initialPassword()));
        } catch (DataIntegrityViolationException error) {
            throw conflict("STAFF_DUPLICATE", "Email or employee code exists.");
        }
        return finish(actor, staff, StaffAction.STAFF_CREATED, body.command(),
                hash, null, "[REDACTED]");
    }

    /** @param staff target @param body replacement profile @return receipt */
    @Transactional(timeout = 20)
    public StaffView update(long staff, @Valid StaffUpdateRequest body) {
        var actor = authorize(true);
        var hash = fingerprint("UPDATE:" + staff, body, "");
        var replay = replay(actor, body.command(), hash);
        if (replay != null) {
            return replay;
        }
        repository.lockTarget(staff);
        var before = repository.get(staff);
        version(before, body.command());
        boolean roleChange = before.role() != body.profile().role();
        if (roleChange) {
            protectManager(before);
            if (repository.incompatible(staff, body.profile().role()) > 0) {
                throw conflict("STAFF_ASSIGNMENT_ROLE_CONFLICT",
                        "End or reassign incompatible active work first.");
            }
        }
        duplicate(body.profile(), before.userId());
        try {
            repository.update(staff, body.profile(),
                    body.command().recordVersion());
        } catch (DataIntegrityViolationException error) {
            throw conflict("STAFF_DUPLICATE", "Email or employee code exists.");
        }
        var action = roleChange ? StaffAction.STAFF_ROLE_CHANGED
                : StaffAction.STAFF_UPDATED;
        return finish(actor, staff, action, body.command(), hash,
                before.role(), null);
    }

    /** @param staff target @param body state command @return receipt */
    @Transactional(timeout = 20)
    public StaffView status(long staff, @Valid StaffStatusRequest body) {
        var actor = authorize(true);
        var hash = fingerprint("STATUS:" + staff, body, "");
        var replay = replay(actor, body.command(), hash);
        if (replay != null) {
            return replay;
        }
        repository.lockTarget(staff);
        var before = repository.get(staff);
        version(before, body.command());
        if (body.status() != StaffStatus.ACTIVE) {
            protectManager(before);
            if (repository.driving(staff)) {
                throw conflict("DRIVER_IN_TRANSIT",
                        "Reassign the in-transit driver before disabling.");
            }
            repository.endAssignments(staff);
        }
        repository.status(staff, body.status(), body.command().recordVersion());
        return finish(actor, staff, StaffAction.STAFF_STATUS_CHANGED,
                body.command(), hash, before.role(), body.reason());
    }

    private CurrentActor authorize(boolean write) {
        if (!repository.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "STAFF_SCHEMA_PENDING", "Deploy staff migration 013.");
        }
        if (write) {
            repository.lock();
        }
        var actor = actors.current();
        long staff = repository.manager(actor.userId());
        return new CurrentActor(actor.userId(), "MANAGER", null, staff);
    }

    private void protectManager(StaffView before) {
        if (before.role() == StaffRole.MANAGER
                && before.accountStatus() == StaffStatus.ACTIVE
                && before.staffStatus() == StaffStatus.ACTIVE
                && repository.otherManagers(before.staffId()) == 0) {
            throw conflict("LAST_ACTIVE_MANAGER_PROTECTED",
                    "At least one other active Manager must remain.");
        }
    }

    private void version(StaffView view, StaffCommand command) {
        if (!view.recordVersion().equalsIgnoreCase(command.recordVersion())) {
            throw conflict("STAFF_VERSION_CONFLICT", "Reload the staff view.");
        }
    }

    private void duplicate(StaffProfileInput profile, long except) {
        if (repository.duplicate(profile, except)) {
            throw conflict("STAFF_DUPLICATE", "Email or employee code exists.");
        }
    }

    private StaffView replay(CurrentActor actor, StaffCommand command,
            String hash) {
        var prior = repository.operation(actor.userId(),
                command.operationId());
        if (prior.isEmpty()) {
            return null;
        }
        var row = prior.getFirst();
        if (!hash.equals(row.get("RequestHash"))) {
            throw conflict("IDEMPOTENCY_CONFLICT",
                    "UUID was already used with different input.");
        }
        try {
            return mapper.readValue(row.get("ReceiptJSON").toString(),
                    StaffView.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Invalid stored staff receipt.");
        }
    }

    private StaffView finish(CurrentActor actor, long staff, StaffAction action,
            StaffCommand command, String hash, StaffRole previous,
            String sensitiveText) {
        var result = repository.get(staff);
        var snapshot = mapper.createObjectNode().put("schemaVersion", 1)
                .put("staffId", staff).put("userId", result.userId())
                .put("role", result.role().name())
                .put("accountStatus", result.accountStatus().name())
                .put("staffStatus", result.staffStatus().name())
                .put("recordVersion", result.recordVersion())
                .put("operationId", command.operationId().toString());
        if (previous != null) {
            snapshot.put("previousRole", previous.name());
        }
        if (sensitiveText != null) {
            snapshot.put("privateContext", sensitiveText);
        }
        audits.appendStaff(actor, staff, action,
                command.operationId(), snapshot);
        try {
            repository.save(actor.userId(), command.operationId(), hash,
                    mapper.writeValueAsString(result));
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot encode staff receipt.");
        }
        return result;
    }

    private String fingerprint(String scope, Object body, String password) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(fingerprintKey);
            mac.update((scope + "\n").getBytes(StandardCharsets.UTF_8));
            mac.update(mapper.writeValueAsBytes(body));
            mac.update(("\n" + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException | JsonProcessingException error) {
            throw new IllegalStateException("Cannot fingerprint staff input.");
        }
    }

    private OrderException conflict(String code, String message) {
        return new OrderException(HttpStatus.CONFLICT, code, message);
    }
}
