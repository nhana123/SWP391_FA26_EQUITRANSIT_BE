/*
 * FinalDeliveryValidator
 * Author: EquiTransit development team
 * Date: 2026-10-18
 * Copyright: EquiTransit project contributors.
 * Purpose: Enforce Quarantine Release gate, authorized receiver & artifacts.
 */
package com.equitransit.backend.service.handover;

import com.equitransit.backend.dto.handover.request.FinalDeliveryHandoverCreateRequest;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.handover.TripHandoverRepository.OrderReceiverRow;
import com.equitransit.backend.service.ComplianceService;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Validates quarantine release clearance, recipient authority & artifacts.
 */
@Component
public class FinalDeliveryValidator {

    private static final Set<String> VALID_PROXY_AUTHORITIES = Set.of(
            "STABLE_MANAGER", "TRAINER", "GROOM", "VETERINARIAN",
            "AUTHORIZED_REPRESENTATIVE", "PROXY", "AGENT"
    );

    private final ObjectProvider<ComplianceService> complianceProvider;

    /**
     * Creates final delivery validator.
     * @param complianceProvider optional compliance service provider
     */
    public FinalDeliveryValidator(
            ObjectProvider<ComplianceService> complianceProvider) {
        this.complianceProvider = complianceProvider;
    }

    /**
     * Validates that all prerequisites for final delivery are satisfied.
     *
     * @param orderId parent order identifier
     * @param request final delivery payload
     * @param expectedReceiver authorized receiver from transport order
     */
    public void validateFinalDelivery(long orderId,
            FinalDeliveryHandoverCreateRequest request,
            OrderReceiverRow expectedReceiver) {
        validateQuarantineRelease(orderId);
        validateReceiverIdentity(request, expectedReceiver);
        validateArtifacts(request);
    }

    /**
     * Checks F08 quarantine release clearance gate.
     * @param orderId order PK
     */
    public void validateQuarantineRelease(long orderId) {
        ComplianceService compliance = complianceProvider.getIfAvailable();
        if (compliance != null) {
            try {
                boolean passed = compliance.quarantineReleasePassed(orderId);
                if (!passed) {
                    throw new OrderException(HttpStatus.CONFLICT,
                            "QUARANTINE_RELEASE_REQUIRED",
                            "Final delivery cannot be completed without a "
                                    + "valid QUARANTINE_RELEASE clearance.");
                }
            } catch (OrderException oe) {
                throw oe;
            } catch (Exception e) {
                // If compliance service threw specific business denial
                if (e.getMessage() != null
                        && e.getMessage().contains("QUARANTINE_RELEASE")) {
                    throw new OrderException(HttpStatus.CONFLICT,
                            "QUARANTINE_RELEASE_REQUIRED", e.getMessage());
                }
                // fallback if method thrown
            }
        }
    }

    /**
     * Checks that receivedByName matches authorized consignee or has proxy.
     *
     * @param request final delivery payload
     * @param expected authorized receiver from order
     */
    public void validateReceiverIdentity(
            FinalDeliveryHandoverCreateRequest request,
            OrderReceiverRow expected) {
        if (expected == null || expected.receiverName() == null
                || expected.receiverName().isBlank()) {
            return;
        }

        String actualName = request.receivedByName().trim();
        String expectedName = expected.receiverName().trim();

        if (actualName.equalsIgnoreCase(expectedName)) {
            return;
        }

        // Names differ: verify receiverAuthority delegation
        String authority = request.receiverAuthority();
        if (authority == null || authority.isBlank()) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RECEIVER_IDENTITY_MISMATCH",
                    "ReceivedByName '" + actualName + "' does not match "
                            + "authorized consignee '" + expectedName
                            + "' and no proxy authority was specified.");
        }

        String normAuth = authority.trim().toUpperCase(Locale.ROOT);
        if (!VALID_PROXY_AUTHORITIES.contains(normAuth)
                && (expected.receiverAuthority() == null
                || !normAuth.equalsIgnoreCase(
                        expected.receiverAuthority().trim()))) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "RECEIVER_IDENTITY_MISMATCH",
                    "ReceivedByName '" + actualName + "' does not match "
                            + "authorized consignee '" + expectedName
                            + "' with unrecognized authority '"
                            + authority + "'.");
        }
    }

    /**
     * Verifies mandatory electronic signature and handover photograph.
     * @param request final delivery payload
     */
    public void validateArtifacts(FinalDeliveryHandoverCreateRequest request) {
        if (request.receiverSignatureFileKey() == null
                || request.receiverSignatureFileKey().isBlank()) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "SIGNATURE_REQUIRED",
                    "Final delivery requires an electronic signature file.");
        }
        if (request.photoFileKey() == null
                || request.photoFileKey().isBlank()) {
            throw new OrderException(HttpStatus.BAD_REQUEST,
                    "HANDOVER_PHOTO_REQUIRED",
                    "Final delivery requires a stall delivery photo.");
        }
    }
}
