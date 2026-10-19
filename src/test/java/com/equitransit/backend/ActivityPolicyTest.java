/*
 * ActivityPolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Audit privacy and notification failure isolation.
 */
package com.equitransit.backend;


import com.equitransit.backend.service.activity.AuditRedactor;
import com.equitransit.backend.service.activity.NotificationWriter;
import com.equitransit.backend.service.activity.NotificationWorker;
import com.equitransit.backend.mail.AccountMailSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;


/** No real SMTP, SMS or private body logging in tests. */
public class ActivityPolicyTest {

    /** @throws Exception if JSON cannot be parsed */
    @Test
    public void removesSecretsFreeTextAndCostsEvenInNestedPayloads()
            throws Exception {
        var mapper = new ObjectMapper();
        var redactor = new AuditRedactor(mapper);
        var node = mapper.readTree("{\"password\":\"secret-password\","
                + "\"token\":\"raw-jwt\",\"status\":\"APPROVED\","
                + "\"internalNotes\":\"private-note\",\"margin\":123,"
                + "\"nested\":{\"medical\":\"private-health\"}}");
        String encoded = redactor.redact(node).toString();

        assertThat(encoded).contains("APPROVED", "[REDACTED]")
                .doesNotContain("secret-password", "raw-jwt", "private-note",
                        "private-health", "123");
    }

    /** @throws Exception if JSON cannot be parsed */
    @Test
    public void preservesOnlyStructuredAuditMetadata() throws Exception {
        var mapper = new ObjectMapper();
        var redactor = new AuditRedactor(mapper);
        var node = mapper.readTree("{\"orderId\":101,\"revisionId\":2,"
                + "\"status\":\"ACCEPTED\",\"operationId\":"
                + "\"d86b4f63-5acc-4f20-8da3-e24323268814\"}");

        assertThat(redactor.redact(node)).isEqualTo(node);
    }

    /** A mail adapter exception cannot escape to a domain caller. */
    @Test
    public void failedEmailRetainsRetryStateAndProcessesNextMessage() {
        var writer = mock(NotificationWriter.class);
        var sender = mock(AccountMailSender.class);

        when(writer.claimMail()).thenReturn(List.of(1L, 2L));
        when(writer.recipient(1)).thenReturn("first@example.invalid");
        when(writer.recipient(2)).thenReturn(null);
        doThrow(new IllegalStateException("Synthetic SMTP failure"))
                .when(sender).send(any());
        new NotificationWorker(writer, sender).poll();
        verify(writer).completeMail(1, false, false);
        verify(writer).completeMail(2, true, true);
    }

    /** Projection failure is deferred to a later poll, not thrown to domain. */
    @Test
    public void projectionFailureIsCaughtWithoutErasingDurableAudit() {
        var writer = mock(NotificationWriter.class);
        var sender = mock(AccountMailSender.class);

        doThrow(new IllegalStateException("Synthetic SQL failure"))
                .when(writer).dispatch();
        new NotificationWorker(writer, sender).poll();
        verify(writer).dispatch();
    }
}
