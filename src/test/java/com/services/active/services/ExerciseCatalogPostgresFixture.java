package com.services.active.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.services.active.repository.ExerciseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Private schemas and a persistence-only context avoid application jobs and shared test cleanup. */
final class ExerciseCatalogPostgresFixture implements AutoCloseable {
    static final UUID EXERCISE = UUID.fromString("00000000-0000-0000-0000-000000000101");
    static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000201");
    static final UUID TEMPLATE = UUID.fromString("00000000-0000-0000-0000-000000000301");
    static final UUID TEMPLATE_EXERCISE = UUID.fromString("00000000-0000-0000-0000-000000000401");
    static final UUID WORKOUT_RECORD = UUID.fromString("00000000-0000-0000-0000-000000000501");
    static final UUID EXERCISE_RECORD = UUID.fromString("00000000-0000-0000-0000-000000000601");
    static final UUID PERSONAL_BEST = UUID.fromString("00000000-0000-0000-0000-000000000701");
    final ObjectMapper mapper = new ObjectMapper();
    final ExerciseCatalog catalog = new ExerciseCatalog();
    final DataSource dataSource;
    final JdbcTemplate jdbc;
    final String schema = "catalog_it_" + UUID.randomUUID().toString().replace("-", "");
    private final JdbcTemplate admin;
    private AnnotationConfigApplicationContext context;

    ExerciseCatalogPostgresFixture(PostgreSQLContainer<?> postgres, boolean startAtV2) {
        admin = new JdbcTemplate(new DriverManagerDataSource(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword()));
        admin.execute("CREATE SCHEMA " + schema);
        String url = postgres.getJdbcUrl() + (postgres.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=" + schema
                + "&socketTimeout=25&options=-c%20statement_timeout%3D20000%20-c%20lock_timeout%3D15000";
        dataSource = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        var configuration = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .schemas(schema).defaultSchema(schema);
        if (startAtV2) configuration.target("2");
        try {
            configuration.load().migrate();
        } catch (RuntimeException failure) {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
            throw failure;
        }
    }

    ExerciseCatalogImporter importer() {
        if (context == null) {
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .schemas(schema).defaultSchema(schema).load().migrate();
            context = new AnnotationConfigApplicationContext();
            context.getBeanFactory().registerSingleton("dataSource", dataSource);
            context.register(PersistenceConfiguration.class);
            context.refresh();
        }
        return context.getBean(ExerciseCatalogImporter.class);
    }

    ObjectNode record(int index) throws Exception {
        return (ObjectNode) mapper.readTree(catalog.pinnedJson()).get(index).deepCopy();
    }

    String json(ObjectNode... records) {
        ArrayNode array = mapper.createArrayNode();
        for (ObjectNode record : records) array.add(record);
        return array.toString();
    }

    String key(ObjectNode record) { return record.path("id").asText(); }

    void legacy(UUID id, ObjectNode record) {
        jdbc.update("""
                INSERT INTO exercises (id, name, level, force, mechanic, equipment, category,
                    primary_muscles, secondary_muscles, instructions)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::text[], ?::text[], ?::text[])
                """, id, record.path("name").asText(), constant(record, "level"), constant(record, "force"),
                constant(record, "mechanic"), constant(record, "equipment"), constant(record, "category"),
                pgArray(strings(record, "primaryMuscles")), pgArray(strings(record, "secondaryMuscles")),
                pgArray(strings(record, "instructions")));
    }

    private static String constant(ObjectNode record, String field) {
        return record.path(field).isNull() ? null
                : record.path(field).asText().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private static List<String> strings(ObjectNode record, String field) {
        List<String> values = new java.util.ArrayList<>();
        record.path(field).forEach(value -> values.add(value.asText()));
        return values;
    }

    private static String pgArray(List<String> values) {
        return "{" + String.join(",", values.stream().map(value -> "\""
                + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"").toList()) + "}";
    }

    void references(UUID exercise) {
        jdbc.update("INSERT INTO users (id, workos_id, created_at) VALUES (?, 'catalog-it-owner', DATE '2026-01-02')", OWNER);
        jdbc.update("INSERT INTO workout_templates (id, created_at, updated_at) VALUES (?, TIMESTAMPTZ '2026-01-02 10:00:00Z', TIMESTAMPTZ '2026-01-02 10:00:00Z')", TEMPLATE);
        jdbc.update("""
                INSERT INTO template_exercises (id, template_id, exercise_id, ordinal, reps, weight, duration_seconds, notes)
                VALUES (?, ?, ?, 3, ARRAY[8,6], ARRAY[40.5,42.5], ARRAY[30,45], 'template history')
                """, TEMPLATE_EXERCISE, TEMPLATE, exercise);
        jdbc.update("""
                INSERT INTO workout_records (id, user_id, workout_title, notes, start_time, created_at)
                VALUES (?, ?, 'Original workout', 'workout history', TIMESTAMPTZ '2026-01-02 10:00:00Z', TIMESTAMPTZ '2026-01-02 11:00:00Z')
                """, WORKOUT_RECORD, OWNER);
        jdbc.update("""
                INSERT INTO exercise_records (id, user_id, workout_record_id, exercise_id, ordinal, reps, weight,
                    duration_seconds, notes, achieved_one_rm_value, achieved_one_rm_set_index,
                    achieved_total_volume_value, created_at)
                VALUES (?, ?, ?, ?, 3, ARRAY[8,6], ARRAY[40.5,42.5], ARRAY[30,45], 'record history',
                    51.25, 1, 579, TIMESTAMPTZ '2026-01-02 11:00:00Z')
                """, EXERCISE_RECORD, OWNER, WORKOUT_RECORD, exercise);
        jdbc.update("""
                INSERT INTO exercise_personal_bests (id, user_id, exercise_id, one_rm, one_rm_record_id,
                    one_rm_record_set_index, total_volume, total_volume_record_id, created_at, updated_at)
                VALUES (?, ?, ?, 51.25, ?, 1, 579, ?, TIMESTAMPTZ '2026-01-02 11:00:00Z', TIMESTAMPTZ '2026-01-02 11:00:00Z')
                """, PERSONAL_BEST, OWNER, exercise, EXERCISE_RECORD, EXERCISE_RECORD);
    }

    List<String> history() {
        return List.of(tableRows("template_exercises"), tableRows("exercise_records"),
                tableRows("exercise_personal_bests"), tableRows("workout_records"));
    }

    String tableRows(String table) {
        return jdbc.queryForObject("SELECT COALESCE(jsonb_agg(to_jsonb(t) ORDER BY id), '[]'::jsonb)::text FROM "
                + table + " t", String.class);
    }

    Map<String, UUID> mappings() {
        return jdbc.query("SELECT source_key, id FROM exercises WHERE source_key IS NOT NULL ORDER BY source_key",
                result -> {
                    Map<String, UUID> mappings = new java.util.LinkedHashMap<>();
                    while (result.next()) mappings.put(result.getString(1), result.getObject(2, UUID.class));
                    return mappings;
                });
    }

    void assertReferences(UUID exercise, List<String> before) {
        assertThat(history()).isEqualTo(before);
        for (String table : List.of("template_exercises", "exercise_records", "exercise_personal_bests")) {
            assertThat(jdbc.queryForObject("SELECT exercise_id FROM " + table, UUID.class)).isEqualTo(exercise);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isEqualTo(1);
        }
    }

    @Override
    public void close() {
        try {
            if (context != null) context.close();
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ExerciseRepository.class)
    @Import({ExerciseCatalog.class, ExerciseCatalogImporter.class})
    static class PersistenceConfiguration {
        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.services.active.models");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate",
                    "hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }

        @Bean
        JpaTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }

        @Bean
        EntityManager entityManager(EntityManagerFactory factory) {
            return SharedEntityManagerCreator.createSharedEntityManager(factory);
        }
    }
}
