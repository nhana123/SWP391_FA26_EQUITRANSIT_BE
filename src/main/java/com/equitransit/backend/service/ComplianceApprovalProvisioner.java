package com.equitransit.backend.service;

import com.equitransit.backend.repository.ComplianceRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Same-transaction approval hook, independent of actor-scoped F08 access. */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "app.compliance.auto-approval", havingValue = "true",
        matchIfMissing = true)
public class ComplianceApprovalProvisioner {
    private final ComplianceRepository data;
    private final ComplianceAutomation automation;
    private final Clock clock;

    /** @param data persistence @param automation builder @param clock UTC */
    public ComplianceApprovalProvisioner(ComplianceRepository data,
            ComplianceAutomation automation, Clock clock) {
        this.data = data;
        this.automation = automation;
        this.clock = clock;
    }

    /** Locked approved revision.
     * @param order order @param revision revision @param user account */
    public record Approved(long order, long revision, long user) { }

    /** @param event approval; failures roll back the assessment */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void provision(Approved event) {
        if (!data.localFilesReady() || !data.ready()) {
            return;
        }
        var args = new HashMap<String, Object>();
        args.put("order", event.order());
        args.put("revision", event.revision());
        args.put("user", event.user());
        args.put("now", LocalDateTime.now(clock.withZone(ZoneOffset.UTC))
                .withNano(0));
        var route = data.rows("route", args).getFirst();
        args.put("horse", route.get("HorseID"));
        args.put("origin", route.get("OriginCountryCode"));
        args.put("destination", route.get("DestinationCountryCode"));
        data.rows("horseLock", args);
        automation.bootstrap(args);
    }
}
