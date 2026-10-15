package com.example.medical.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layering guard.
 * <p>
 * <b>M8.5:</b> {@code common/} and {@code security/} are the shared kernel —
 * business modules depend on them, never the other way round. Before M8.5 five
 * files broke that, three of them closing real import cycles ({@code common ↔
 * appointment}, {@code common ↔ prescription}, {@code security ↔ system}).
 * <p>
 * <b>Round 52:</b> the same rule one level down, between modules. A repository is
 * a module's persistence contract, so importing another module's skips that
 * module's audit and scope rules; and a mutual import means neither module can be
 * understood, tested or moved on its own. Three checks, no allowlist: 52.1 landed
 * them with today's violations listed as a shrinking allowlist, 52.2–52.7 removed
 * every violation, and 52.8 deleted the mechanism. A violation is now a build
 * failure, full stop.
 * <p>
 * <b>52.3a:</b> a reference counts whether it arrives by import or is written out
 * fully qualified — 52.3's measurement found {@code AppointmentService} holding
 * {@code com.example.medical.module.billing.repository.ChargeRepository} with no
 * import at all, a live edge none of the counts above included. Comments are
 * excluded: a javadoc {@code {@link}} documents a type, it does not depend on it.
 * <p>
 * A source scan rather than an ArchUnit rule, because the project does not take
 * new dependencies for checks it can make itself. Runs from the Maven module
 * root, which is where Surefire sets the working directory.
 */
class LayeringGuardTest {

    private static final List<String> SHARED_KERNEL = List.of("common", "security");

    private static final Path ROOT = Path.of("src/main/java/com/example/medical");

    /** {@code import com.example.medical.module.<target>.<rest>.<Class>;} */
    private static final Pattern MODULE_IMPORT =
            Pattern.compile("import com\\.example\\.medical\\.module\\.([a-z]+)\\.(\\S+)\\.(\\w+);");

    /**
     * The same reference written out in code. The class name must start with a
     * capital — that is what keeps {@code billing.entity.Charge} from splitting
     * into {@code billing} + {@code entity.Charge}, and what stops a match from
     * running across a delimiter into the next call on the line.
     */
    private static final Pattern MODULE_REFERENCE =
            Pattern.compile("\\bcom\\.example\\.medical\\.module\\.([a-z]+)\\.([A-Za-z0-9_.]+?)\\.([A-Z]\\w*)\\b");

    /** Block comments, javadoc and line comments — dropped before the reference scan. */
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Test
    void sharedKernelShouldNotImportBusinessModules() throws IOException {
        Path root = moduleRoot();

        List<String> offenders = new ArrayList<>();
        for (String pkg : SHARED_KERNEL) {
            Path dir = root.resolve(pkg);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    if (Files.readString(file).contains("import com.example.medical.module.")) {
                        offenders.add(relativize(root, file));
                    }
                }
            }
        }

        assertTrue(offenders.isEmpty(),
                "shared kernel must not import a business module, found: " + offenders
                        + " — invert it with an interface in common/ (see DoctorPatientScopeProvider)");
    }

    @Test
    void noModuleShouldReferenceAnotherModulesRepository() throws IOException {
        Set<String> violations = new TreeSet<>();
        for (CrossModuleReference r : crossModuleReferences()) {
            if (r.simpleName().endsWith("Repository")) violations.add(r.key());
        }
        assertNoViolations("repository references", violations);
    }

    /**
     * An entity carries persistence semantics — its converter, its soft-delete
     * rules, its version column. A status enum carries a vocabulary, and the
     * dashboard's counters are deliberately built from {@code BillClaimStatus}
     * (Round 51.11), so enums are allowed and entities are not.
     */
    @Test
    void crossModuleEntityReferencesShouldBeEnumsOnly() throws IOException {
        Set<String> violations = new TreeSet<>();
        for (CrossModuleReference r : crossModuleReferences()) {
            if (!"entity".equals(r.packageWithinModule())) continue;
            if (r.simpleName().endsWith("Repository")) continue;
            if (!isEnum(r)) violations.add(r.key());
        }
        assertNoViolations("entity references", violations);
    }

    @Test
    void modulesShouldNotFormImportCycles() throws IOException {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (CrossModuleReference r : crossModuleReferences()) {
            graph.computeIfAbsent(r.from(), k -> new TreeSet<>()).add(r.to());
        }

        Set<String> cycles = new TreeSet<>();
        for (List<String> cycle : findCycles(graph)) {
            cycles.add(String.join(" ↔ ", cycle));
        }
        assertNoViolations("module import cycles", cycles);
    }

    private void assertNoViolations(String what, Set<String> violations) {
        System.out.printf("layering: cross-module %-26s %d%n", what, violations.size());

        assertTrue(violations.isEmpty(), "a module must not reference " + what + ": " + violations
                + " — cross the boundary through the owning module's service or an interface in"
                + " common/lookup (see PatientLookup, StaffLookup, ClinicalMeasureReader)");
    }

    private record CrossModuleReference(String file, String from, String to, String packageWithinModule,
                                        String simpleName) {

        String key() {
            return file + " → " + to + "." + simpleName;
        }
    }

    private Path moduleRoot() {
        assertTrue(Files.isDirectory(ROOT),
                "expected to run from medical-server; looked for " + ROOT.toAbsolutePath());
        return ROOT;
    }

    /**
     * Every reference a module makes into another module, by import or written out
     * fully qualified. Both forms are collected because both create the dependency;
     * only the import form was ever visible here (52.3a).
     */
    private List<CrossModuleReference> crossModuleReferences() throws IOException {
        Path root = moduleRoot();
        List<CrossModuleReference> found = new ArrayList<>();
        Path modules = root.resolve("module");
        try (Stream<Path> files = Files.walk(modules)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder()).toList()) {
                String from = modules.relativize(file).getName(0).toString();
                String source = Files.readString(file);
                Set<String> seen = new LinkedHashSet<>();

                Matcher m = MODULE_IMPORT.matcher(source);
                while (m.find()) {
                    String to = m.group(1);
                    if (to.equals(from)) continue;
                    if (seen.add(to + "." + m.group(3))) {
                        found.add(new CrossModuleReference(relativize(root, file), from, to,
                                firstSegment(m.group(2)), m.group(3)));
                    }
                }

                String code = MODULE_IMPORT.matcher(COMMENT.matcher(source).replaceAll(" "))
                        .replaceAll(" ");
                Matcher r = MODULE_REFERENCE.matcher(code);
                while (r.find()) {
                    String to = r.group(1);
                    if (to.equals(from)) continue;
                    if (seen.add(to + "." + r.group(3))) {
                        found.add(new CrossModuleReference(relativize(root, file), from, to,
                                firstSegment(r.group(2)), r.group(3)));
                    }
                }
            }
        }
        return found;
    }

    /** {@code dto.foo.Bar} is a class in {@code dto}; {@code entity} is a class in {@code entity}. */
    private String firstSegment(String rest) {
        return rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
    }

    private boolean isEnum(CrossModuleReference r) throws IOException {
        Path dir = moduleRoot().resolve("module").resolve(r.to());
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().equals(r.simpleName() + ".java"))
                    .toList()) {
                return Pattern.compile("\\benum\\s+" + r.simpleName() + "\\b")
                        .matcher(Files.readString(file)).find();
            }
        }
        return false;
    }

    /**
     * Every cycle reachable in the module graph, each rotated so the alphabetically
     * first module leads (a cycle is one thing, whichever import closes it).
     */
    private Set<List<String>> findCycles(Map<String, Set<String>> graph) {
        Set<List<String>> cycles = new LinkedHashSet<>();
        for (String start : graph.keySet()) {
            walk(start, start, graph, new ArrayList<>(List.of(start)), cycles);
        }
        Set<List<String>> canonical = new TreeSet<>(Comparator.comparing(Object::toString));
        for (List<String> cycle : cycles) {
            int first = 0;
            for (int i = 1; i < cycle.size(); i++) {
                if (cycle.get(i).compareTo(cycle.get(first)) < 0) first = i;
            }
            List<String> rotated = new ArrayList<>();
            for (int i = 0; i < cycle.size(); i++) rotated.add(cycle.get((first + i) % cycle.size()));
            canonical.add(rotated);
        }
        return canonical;
    }

    private void walk(String start, String current, Map<String, Set<String>> graph,
                      List<String> path, Set<List<String>> cycles) {
        for (String next : graph.getOrDefault(current, Set.of())) {
            if (next.equals(start)) {
                cycles.add(List.copyOf(path));
            } else if (!path.contains(next)) {
                path.add(next);
                walk(start, next, graph, path, cycles);
                path.remove(path.size() - 1);
            }
        }
    }

    private String relativize(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
