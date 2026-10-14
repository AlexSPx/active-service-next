package com.services.active.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.services.active.models.Exercise;
import com.services.active.models.types.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

/** Strict boundary for the authoritative snapshot and reviewed legacy matching contract. */
@Component
public class ExerciseCatalog {
    private static final String SHA = "5bb747e3fc658f095a60dcbf6d53c96627acdcc6ffb6fffde86f7e26995d40bf";
    private static final String REVISION = "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5";
    private static final List<String> STORED_FIELDS = List.of("name", "level", "force", "mechanic", "equipment",
            "primaryMuscles", "secondaryMuscles", "instructions", "category");
    private static final Set<String> SOURCE_FIELDS = Set.of("id", "name", "level", "force", "mechanic", "equipment",
            "primaryMuscles", "secondaryMuscles", "instructions", "category", "images");
    private final ObjectMapper mapper = new ObjectMapper().enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final String pinnedJson;
    private final Map<String, Exercise> baseline;
    private final Map<String, String> legacyFingerprints;

    public ExerciseCatalog() {
        try {
            byte[] source;
            try (var input = new ClassPathResource("catalog/exercises.json").getInputStream()) {
                source = input.readAllBytes();
            }
            require(SHA.equals(hash(source)), "Pinned catalog checksum mismatch");
            pinnedJson = new String(source, StandardCharsets.UTF_8);
            baseline = parse(pinnedJson);
            JsonNode manifest;
            try (var input = new ClassPathResource("catalog/legacy-fingerprints.json").getInputStream()) {
                manifest = mapper.readTree(input);
            }
            require("reviewed".equals(manifest.path("status").asText()), "Manifest is not reviewed");
            JsonNode approval = manifest.path("approval");
            require("Codex parent orchestrator".equals(approval.path("reviewer").asText()) &&
                    approval.path("scope").isTextual() && !approval.path("scope").asText().isBlank() &&
                    approval.path("evidence").isTextual() && !approval.path("evidence").asText().isBlank(),
                    "Manifest review metadata is missing");
            require(SHA.equals(manifest.path("source").path("sha256").asText()) &&
                    REVISION.equals(manifest.path("source").path("revision").asText()), "Manifest source mismatch");
            require("SHA-256".equals(manifest.path("fingerprint").path("algorithm").asText()) &&
                    mapper.valueToTree(STORED_FIELDS).equals(manifest.path("fingerprint").path("storedFields")),
                    "Manifest fingerprint contract mismatch");
            JsonNode entries = manifest.path("entries");
            require(entries.isArray() && entries.size() == baseline.size(), "Manifest coverage mismatch");
            Map<String, String> fingerprints = new HashMap<>();
            Set<String> unique = new HashSet<>();
            for (JsonNode entry : entries) {
                String key = text(entry, "sourceKey");
                String fingerprint = text(entry, "legacyFingerprint");
                require(baseline.containsKey(key) && fingerprint.equals(fingerprint(baseline.get(key))),
                        "Manifest fingerprint mismatch: " + key);
                require(fingerprints.putIfAbsent(key, fingerprint) == null && unique.add(fingerprint),
                        "Duplicate manifest key or fingerprint: " + key);
            }
            legacyFingerprints = Map.copyOf(fingerprints);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read pinned exercise catalog", e);
        }
    }

    public String pinnedJson() { return pinnedJson; }
    public String legacyFingerprint(String key) { return legacyFingerprints.get(key); }
    public String legacyName(String key) { return baseline.containsKey(key) ? baseline.get(key).getName() : null; }

    public Map<String, Exercise> parse(String json) {
        try {
            JsonNode records = mapper.readTree(json);
            require(records != null && records.isArray() && !records.isEmpty(), "Catalog must be a nonempty array");
            Map<String, Exercise> result = new LinkedHashMap<>();
            for (JsonNode record : records) {
                Set<String> fields = new HashSet<>();
                record.fieldNames().forEachRemaining(fields::add);
                require(record.isObject() && fields.equals(SOURCE_FIELDS), "Malformed catalog fields");
                String key = text(record, "id");
                Exercise exercise = Exercise.builder()
                        .sourceKey(key).name(text(record, "name"))
                        .level(enumValue(record, "level", Level::fromName, false))
                        .force(enumValue(record, "force", ForceType::fromName, true))
                        .mechanic(enumValue(record, "mechanic", MechanicType::fromName, true))
                        .equipment(enumValue(record, "equipment", Equipment::fromName, true))
                        .category(enumValue(record, "category", Category::fromName, false))
                        .primaryMuscles(array(record, "primaryMuscles"))
                        .secondaryMuscles(array(record, "secondaryMuscles"))
                        .instructions(array(record, "instructions")).build();
                array(record, "images");
                require(result.putIfAbsent(key, exercise) == null, "Duplicate source key: " + key);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Malformed catalog JSON", e);
        }
    }

    public String fingerprint(Exercise exercise) {
        Map<String, Object> values = new TreeMap<>();
        values.put("name", exercise.getName());
        values.put("level", constant(exercise.getLevel()));
        values.put("force", constant(exercise.getForce()));
        values.put("mechanic", constant(exercise.getMechanic()));
        values.put("equipment", constant(exercise.getEquipment()));
        values.put("category", constant(exercise.getCategory()));
        values.put("primaryMuscles", exercise.getPrimaryMuscles());
        values.put("secondaryMuscles", exercise.getSecondaryMuscles());
        values.put("instructions", exercise.getInstructions());
        try {
            return hash(mapper.writeValueAsBytes(values));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot fingerprint exercise", e);
        }
    }

    private static String constant(Enum<?> value) { return value == null ? null : value.name(); }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isTextual() && !value.asText().isBlank(), "Expected nonblank text: " + field);
        return value.asText();
    }

    private static <T> T enumValue(JsonNode node, String field, Function<String, T> converter, boolean nullable) {
        if (nullable && node.path(field).isNull()) return null;
        T value = converter.apply(text(node, field));
        require(value != null, "Unknown enum: " + field);
        return value;
    }

    private static List<String> array(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isArray(), "Expected array: " + field);
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            require(item.isTextual(), "Expected text array: " + field);
            result.add(item.asText());
        }
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
