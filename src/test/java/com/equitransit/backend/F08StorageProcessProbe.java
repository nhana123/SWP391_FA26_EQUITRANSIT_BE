package com.equitransit.backend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import com.equitransit.backend.service.ComplianceService;

/** A separate JVM verifies persistence without shared static state. */
public final class F08StorageProcessProbe {
    private F08StorageProcessProbe() {
    }

    /** Writes or verifies the fixture using only persistent storage.
     * @param args root, mode, opaque key
     * @throws Exception failed integrity or I/O
     */
    public static void main(String[] args) throws Exception {
        var service = new ComplianceService(null, null, null, null, null,
                null, null, null, null, null, null, null, null);
        service.configureStorageRoot(Path.of(args[0]).toString());
        byte[] expected = "%PDF-1.7 process restart fixture"
                .getBytes(StandardCharsets.UTF_8);
        if ("write".equals(args[1])) {
            service.registerVerifiedFile(7, 9, args[2], "passport.pdf",
                    "application/pdf", expected);
        } else {
            var method = ComplianceService.class.getDeclaredMethod(
                    "loadStoredFile", String.class);
            method.setAccessible(true);
            var file = (ComplianceService.VerifiedStorageFile)
                    method.invoke(null, args[2]);
            if (file == null || file.horseId() != 7 || file.customerId() != 9
                    || !java.util.Arrays.equals(expected, file.content())) {
                throw new IllegalStateException("Restart verification failed");
            }
        }
    }
}
