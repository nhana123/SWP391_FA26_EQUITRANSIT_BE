/*
 * PaymentProofService
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Owner-scoped, bounded, sanitized image evidence.
 */
package com.equitransit.backend.service.payment;


import com.equitransit.backend.dto.payment.PaymentProofCommand;
import com.equitransit.backend.dto.payment.PaymentProofReceipt;
import com.equitransit.backend.dto.payment.PaymentProofView;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.repository.assessment.AssessmentRepository;
import com.equitransit.backend.repository.payment.PaymentRepository;
import com.equitransit.backend.repository.order.OrderRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.json.order.OrderJsonCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;


/**
 * Evidence is stored outside URLs/paths and never served as executable HTML.
 */
@Service
@Validated
@PreAuthorize("isAuthenticated()")
@Transactional(timeout = 15)
public class PaymentProofService {

    private final PaymentRepository ledger;
    private final AssessmentRepository cases;
    private final OrderRepository orders;
    private final CurrentActorService actors;
    private final ObjectMapper mapper;
    private final OrderJsonCodec json;

    /**
     * @param ledger immutable evidence storage
     * @param cases parent and live staff checks
     * @param orders ownership checks
     * @param actors trusted identity
     * @param mapper receipt decoder
     * @param json canonical encoding
     */
    public PaymentProofService(PaymentRepository ledger,
            AssessmentRepository cases, OrderRepository orders,
            CurrentActorService actors, ObjectMapper mapper,
            OrderJsonCodec json) {
        this.ledger = ledger;
        this.cases = cases;
        this.orders = orders;
        this.actors = actors;
        this.mapper = mapper;
        this.json = json;
    }

    /**
     * @param order owned or managed parent
     * @param body PNG/JPEG only, max5MiB decoded,20MP
     * @return opaque key; no financial or report side effects
     */
    public PaymentProofReceipt upload(long order,
            @Valid PaymentProofCommand body) {
        var actor = access(order);
        var args = new java.util.HashMap<String, Object>();

        args.put("order", order);
        args.put("user", actor.userId());
        args.put("op", body.command().operationId());
        String hash = TokenTools.hash("PROOF:" + json.canonical(body));
        var replay = ledger.rows("replay", args);

        if (!replay.isEmpty()) {
            if (!hash.equals(replay.getFirst().get("RequestHash"))) {
                throw failure("PAYMENT_RETRY_CONFLICT");
            }
            try {
                return mapper.readValue(replay.getFirst().get("ReceiptJSON")
                        .toString(), PaymentProofReceipt.class);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw failure("PAYMENT_RECEIPT_UNSUPPORTED");
            }
        }
        if (!cases.order(order, false).orElseThrow().version()
                .equalsIgnoreCase(body.command().recordVersion())) {
            throw failure("ORDER_VERSION_CONFLICT");
        }
        byte[] safe = sanitize(body.contentBase64(), body.mediaType());
        String key = UUID.randomUUID().toString();

        args.put("key", key);
        args.put("mime", body.mediaType());
        args.put("bytes", safe);
        ledger.update("proofInsert", args);
        var receipt = new PaymentProofReceipt(key, body.mediaType(),
                safe.length);

        args.put("hash", hash);
        args.put("receipt", json.write(receipt));
        ledger.update("operation", args);
        return receipt;
    }

    /**
     * @param order authorized parent
     * @param key opaque identifier; never a path
     * @return typed JSON base64 content, PNG/JPEG only
     */
    public PaymentProofView get(long order, String key) {
        var actor = access(order);
        var row = require(order, key, actor);

        return new PaymentProofView(key, row.get("MediaType").toString(),
                Base64.getEncoder().encodeToString(
                        (byte[]) ledger.rows("proofBytes", Map.of(
                                "order", order, "key", UUID.fromString(key)))
                                .getFirst().get("ContentBytes")));
    }

    /**
     * @param order locked parent
     * @param key opaque stored identifier
     * @param actor live identity; customers may use only their own uploads
     * @return verified proof row
     */
    public Map<String, Object> require(long order, String key,
            CurrentActor actor) {
        UUID parsed;

        try {
            parsed = UUID.fromString(key);
        } catch (IllegalArgumentException e) {
            throw failure("PAYMENT_PROOF_INVALID");
        }
        var rows = ledger.rows("proof", Map.of("order", order, "key", parsed));

        if (rows.size() != 1 || ("CUSTOMER".equals(actor.roleCode())
                && ((Number) rows.getFirst().get("UserID")).longValue()
                        != actor.userId())) {
            throw failure("PAYMENT_PROOF_UNAVAILABLE");
        }
        return rows.getFirst();
    }

    private CurrentActor access(long order) {
        if (!ledger.ready()) {
            throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                    "PAYMENT_SCHEMA_PENDING", "Deploy payment migration007.");
        }
        cases.order(order,
                true).orElseThrow(() -> failure("PAYMENT_NOT_FOUND"));
        var actor = actors.current();

        if ("CUSTOMER".equals(actor.roleCode())) {
            if (orders.findOwned(order, actor.customerId(), false).isEmpty()) {
                throw failure("PAYMENT_NOT_FOUND");
            }
        } else if (!"MANAGER".equals(actor.roleCode())
                || actor.staffId() == null
                || !cases.activeStaff(actor.staffId(), "MANAGER")) {
            throw new OrderException(HttpStatus.FORBIDDEN,
                    "PAYMENT_PROOF_FORBIDDEN", "Access is denied.");
        }
        ledger.lockLedger();
        return actor;
    }

    private byte[] sanitize(String content, String mime) {
        try {
            if (!mime.equals("image/png") && !mime.equals("image/jpeg")) {
                throw failure("PAYMENT_PROOF_TYPE");
            }
            byte[] bytes = Base64.getDecoder().decode(content);

            if (bytes.length == 0 || bytes.length > 5242880) {
                throw failure("PAYMENT_PROOF_SIZE");
            }
            try (var stream = ImageIO.createImageInputStream(
                    new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);

                if (!readers.hasNext()) {
                    throw failure("PAYMENT_PROOF_CONTENT");
                }
                var reader = readers.next();

                try {
                    reader.setInput(stream, true, true);
                    String format = reader.getFormatName();
                    boolean correct = mime.equals("image/png")
                            ? format.equalsIgnoreCase("png")
                            : format.equalsIgnoreCase("JPEG");

                    if (!correct || (long) reader.getWidth(0)
                            * reader.getHeight(0) > 20000000) {
                        throw failure("PAYMENT_PROOF_CONTENT");
                    }
                    var image = reader.read(0);
                    var out = new ByteArrayOutputStream();

                    if (!ImageIO.write(image, format, out)
                            || out.size() > 5242880) {
                        throw failure("PAYMENT_PROOF_SIZE");
                    }
                    return out.toByteArray();
                } finally {
                    reader.dispose();
                }
            }
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw failure("PAYMENT_PROOF_CONTENT");
        }
    }

    private OrderException failure(String code) {
        return new OrderException(HttpStatus.CONFLICT, code,
                "Proof is unavailable or does not meet image limits.");
    }
}
