/*
 * LegalFilePolicy
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Implement revision-bound legal evidence.
 */
package com.equitransit.backend.service.compliance;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSArray;
import org.springframework.stereotype.Component;


/** Bounded PDF/PNG/JPEG inspection; retain original evidence bytes/hash. */
@Component
public class LegalFilePolicy {

    private static final int MAX_BYTES = 5242880;
    private static final Set<String> ACTIVE_KEYS = Set.of("JS", "JavaScript",
            "AA", "OpenAction", "EmbeddedFiles", "RichMedia", "XFA");
    private static final Set<String> ACTIVE_ACTIONS = Set.of("Launch",
            "SubmitForm", "ImportData", "GoToR", "JavaScript", "URI");

    /**
     * @param name simple filename, never a disk path
     * @param mime declared type must match parsed content
     * @param encoded max5MiB decoded
     * @return unchanged inspected bytes for immutable legal evidence
     */
    public byte[] inspect(String name, String mime, String encoded) {
        LegalEvidencePolicy.require(name.matches(
                "[A-Za-z0-9][A-Za-z0-9_. -]{0,250}\\.(pdf|png|jpg|jpeg)")
                && !name.contains(".."), "FILE_NAME_INVALID");
        String suffix = name.substring(name.lastIndexOf('.') + 1);
        boolean pdf = "application/pdf".equals(mime);
        boolean png = "image/png".equals(mime);

        LegalEvidencePolicy.require(pdf && suffix.equals("pdf")
                || png && suffix.equals("png") || "image/jpeg".equals(mime)
                && (suffix.equals("jpg") || suffix.equals("jpeg")),
                "FILE_TYPE_INVALID");
        try {
            byte[] bytes = Base64.getDecoder().decode(encoded);

            LegalEvidencePolicy.require(bytes.length > 0
                    && bytes.length <= MAX_BYTES, "FILE_SIZE_INVALID");
            if (pdf) {
                pdf(bytes);
            } else {
                image(bytes, png);
            }
            return bytes;
        } catch (IOException | IllegalArgumentException error) {
            throw new com.equitransit.backend.exception.OrderException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "FILE_CONTENT_INVALID", "Unsupported evidence content.");
        }
    }

    private void pdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            LegalEvidencePolicy.require(!document.isEncrypted()
                    && document.getNumberOfPages() > 0
                    && document.getNumberOfPages() <= 100,
                    "FILE_PDF_LIMITS");
            Set<COSBase> seen = Collections.newSetFromMap(
                    new IdentityHashMap<>());

            inspectObject(document.getDocumentCatalog().getCOSObject(),
                    seen, 0);
            for (var key : document.getDocument().getXrefTable().keySet()) {
                inspectObject(document.getDocument().getObjectFromPool(key),
                        seen, 0);
            }
        }
    }

    private void inspectObject(COSBase object, Set<COSBase> seen, int depth) {
        if (object == null || !seen.add(object)) {
            return;
        }
        LegalEvidencePolicy.require(depth <= 64 && seen.size() <= 20000,
                "FILE_PDF_LIMITS");
        if (object instanceof COSObject indirect) {
            inspectObject(indirect.getObject(), seen, depth + 1);
        } else if (object instanceof COSDictionary dictionary) {
            LegalEvidencePolicy.require(!ACTIVE_ACTIONS.contains(
                    dictionary.getNameAsString("S", "")), "FILE_PDF_ACTIVE");
            for (var key : dictionary.keySet()) {
                LegalEvidencePolicy.require(!ACTIVE_KEYS.contains(
                        key.getName()), "FILE_PDF_ACTIVE");
                inspectObject(dictionary.getDictionaryObject(key), seen,
                        depth + 1);
            }
        } else if (object instanceof COSArray array) {
            for (var item : array) {
                inspectObject(item, seen, depth + 1);
            }
        }
    }

    private void image(byte[] bytes, boolean png) throws IOException {
        try (var stream = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(stream);

            LegalEvidencePolicy.require(readers.hasNext(),
                    "FILE_CONTENT_INVALID");
            var reader = readers.next();

            try {
                reader.setInput(stream, true, true);
                LegalEvidencePolicy.require(reader.getFormatName()
                        .equalsIgnoreCase(png ? "png" : "JPEG")
                        && (long) reader.getWidth(0) * reader.getHeight(0)
                                <= 20000000, "FILE_CONTENT_INVALID");
                LegalEvidencePolicy.require(reader.read(0) != null,
                        "FILE_CONTENT_INVALID");
            } finally {
                reader.dispose();
            }
        }
    }
}
