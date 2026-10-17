package com.equitransit.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

/** Keeps generated private files within the disposable Maven target folder. */
class ComplianceTestDirectoryFactory implements TempDirFactory {
    @Override
    public Path createTempDirectory(AnnotatedElementContext element,
            ExtensionContext context) throws IOException {
        Path root = Path.of("target", "f08-private-test-files")
                .toAbsolutePath();
        Files.createDirectories(root);
        return Files.createTempDirectory(root, "case-");
    }
}
