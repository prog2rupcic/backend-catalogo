package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.config.CatedraIntegrationProperties;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

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
            .withExposedPorts(6379).withCommand("redis-server", "--requirepass", "test-only");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
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
            assertThat(connection.ping()).isEqualTo("PONG");
        }
    }
}
