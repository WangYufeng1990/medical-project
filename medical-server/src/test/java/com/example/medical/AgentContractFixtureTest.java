package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 55.1 — the Java side of the Java ⇄ Python contract.
 * <p>
 * Both runtimes read {@code contracts/agent/v1/}: the JSON Schemas are generated from the
 * Pydantic models and the fixtures are what the two languages agree on. This test checks
 * every fixture against the schema it belongs to, so a payload one side refuses cannot
 * quietly pass the other.
 * <p>
 * The validator below is a small, deliberate implementation of <em>exactly</em> the
 * keywords these contracts use — {@code $ref}, {@code anyOf}, {@code type},
 * {@code required}, {@code additionalProperties}, {@code enum}, {@code const},
 * {@code minimum}/{@code exclusiveMinimum}/{@code maximum}/{@code exclusiveMaximum},
 * {@code minLength}/{@code maxLength} and {@code format: date-time}. A mature JSON Schema
 * library would be preferable, but this sandbox cannot add one to the Maven repository, so
 * the risk is bounded instead: {@link #theSchemasOnlyUseKeywordsThisValidatorChecks()}
 * fails when a schema starts using a keyword the validator does not implement, and
 * {@link #theSchemasOnlyUseFormatsThisValidatorChecks()} does the same for formats. The
 * hand-rolled version this replaced skipped {@code null} and ignored numeric bounds,
 * string lengths and {@code anyOf}, which is why {@code run_id: null} used to pass here
 * and fail in Python.
 */
class AgentContractFixtureTest {

    private static final Path CONTRACTS = Path.of("..", "contracts", "agent", "v1");
    private static final Path FIXTURES = CONTRACTS.resolve("fixtures");

    /** Keywords this validator enforces; anything else in a schema is a test failure. */
    private static final Set<String> SUPPORTED_KEYWORDS = Set.of(
            "$defs", "$ref", "anyOf", "type", "required", "additionalProperties",
            "enum", "const", "minimum", "exclusiveMinimum", "maximum", "exclusiveMaximum",
            "minLength", "maxLength", "format",
            // structural/annotation keywords: they carry no assertion of their own
            "properties", "title", "description", "default");

    private static final Set<String> SUPPORTED_FORMATS = Set.of("date-time");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void everySchemaForbidsUnknownFieldsAndDeclaresARequiredSet() throws IOException {
        List<String> schemas = schemaFiles();
        assertTrue(schemas.size() >= 3, "expected the run start/status and error schemas, found " + schemas);

        for (String name : schemas) {
            JsonNode schema = read(CONTRACTS.resolve(name));
            assertTrue(schema.path("additionalProperties").isBoolean()
                            && !schema.path("additionalProperties").asBoolean(),
                    name + " must reject fields the contract does not define");
            assertTrue(schema.path("required").isArray(),
                    name + " must declare which fields are required");
        }
    }

    @Test
    void theSchemasOnlyUseKeywordsThisValidatorChecks() throws IOException {
        Set<String> used = new TreeSet<>();
        for (String name : schemaFiles()) {
            collectKeywords(read(CONTRACTS.resolve(name)), used);
        }
        used.removeAll(SUPPORTED_KEYWORDS);

        assertTrue(used.isEmpty(),
                "the schemas use keywords this validator does not implement: " + used
                        + " — implement them here before relying on the contract test");
    }

    @Test
    void theSchemasOnlyUseFormatsThisValidatorChecks() throws IOException {
        Set<String> formats = new TreeSet<>();
        for (String name : schemaFiles()) {
            collectFormats(read(CONTRACTS.resolve(name)), formats);
        }
        formats.removeAll(SUPPORTED_FORMATS);

        assertTrue(formats.isEmpty(), "unchecked formats in the contract: " + formats);
    }

    @Test
    void validFixturesFitTheirSchema() throws IOException {
        for (Path fixture : fixtures()) {
            if (!fixture.getFileName().toString().contains(".valid.")) continue;
            JsonNode schema = schemaFor(fixture);
            List<String> violations = validate(read(fixture), schema, schema, "$");
            assertTrue(violations.isEmpty(),
                    fixture.getFileName() + " must satisfy its schema: " + violations);
        }
    }

    @Test
    void invalidFixturesViolateTheirSchema() throws IOException {
        List<Path> invalid = fixtures().stream()
                .filter(path -> path.getFileName().toString().contains(".invalid-"))
                .toList();
        assertTrue(invalid.size() >= 6, "expected the boundary fixtures, found " + invalid);

        for (Path fixture : invalid) {
            JsonNode schema = schemaFor(fixture);
            List<String> violations = validate(read(fixture), schema, schema, "$");
            assertTrue(!violations.isEmpty(),
                    fixture.getFileName() + " is marked invalid but satisfies its schema");
        }
    }

    @Test
    void aNullNumberOrBooleanForAnIdentityFieldIsATypeError() throws IOException {
        // The payloads the old validator let through: null was skipped outright, and the
        // other two must fail on the field's type rather than an incidental keyword.
        for (String name : List.of("run-start.invalid-null-run-id.json",
                "run-start.invalid-numeric-string-run-id.json",
                "run-start.invalid-boolean-run-id.json")) {
            Path fixture = FIXTURES.resolve(name);
            JsonNode schema = schemaFor(fixture);
            List<String> violations = validate(read(fixture), schema, schema, "$");

            assertTrue(violations.stream().anyMatch(message -> message.contains("$.run_id")),
                    name + " must be refused on run_id: " + violations);
        }
    }

    @Test
    void aTimestampThatIsNotADateIsRefused() throws IOException {
        Path fixture = FIXTURES.resolve("run-status.invalid-timestamp.json");
        JsonNode schema = schemaFor(fixture);

        List<String> violations = validate(read(fixture), schema, schema, "$");

        assertTrue(violations.stream().anyMatch(message -> message.contains("$.updated_at")),
                "the timestamp must be checked, not assumed: " + violations);
    }

    @Test
    void everyFixtureIsCoveredByASchema() throws IOException {
        for (Path fixture : fixtures()) {
            assertTrue(schemaFor(fixture) != null, "no schema for fixture " + fixture.getFileName());
        }
    }

    // ── the fixtures and their schemas ──────────────────────────────

    private List<String> schemaFiles() throws IOException {
        try (Stream<Path> files = Files.list(CONTRACTS)) {
            return files.filter(path -> path.toString().endsWith(".schema.json"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
    }

    private List<Path> fixtures() throws IOException {
        try (Stream<Path> files = Files.list(FIXTURES)) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    /** {@code run-start.valid.json} → {@code run-start.request.schema.json}. */
    private JsonNode schemaFor(Path fixture) throws IOException {
        String name = fixture.getFileName().toString();
        String prefix = name.substring(0, name.indexOf('.'));
        try (Stream<Path> files = Files.list(CONTRACTS)) {
            return files.filter(path -> path.getFileName().toString().startsWith(prefix + "."))
                    .filter(path -> path.toString().endsWith(".schema.json"))
                    .findFirst()
                    .map(path -> {
                        try {
                            return read(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .orElse(null);
        }
    }

    private JsonNode read(Path path) throws IOException {
        return objectMapper.readTree(Files.readString(path));
    }

    /** Keywords of every subschema; property names are data and are traversed as such. */
    private void collectKeywords(JsonNode node, Set<String> into) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String name = field.getKey();
                if (name.equals("properties") || name.equals("$defs")) {
                    // Both are maps whose keys are names (property or schema), not
                    // keywords; only their values are schemas.
                    into.add(name);
                    field.getValue().fields().forEachRemaining(entry ->
                            collectKeywords(entry.getValue(), into));
                    continue;
                }
                into.add(name);
                collectKeywords(field.getValue(), into);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectKeywords(child, into));
        }
    }

    private void collectFormats(JsonNode node, Set<String> into) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getKey().equals("format") && field.getValue().isTextual()) {
                    into.add(field.getValue().asText());
                }
                collectFormats(field.getValue(), into);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectFormats(child, into));
        }
    }

    // ── the validator ───────────────────────────────────────────────

    /**
     * The subset of JSON Schema the agent contracts use. Returns one message per
     * violation, so a failing fixture says what it broke.
     */
    private List<String> validate(JsonNode value, JsonNode schema, JsonNode root, String where) {
        List<String> violations = new ArrayList<>();

        if (schema.has("$ref")) {
            return validate(value, resolve(root, schema.path("$ref").asText()), root, where);
        }
        if (schema.has("anyOf")) {
            List<String> failures = new ArrayList<>();
            for (JsonNode option : schema.path("anyOf")) {
                List<String> attempt = validate(value, option, root, where);
                if (attempt.isEmpty()) return violations;
                failures.addAll(attempt);
            }
            violations.add(where + ": matches none of the allowed shapes " + failures);
            return violations;
        }
        if (value.isNull()) {
            if (!"null".equals(schema.path("type").asText())) {
                violations.add(where + ": null is not allowed here");
            }
            return violations;
        }
        if (schema.has("const") && !value.asText().equals(schema.path("const").asText())) {
            violations.add(where + ": expected " + schema.path("const") + ", got " + value);
        }
        if (schema.has("enum")) {
            Set<String> allowed = new TreeSet<>();
            schema.path("enum").forEach(node -> allowed.add(node.asText()));
            if (!allowed.contains(value.asText())) {
                violations.add(where + ": " + value + " is not one of " + allowed);
            }
        }
        if (schema.has("type") && !hasType(value, schema.path("type").asText())) {
            violations.add(where + ": expected type " + schema.path("type").asText()
                    + ", got " + value.getNodeType());
            return violations;
        }
        if (value.isNumber()) {
            violations.addAll(checkBounds(value, schema, where));
        }
        if (value.isTextual()) {
            violations.addAll(checkText(value.asText(), schema, where));
        }
        if (value.isObject()) {
            violations.addAll(checkObject(value, schema, root, where));
        }
        return violations;
    }

    private List<String> checkBounds(JsonNode value, JsonNode schema, String where) {
        List<String> violations = new ArrayList<>();
        double number = value.asDouble();
        if (schema.has("minimum") && number < schema.path("minimum").asDouble()) {
            violations.add(where + ": " + number + " is below the minimum " + schema.path("minimum"));
        }
        if (schema.has("exclusiveMinimum") && number <= schema.path("exclusiveMinimum").asDouble()) {
            violations.add(where + ": " + number + " is not above " + schema.path("exclusiveMinimum"));
        }
        if (schema.has("maximum") && number > schema.path("maximum").asDouble()) {
            violations.add(where + ": " + number + " is above the maximum " + schema.path("maximum"));
        }
        if (schema.has("exclusiveMaximum") && number >= schema.path("exclusiveMaximum").asDouble()) {
            violations.add(where + ": " + number + " is not below " + schema.path("exclusiveMaximum"));
        }
        return violations;
    }

    private List<String> checkText(String text, JsonNode schema, String where) {
        List<String> violations = new ArrayList<>();
        // JSON Schema counts Unicode code points, and so does Python's len(). String.length()
        // counts UTF-16 code units, so an emoji would count twice and a valid payload would
        // be rejected here while Python accepts it (Round 55.1 review).
        int length = text.codePointCount(0, text.length());
        if (schema.has("minLength") && length < schema.path("minLength").asInt()) {
            violations.add(where + ": shorter than " + schema.path("minLength") + " characters");
        }
        if (schema.has("maxLength") && length > schema.path("maxLength").asInt()) {
            violations.add(where + ": longer than " + schema.path("maxLength") + " characters");
        }
        if ("date-time".equals(schema.path("format").asText()) && !isDateTime(text)) {
            violations.add(where + ": '" + text + "' is not an ISO-8601 date-time");
        }
        return violations;
    }

    private List<String> checkObject(JsonNode value, JsonNode schema, JsonNode root, String where) {
        List<String> violations = new ArrayList<>();
        for (JsonNode required : schema.path("required")) {
            if (!value.has(required.asText())) {
                violations.add(where + ": missing required field '" + required.asText() + "'");
            }
        }
        if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean()) {
            Iterator<String> names = value.fieldNames();
            while (names.hasNext()) {
                String field = names.next();
                if (!schema.path("properties").has(field)) {
                    violations.add(where + ": unknown field '" + field + "'");
                }
            }
        }
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode propertySchema = schema.path("properties").path(field.getKey());
            if (!propertySchema.isMissingNode()) {
                violations.addAll(validate(field.getValue(), propertySchema, root,
                        where + "." + field.getKey()));
            }
        }
        return violations;
    }

    private JsonNode resolve(JsonNode root, String reference) {
        String pointer = reference.substring(reference.lastIndexOf('/') + 1);
        JsonNode target = root.path("$defs").path(pointer);
        assertTrue(!target.isMissingNode(), "unresolvable $ref " + reference);
        return target;
    }

    private boolean isDateTime(String text) {
        try {
            OffsetDateTime.parse(text);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean hasType(JsonNode value, String type) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "null" -> value.isNull();
            default -> false;
        };
    }
}
