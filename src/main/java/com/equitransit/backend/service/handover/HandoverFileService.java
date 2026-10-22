/*
 * HandoverFileService
 * Purpose: Authorize and persist F14 handover file uploads/downloads.
 */
package com.equitransit.backend.service.handover;

import com.equitransit.backend.dto.handover.request.HandoverFileUploadRequest;
import com.equitransit.backend.dto.handover.response.HandoverFileResponse;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.handover.HandoverFileRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.LegalFilePolicy;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** F14 file boundary with staff assignment and customer ownership checks. */
@Service
public class HandoverFileService {

    private final HandoverFileRepository files;
    private final CurrentActorService actors;
    private final LegalFilePolicy policy;

    /**
     * @param files F14 file repository
     * @param actors current actor resolver
     * @param policy content and filename inspector
     */
    public HandoverFileService(HandoverFileRepository files,
            CurrentActorService actors, LegalFilePolicy policy) {
        this.files = files;
        this.actors = actors;
        this.policy = policy;
    }

    /**
     * Uploads a file for an order.
     * @param orderId order key
     * @param request upload payload
     * @return opaque file response
     */
    @Transactional
    public HandoverFileResponse upload(long orderId,
            HandoverFileUploadRequest request) {
        requireSchema();
        CurrentActor actor = actors.current();
        if (actor.staffId() == null
                || !isStaffUploadRole(actor.roleCode())
                || !files.staffCanAccess(orderId, actor.staffId(),
                        actor.roleCode())) {
            throw new AccessDeniedException(
                    "Active order assignment required.");
        }
        byte[] content = policy.inspect(request.fileName(),
                request.contentType(), request.contentBase64());
        String key = UUID.randomUUID().toString();
        files.insert(key, orderId, actor.userId(), request.purpose(),
                request.contentType(), request.fileName(), content, sha256(
                        content));
        return new HandoverFileResponse(key, request.purpose(),
                request.contentType(), request.fileName(),
                request.contentBase64());
    }

    /**
     * Downloads a file while hiding actual-cost evidence from customers.
     * @param orderId order key
     * @param key opaque file key
     * @return file response
     */
    @Transactional(readOnly = true)
    public HandoverFileResponse download(long orderId, UUID key) {
        requireSchema();
        CurrentActor actor = actors.current();
        java.util.Optional<java.util.Map<String, Object>> row;
        if (actor.customerId() != null) {
            if (!files.customerOwns(orderId, actor.customerId())) {
                throw new AccessDeniedException("Order ownership required.");
            }
            row = files.findCustomer(orderId, key.toString());
        } else if (actor.staffId() == null
                || !isStaffReadRole(actor.roleCode())
                || !files.staffCanAccess(orderId, actor.staffId(),
                        actor.roleCode())) {
            throw new AccessDeniedException(
                    "Active order assignment required.");
        } else {
            row = files.find(orderId, key.toString(), null);
        }
        var file = row.orElseThrow(
                () -> new OrderException(HttpStatus.NOT_FOUND,
                        "HANDOVER_FILE_NOT_FOUND",
                        "No permitted handover file exists for this order."));
        return new HandoverFileResponse(key.toString(),
                file.get("Purpose").toString(),
                file.get("ContentType").toString(),
                file.get("OriginalFileName").toString(),
                Base64.getEncoder().encodeToString(
                        (byte[]) file.get("ContentBytes")));
    }

    private boolean isStaffUploadRole(String role) {
        return "DRIVER".equals(role) || "COORDINATOR".equals(role)
                || "MANAGER".equals(role);
    }

    private boolean isStaffReadRole(String role) {
        return isStaffUploadRole(role);
    }

    private void requireSchema() {
        if (!files.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "HANDOVER_FILE_SCHEMA_PENDING",
                    "Deploy migration 019 before using F14 files.");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }
}
