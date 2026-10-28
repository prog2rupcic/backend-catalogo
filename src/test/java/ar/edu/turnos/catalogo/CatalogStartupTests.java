package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.config.CatedraIntegrationProperties;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogChangesSource;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap.CatalogReconciliationJob;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "catalog.catedra.base-url=http://localhost:9999",
        "catalog.catedra.jwt=test-only",
        "catalog.kafka.enabled=true"
})
@EmbeddedKafka(partitions = 1, topics = "catedra.catalog.test", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource("classpath:test-integration.properties")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CatalogStartupTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCopyFileToContainer(MountableFile.forClasspathResource("redis/catalog-test.acl"), "/tmp/catalog.acl")
            .withCommand("redis-server", "--aclfile", "/tmp/catalog.acl");

    @BeforeAll
    static void seedVersion() throws Exception {
        setVersion("3");
        redis.execInContainer("redis-cli", "HSET", "catedra:sync:metadata",
                "currentVersion", "3", "oldestAvailableVersion", "1");
    }

    private static void setVersion(String value) throws Exception {
        var result = redis.execInContainer("redis-cli", "SET", "catedra:sync:current-version", value);
        assertThat(result.getExitCode()).isZero();
        assertThat(result.getStdout()).contains("OK");
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.username", () -> "grp_test");
    }

    @TestConfiguration
    static class SnapshotFixture {
        @Bean
        @Primary
        CatalogSnapshotSource source() {
            return () -> new CatalogSnapshot(3, List.of(), List.of(), List.of());
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired HealthEndpoint health;
    @Autowired CatedraIntegrationProperties integration;
    @Autowired KafkaProperties kafka;
    @Autowired LettuceConnectionFactory redisConnectionFactory;
    @Autowired @Qualifier("catalogRedisHealthIndicator") HealthIndicator redisHealth;
    @Autowired CatalogChangesSource changes;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @Autowired KafkaListenerEndpointRegistry listeners;
    @Autowired CatalogReconciliationJob reconciliation;

    @Test
    @Order(1)
    void firstStartupPersistsVersionEvenForEmptyCollections() {
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state WHERE id=1", Long.class))
                .isEqualTo(3);
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void bindsRedisAndKafkaConfigurationWithoutConsumingEvents() {
        assertThat(integration.getKafkaCatalogTopic()).isEqualTo("catedra.catalog.test");
        assertThat(kafka.getBootstrapServers()).isNotEmpty();
        assertThat(kafka.getConsumer().getGroupId()).isEqualTo("alumnos-test");
        try (var connection = redisConnectionFactory.getConnection()) {
            assertThatThrownBy(connection::ping).hasStackTraceContaining("NOPERM");
            assertThatThrownBy(connection.serverCommands()::info).hasStackTraceContaining("NOPERM");
        }
        assertThat(redisHealth.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void redisReadsIdsAndCurrentEntitiesAndRejectsMissingIncrement() throws Exception {
        seedChanges();
        assertThat(changes.history().currentVersion()).isEqualTo(4);
        assertThat(changes.changes(4).categories().getFirst().enabled()).isFalse();
        assertThatThrownBy(() -> changes.changes(999)).isInstanceOf(CatalogRebuildRequiredException.class);
    }

    private static void seedChanges() throws Exception {
        redis.execInContainer("redis-cli", "HSET", "catedra:sync:metadata",
                "currentVersion", "4", "oldestAvailableVersion", "1");
        redis.execInContainer("redis-cli", "SET", "catedra:sync:changes:4", """
                {"version":4,"changes":{"professionalCategories":[1],"professionals":[],"weeklySchedules":[]},"extra":true}
                """);
        redis.execInContainer("redis-cli", "HSET", "catedra:sync:professional-categories", "1", """
                {"id":1,"name":"Clinica","description":null,"enabled":false,
                 "createdAt":"2026-07-01T12:00:00Z","updatedAt":"2026-09-10T13:40:00Z","extra":true}
                """);
    }

    @Test
    @Order(2)
    void kafkaDeliveryIsPersistedOnceDespiteDuplicateMessages() throws Exception {
        seedChanges();
        String payload = """
                {"eventId":"kafka-4","eventType":"CatalogUpdated","newVersion":4,"schemaVersion":1}
                """;
        kafkaTemplate.send("catedra.catalog.test", "4", payload).get();
        kafkaTemplate.send("catedra.catalog.test", "4", payload).get();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM processed_catalog_event WHERE event_id='kafka-4'", Integer.class)).isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(4);
                assertThat(jdbc.queryForObject("SELECT enabled FROM professional_category WHERE id=1", Boolean.class)).isFalse();
        });
    }

    @Test
    @Order(3)
    void failedKafkaEventIsReplayedAfterReconciliationRestartsConsumer() throws Exception {
        // No changes:5 y snapshot REST de la fixture obsoleto: no se puede confirmar.
        redis.execInContainer("redis-cli", "HSET", "catedra:sync:metadata", "currentVersion", "5");
        kafkaTemplate.send("catedra.catalog.test", "5", """
                {"eventId":"kafka-5","eventType":"CatalogUpdated","newVersion":5,"schemaVersion":1}
                """).get();
        var container = listeners.getListenerContainer("catalog-updates");
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20))
                .until(() -> !container.isRunning());
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_catalog_event WHERE event_id='kafka-5'", Integer.class)).isZero();

        redis.execInContainer("redis-cli", "SET", "catedra:sync:changes:5", """
                {"version":5,"changes":{"professionalCategories":[],"professionals":[],"weeklySchedules":[]}}
                """);
        reconciliation.runCycle();
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM processed_catalog_event WHERE event_id='kafka-5'", Integer.class)).isEqualTo(1);
        });
    }

    @Test
    @Order(4)
    void invalidKafkaMessageStaysStoppedEvenAfterSuccessfulReconciliation() throws Exception {
        kafkaTemplate.send("catedra.catalog.test", "5", """
                {"eventId":"invalid","eventType":"CatalogUpdated","newVersion":5,"schemaVersion":99}
                """).get();
        var container = listeners.getListenerContainer("catalog-updates");
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(20))
                .until(() -> !container.isRunning());
        reconciliation.runCycle();
        assertThat(container.isRunning()).isFalse();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM processed_catalog_event WHERE event_id='invalid'", Integer.class)).isZero();
    }

    @Test
    void missingVersionReportsDownWithoutExposingSecrets() throws Exception {
        redis.execInContainer("redis-cli", "DEL", "catedra:sync:current-version");
        try {
            var result = redisHealth.health();
            assertThat(result.getStatus().getCode()).isEqualTo("DOWN");
            assertThat(result.getDetails()).containsEntry("reason", "CURRENT_VERSION_MISSING");
            assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
        } finally {
            setVersion("1");
        }
    }

    @Test
    void invalidVersionsReportDown() throws Exception {
        try {
            for (String value : List.of("abc", "-1", "", "9223372036854775808")) {
                setVersion(value);
                var result = redisHealth.health();
                assertThat(result.getStatus().getCode()).isEqualTo("DOWN");
                assertThat(result.getDetails()).containsEntry("reason", "CURRENT_VERSION_INVALID");
            }
        } finally {
            setVersion("1");
        }
    }
}
