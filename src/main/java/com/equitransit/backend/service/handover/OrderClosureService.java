/* Manager-only, concurrency-safe F14 order closure. */
package com.equitransit.backend.service.handover;

import com.equitransit.backend.dto.quote.QuoteCommand;
import com.equitransit.backend.dto.handover.response.OrderClosureResponse;
import com.equitransit.backend.entity.activity.ActivityCode;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.handover.OrderClosureRepository;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.activity.AuditWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Closes delivered orders; it neither invents nor reallocates payments. */
@Service
public class OrderClosureService {

    private final AssessmentRepository cases;
    private final OrderClosureRepository closures;
    private final CurrentActorService actors;
    private final AuditWriter audit;
    private final ObjectMapper mapper;

    /**
     * @param cases parent locking and staff status
     * @param closures state mutation and immutable retry lookup
     * @param actors live authenticated identity
     * @param audit canonical append-only order audit
     * @param mapper JSON codec
     */
    public OrderClosureService(AssessmentRepository cases,
            OrderClosureRepository closures, CurrentActorService actors,
            AuditWriter audit, ObjectMapper mapper) {
        this.cases = cases;
        this.closures = closures;
        this.actors = actors;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** @param orderId delivered parent @param command version and retry key
     * @return updated order closure receipt */
    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public OrderClosureResponse close(long orderId, QuoteCommand command) {
        var actor = actors.current();
        if (!"MANAGER".equals(actor.roleCode()) || actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), "MANAGER")) {
            throw new AccessDeniedException("Active manager required.");
        }
        var parent = cases.order(orderId, true).orElseThrow(() ->
                new OrderException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                        "Transport order not found."));
        String key = TokenTools.hash(actor.userId() + ":" + orderId
                + ":CLOSED:" + command.operationId());
        var prior = closures.replay(key);
        if (prior.isPresent()) {
            try {
                var metadata = mapper.readTree(prior.get().get(
                        "ChangeSnapshot").toString()).path("metadata");
                require(command.recordVersion().equalsIgnoreCase(metadata
                        .path("previousRecordVersion").asText()),
                        "CLOSURE_RETRY_CONFLICT");
                return new OrderClosureResponse(orderId, "CLOSED", metadata
                        .path("recordVersion").asText(), command.operationId(),
                        closures.updatedAt(orderId));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("Invalid closure receipt.", e);
            }
        }
        require(parent.version().equalsIgnoreCase(command.recordVersion()),
                "ORDER_VERSION_CONFLICT");
        require("DELIVERED".equals(parent.status()),
                "ORDER_NOT_DELIVERED");
        require(closures.close(orderId), "ORDER_NOT_DELIVERED");
        var current = cases.order(orderId, false).orElseThrow();
        audit.append(orderId, ActivityCode.CLOSED, command.operationId(),
                mapper.valueToTree(Map.of("orderId", orderId,
                        "operationId", command.operationId(),
                        "status", "CLOSED", "recordVersion", current.version(),
                        "previousRecordVersion", command.recordVersion())));
        return new OrderClosureResponse(orderId, "CLOSED", current.version(),
                command.operationId(), closures.updatedAt(orderId));
    }

    private void require(boolean valid, String code) {
        if (!valid) {
            throw new OrderException(HttpStatus.CONFLICT, code,
                    "Closure state, version or retry input conflicts.");
        }
    }
}
