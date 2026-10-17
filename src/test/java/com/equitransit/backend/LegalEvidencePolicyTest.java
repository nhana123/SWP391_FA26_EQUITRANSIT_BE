/*
 * LegalEvidencePolicyTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify pure evidence validity and content limits.
 */
package com.equitransit.backend;


import com.equitransit.backend.service.LegalEvidencePolicy;
import com.equitransit.backend.service.LegalFilePolicy;
import com.equitransit.backend.exception.OrderException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


/** No legal country rules or permits are invented by these pure tests. */
public class LegalEvidencePolicyTest {

    /** Expiry includes the UTC expiry day; all proof conditions must pass. */
    @Test
    public void expiredRevokedReplacedAndRejectedEvidenceFails() {
        var issued = LocalDate.parse("2027-01-01");
        var expiry = LocalDate.parse("2027-01-31");
        var at = OffsetDateTime.parse("2027-01-31T23:59:59Z");

        assertThat(LegalEvidencePolicy.documentValid(issued, expiry,
                false, true, true, at)).isTrue();
        assertThat(LegalEvidencePolicy.documentValid(issued, expiry,
                false, true, true, at.plusSeconds(1))).isFalse();
        assertThat(LegalEvidencePolicy.documentValid(issued, expiry,
                true, true, true, at)).isFalse();
        assertThat(LegalEvidencePolicy.documentValid(issued, expiry,
                false, false, true, at)).isFalse();
        assertThat(LegalEvidencePolicy.documentValid(issued, expiry,
                false, true, false, at)).isFalse();
    }

    /** Sources are HTTPS references; they are never server-side fetched. */
    @Test
    public void sourceReferencesRejectUnsafeOrAmbiguousUrls() {
        assertThat(LegalEvidencePolicy.sourceUrl(
                "https://authority.example/rules/health")).isTrue();
        for (String source : java.util.List.of("file:///C:/secret",
                "http://authority.example/rule", "javascript:alert(1)",
                "https://user:password@authority.example/rule")) {
            assertThat(LegalEvidencePolicy.sourceUrl(source)).isFalse();
        }
    }

    /** @throws Exception if valid PDF parsing changes original bytes */
    @Test
    public void validPdfKeepsOriginalImmutableBytes() throws Exception {
        byte[] bytes = pdf(false);

        assertThat(new LegalFilePolicy().inspect("certificate.pdf",
                "application/pdf", Base64.getEncoder()
                        .encodeToString(bytes))).isEqualTo(bytes);
    }

    /** @throws Exception if active PDF rejection cannot be tested */
    @Test
    public void activePdfAndSpoofedMimeAreRejected() throws Exception {
        String content = Base64.getEncoder().encodeToString(pdf(true));
        var files = new LegalFilePolicy();

        assertThatThrownBy(() -> files.inspect("permit.pdf",
                "application/pdf", content)).isInstanceOf(OrderException.class);
        assertThatThrownBy(() -> files.inspect("permit.png",
                "image/png", content)).isInstanceOf(OrderException.class);
    }

    /** Paths, oversized content and bogus file types cannot become evidence. */
    @Test
    public void pathTraversalAndSizeLimitsAreEnforced() {
        var files = new LegalFilePolicy();

        for (String path : java.util.List.of("../permit.pdf", "C:/a.pdf",
                "..\\permit.pdf", "a.html", "a.pdf.exe")) {
            assertThatThrownBy(() -> files.inspect(path, "application/pdf",
                    "JVBERi0xLjcK")).isInstanceOf(OrderException.class);
        }
        assertThatThrownBy(() -> files.inspect("large.pdf", "application/pdf",
                Base64.getEncoder().encodeToString(new byte[5242881])))
                .isInstanceOf(OrderException.class);
    }

    private byte[] pdf(boolean active) throws Exception {
        try (var document = new PDDocument();
                var out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            if (active) {
                document.getDocumentCatalog().setOpenAction(
                        new PDActionJavaScript("app.alert('untrusted')"));
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
