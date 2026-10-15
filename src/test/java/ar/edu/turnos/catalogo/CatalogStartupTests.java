package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "catalog.catedra.base-url=http://localhost:9999",
        "catalog.catedra.jwt=test-only"
})
@Testcontainers
class CatalogStartupTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
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

    @Test
    void firstStartupPersistsVersionEvenForEmptyCollections() {
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state WHERE id=1", Long.class))
                .isEqualTo(3);
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
    }
}
