package com.example.medical.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layering guard (M8.5): {@code common/} and {@code security/} are the shared
 * kernel — business modules depend on them, never the other way round. Before
 * M8.5 five files broke that, three of them closing real import cycles
 * ({@code common ↔ appointment}, {@code common ↔ prescription}, {@code security
 * ↔ system}).
 * <p>
 * A source scan rather than an ArchUnit rule, because the project does not take
 * new dependencies for checks it can make itself. Runs from the Maven module
 * root, which is where Surefire sets the working directory.
 */
class LayeringGuardTest {

    private static final List<String> SHARED_KERNEL = List.of("common", "security");

    @Test
    void sharedKernelShouldNotImportBusinessModules() throws IOException {
        Path root = Path.of("src/main/java/com/example/medical");
        assertTrue(Files.isDirectory(root),
                "expected to run from medical-server; looked for " + root.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        for (String pkg : SHARED_KERNEL) {
            Path dir = root.resolve(pkg);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    if (source.contains("import com.example.medical.module.")) {
                        offenders.add(root.relativize(file).toString());
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "shared kernel must not import a business module, found: " + offenders
                        + " — invert it with an interface in common/ (see DoctorPatientScopeProvider)");
    }
}
