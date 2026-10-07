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
 * understood, tested or moved on its own. Three checks, plus the shrinking
 * allowlist in {@link #PENDING_REPOSITORY}, {@link #PENDING_ENTITY} and
 * {@link #PENDING_CYCLE} that Round 52 works down to empty.
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
     * Round 52 work in progress: the violations that exist today, each tagged with
     * the slice that removes it. Two rules keep this honest — a violation that is
     * not listed fails immediately, and so does an entry whose violation has been
     * fixed ({@link #allowlistsShouldNotContainFixedEntries}), which is what makes
     * the list shrink instead of rot. Slice 52.8 deletes the three constants.
     */
    private static final Map<String, String> PENDING_REPOSITORY = Map.ofEntries(
            Map.entry("module/appointment/service/AppointmentService.java → system.SysUserRepository", "52.3"),
            Map.entry("module/chat/service/ChatService.java → system.SysUserRepository", "52.3"),
            Map.entry("module/integration/service/AdtService.java → patient.PatientRepository", "52.5"),
            Map.entry("module/integration/service/LabResultService.java → patient.ObservationRepository", "52.5"),
            Map.entry("module/integration/service/LabResultService.java → patient.PatientRepository", "52.5"),
            Map.entry("module/patient/service/PatientCaseService.java → appointment.AppointmentRepository", "52.7"),
            Map.entry("module/patient/service/PatientCaseService.java → prescription.PrescriptionItemRepository", "52.7"),
            Map.entry("module/patient/service/PatientCaseService.java → prescription.PrescriptionRepository", "52.7"),
            Map.entry("module/prescription/service/PrescriptionService.java → system.SysUserRepository", "52.3"),
            Map.entry("module/quality/service/QualityMeasureService.java → patient.ObservationRepository", "52.6"),
            Map.entry("module/quality/service/QualityMeasureService.java → patient.PatientRepository", "52.6"),
            Map.entry("module/system/controller/EmergencyAccessController.java → patient.PatientRepository", "52.4"));

    private static final Map<String, String> PENDING_ENTITY = Map.ofEntries(
            Map.entry("module/appointment/service/AppointmentService.java → system.SysUser", "52.3"),
            Map.entry("module/chat/service/ChatService.java → system.SysUser", "52.3"),
            Map.entry("module/integration/service/AdtService.java → patient.Patient", "52.5"),
            Map.entry("module/integration/service/LabResultService.java → patient.Observation", "52.5"),
            Map.entry("module/integration/service/LabResultService.java → patient.Patient", "52.5"),
            Map.entry("module/patient/service/PatientCaseService.java → appointment.Appointment", "52.7"),
            Map.entry("module/patient/service/PatientCaseService.java → prescription.Prescription", "52.7"),
            Map.entry("module/patient/service/PatientCaseService.java → prescription.PrescriptionItem", "52.7"),
            Map.entry("module/prescription/service/PrescriptionService.java → system.SysUser", "52.3"),
            Map.entry("module/quality/service/QualityMeasureService.java → patient.Observation", "52.6"),
            Map.entry("module/quality/service/QualityMeasureService.java → patient.Patient", "52.6"));

    private static final Map<String, String> PENDING_CYCLE = Map.of(
            "appointment ↔ system ↔ patient", "52.3 + 52.4",
            "patient ↔ prescription ↔ system", "52.3 + 52.7",
            "patient ↔ system", "52.4");

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
    void noModuleShouldImportAnotherModulesRepository() throws IOException {
        Set<String> violations = new TreeSet<>();
        for (CrossModuleImport i : crossModuleImports()) {
            if (i.simpleName().endsWith("Repository")) violations.add(i.key());
        }
        assertAllowlisted("another module's repository", violations, PENDING_REPOSITORY);
    }

    /**
     * An entity carries persistence semantics — its converter, its soft-delete
     * rules, its version column. A status enum carries a vocabulary, and the
     * dashboard's counters are deliberately built from {@code BillClaimStatus}
     * (Round 51.11), so enums are allowed and entities are not.
     */
    @Test
    void crossModuleEntityImportsShouldBeEnumsOnly() throws IOException {
        Set<String> violations = new TreeSet<>();
        for (CrossModuleImport i : crossModuleImports()) {
            if (!"entity".equals(i.packageWithinModule())) continue;
            if (i.simpleName().endsWith("Repository")) continue;
            if (!isEnum(i)) violations.add(i.key());
        }
        assertAllowlisted("another module's entity", violations, PENDING_ENTITY);
    }

    @Test
    void modulesShouldNotFormImportCycles() throws IOException {
        Map<String, Set<String>> graph = new TreeMap<>();
        for (CrossModuleImport i : crossModuleImports()) {
            graph.computeIfAbsent(i.from(), k -> new TreeSet<>()).add(i.to());
        }

        Set<String> cycles = new TreeSet<>();
        for (List<String> cycle : findCycles(graph)) {
            cycles.add(String.join(" ↔ ", cycle));
        }
        assertAllowlisted("module import cycle", cycles, PENDING_CYCLE);
    }

    @Test
    void allowlistsShouldNotContainFixedEntries() throws IOException {
        List<CrossModuleImport> imports = crossModuleImports();
        Set<String> repository = new TreeSet<>();
        Set<String> entity = new TreeSet<>();
        Map<String, Set<String>> graph = new TreeMap<>();
        for (CrossModuleImport i : imports) {
            graph.computeIfAbsent(i.from(), k -> new TreeSet<>()).add(i.to());
            if (i.simpleName().endsWith("Repository")) repository.add(i.key());
            else if ("entity".equals(i.packageWithinModule()) && !isEnum(i)) entity.add(i.key());
        }
        Set<String> cycles = new TreeSet<>();
        for (List<String> cycle : findCycles(graph)) cycles.add(String.join(" ↔ ", cycle));

        List<String> stale = new ArrayList<>();
        for (String key : PENDING_REPOSITORY.keySet()) if (!repository.contains(key)) stale.add(key);
        for (String key : PENDING_ENTITY.keySet()) if (!entity.contains(key)) stale.add(key);
        for (String key : PENDING_CYCLE.keySet()) if (!cycles.contains(key)) stale.add(key);

        assertTrue(stale.isEmpty(),
                "these allowlist entries no longer describe a violation — delete them: " + stale);
    }

    private void assertAllowlisted(String what, Set<String> violations, Map<String, String> pending) {
        System.out.printf("layering: cross-module %-24s %d (allowlisted %d)%n",
                what + " imports", violations.size(), pending.size());

        List<String> unexpected = new ArrayList<>();
        for (String violation : violations) {
            if (!pending.containsKey(violation)) {
                unexpected.add(violation + " (fix it, or add it with the slice that will)");
            }
        }
        assertTrue(unexpected.isEmpty(), "a module must not import " + what + ": " + unexpected);
    }

    private record CrossModuleImport(String file, String from, String to, String packageWithinModule,
                                     String simpleName) {

        String key() {
            return file + " → " + to + "." + simpleName;
        }

        /** Round 52 works in one direction at a time, so the import target matters. */
        boolean pointsAt(String fqnSuffix) {
            return key().endsWith(fqnSuffix);
        }
    }

    private Path moduleRoot() {
        assertTrue(Files.isDirectory(ROOT),
                "expected to run from medical-server; looked for " + ROOT.toAbsolutePath());
        return ROOT;
    }

    private List<CrossModuleImport> crossModuleImports() throws IOException {
        Path root = moduleRoot();
        List<CrossModuleImport> found = new ArrayList<>();
        Path modules = root.resolve("module");
        try (Stream<Path> files = Files.walk(modules)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder()).toList()) {
                String from = modules.relativize(file).getName(0).toString();
                Matcher m = MODULE_IMPORT.matcher(Files.readString(file));
                while (m.find()) {
                    String to = m.group(1);
                    if (to.equals(from)) continue;
                    String rest = m.group(2);
                    String pkg = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : rest;
                    found.add(new CrossModuleImport(relativize(root, file), from, to, pkg, m.group(3)));
                }
            }
        }
        return found;
    }

    private boolean isEnum(CrossModuleImport i) throws IOException {
        Path dir = moduleRoot().resolve("module").resolve(i.to());
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().equals(i.simpleName() + ".java"))
                    .toList()) {
                return Pattern.compile("\\benum\\s+" + i.simpleName() + "\\b")
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
