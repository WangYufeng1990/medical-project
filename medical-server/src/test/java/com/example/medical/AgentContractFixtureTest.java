package com.example.medical;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Round 55.1 — the Java side of the Java ⇄ Python contract.
 * <p>
 * Both runtimes read {@code contracts/agent/v1/}: the JSON Schemas are generated from the
 * Pydantic models and the fixtures are what the two languages agree on. This test makes
 * the agreement checkable from the Java side without adding a schema-validation
 * dependency: it reads the committed schemas and checks the fixtures against the subset
 * of JSON Schema they use ({@code type}, {@code required}, {@code enum}, {@code const},
 * {@code additionalProperties}), so a schema change that the fixtures do not follow — or
 * a fixture nobody updated — fails the backend build.
 */
class AgentContractFixtureTest {

    private static final Path CONTRACTS = Path.of("..", "contracts", "agent", "v1");
    private static final Path FIXTURES = CONTRACTS.resolve("fixtures");

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
    void validFixturesFitTheirSchema() throws IOException {
        for (Path fixture : fixtures()) {
            if (!fixture.getFileName().toString().contains(".valid.")) continue;
            JsonNode schema = schemaFor(fixture);
            List<String> violations = validate(read(fixture), schema, schema, "$");
            assertTrue(violations.isEmpty(), fixture.getFileName() + " must satisfy its schema: " + violations);
        }
    }

    @Test
    void invalidFixturesViolateTheirSchema() throws IOException {
        for (Path fixture : fixtures()) {
            if (!fixture.getFileName().toString().contains(".invalid-")) continue;
            JsonNode schema = schemaFor(fixture);
            List<String> violations = validate(read(fixture), schema, schema, "$");
            assertTrue(!violations.isEmpty(),
                    fixture.getFileName() + " is marked invalid but satisfies its schema");
        }
    }

    @Test
    void everyFixtureIsCoveredByASchema() throws IOException {
        for (Path fixture : fixtures()) {
            assertTrue(schemaFor(fixture) != null, "no schema for fixture " + fixture.getFileName());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────

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
                            return fail("could not read schema for " + name + ": " + e.getMessage());
                        }
                    })
                    .orElse(null);
        }
    }

    private JsonNode read(Path path) throws IOException {
        return objectMapper.readTree(Files.readString(path));
    }

    /**
     * The subset of JSON Schema the agent contracts use. Returns one message per
     * violation, so a failing fixture says what it broke.
     */
    private List<String> validate(JsonNode value, JsonNode schema, JsonNode root, String where) {
        List<String> violations = new ArrayList<>();
        if (schema.has("$ref")) {
            JsonNode target = resolve(root, schema.path("$ref").asText());
            return validate(value, target, root, where);
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
            violations.add(where + ": expected type " + schema.path("type").asText() + ", got " + value.getNodeType());
        }
        if (value.isObject()) {
            for (JsonNode required : schema.path("required")) {
                if (!value.has(required.asText())) {
                    violations.add(where + ": missing required field '" + required.asText() + "'");
                }
            }
            if (schema.has("additionalProperties") && !schema.path("additionalProperties").asBoolean()) {
                Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
                while (fields.hasNext()) {
                    String field = fields.next().getKey();
                    if (!schema.path("properties").has(field)) {
                        violations.add(where + ": unknown field '" + field + "'");
                    }
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode propertySchema = schema.path("properties").path(field.getKey());
                if (!propertySchema.isMissingNode() && !field.getValue().isNull()) {
                    violations.addAll(validate(field.getValue(), propertySchema, root, where + "." + field.getKey()));
                }
            }
        }
        return violations;
    }

    private JsonNode resolve(JsonNode root, String reference) {
        String pointer = reference.substring(reference.lastIndexOf('/') + 1);
        return root.path("$defs").path(pointer);
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
