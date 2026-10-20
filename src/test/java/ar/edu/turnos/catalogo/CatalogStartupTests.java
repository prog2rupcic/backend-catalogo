package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.config.CatedraIntegrationProperties;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
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
        "catalog.catedra.jwt=test-only"
})
@TestPropertySource("classpath:test-integration.properties")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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
        setVersion("1");
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

    @Test
    void firstStartupPersistsVersionEvenForEmptyCollections() {
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state WHERE id=1", Long.class))
                .isEqualTo(3);
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    }

    @Test
    void bindsRedisAndKafkaConfigurationWithoutConsumingEvents() {
        assertThat(integration.getKafkaCatalogTopic()).isEqualTo("catedra.catalog.test");
        assertThat(kafka.getBootstrapServers()).containsExactly("localhost:9092");
        assertThat(kafka.getConsumer().getGroupId()).isEqualTo("alumnos-test");
        try (var connection = redisConnectionFactory.getConnection()) {
            assertThatThrownBy(connection::ping).hasStackTraceContaining("NOPERM");
            assertThatThrownBy(connection.serverCommands()::info).hasStackTraceContaining("NOPERM");
        }
        assertThat(redisHealth.health().getStatus().getCode()).isEqualTo("UP");
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
