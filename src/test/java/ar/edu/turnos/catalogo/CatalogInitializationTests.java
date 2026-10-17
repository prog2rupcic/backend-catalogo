package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.InitializeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "catalog.bootstrap.enabled=false",
        "catalog.catedra.base-url=http://localhost:9999",
        "catalog.catedra.jwt=test-only",
        "management.health.redis.enabled=false"
})
@TestPropertySource("classpath:test-integration.properties")
@Testcontainers
class CatalogInitializationTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @TestConfiguration
    static class SourceConfiguration {
        @Bean
        AtomicInteger snapshotRequests() {
            return new AtomicInteger();
        }

        @Bean
        AtomicBoolean failSnapshotRequest() {
            return new AtomicBoolean();
        }

        @Bean
        @Primary
        CatalogSnapshotSource snapshotSource(AtomicInteger requests, AtomicBoolean fail) {
            return () -> {
                requests.incrementAndGet();
                if (fail.get()) {
                    throw new IllegalStateException("Catedra no disponible");
                }
                return snapshot(false);
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired InitializeCatalogUseCase initialize;
    @Autowired AtomicInteger snapshotRequests;
    @Autowired AtomicBoolean failSnapshotRequest;
    @Autowired @Qualifier("catalogInitializationHealth") HealthIndicator health;
    @Autowired ar.edu.turnos.catalogo.catalogo.application.usecases.ApplyCatalogSnapshotUseCase applySnapshot;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");
        jdbc.update("DELETE FROM catalog_sync_state");
        snapshotRequests.set(0);
        failSnapshotRequest.set(false);
    }

    @Test
    void loadsAllCollectionsAndVersionOnFirstInitialization() {
        assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");

        initialize.initializeIfEmpty();

        assertThat(count("professional_category")).isEqualTo(1);
        assertThat(count("professional")).isEqualTo(1);
        assertThat(count("weekly_schedule")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state WHERE id=1", Long.class))
                .isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT enabled FROM professional_category WHERE id=1", Boolean.class))
                .isFalse();
        assertThat(health.health().getStatus().getCode()).isEqualTo("UP");

        initialize.initializeIfEmpty();
        assertThat(snapshotRequests).hasValue(1);
    }

    @Test
    void rollsBackEvenAfterInsertingSomeCategories() {
        CatalogSnapshot bad = new CatalogSnapshot(8, snapshot(false).categories(),
                List.of(new CatalogSnapshot.Professional(15, 999, "Ana", "Gomez", true,
                        Instant.EPOCH, Instant.EPOCH)), List.of());
        assertThatThrownBy(() -> apply(bad)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count("professional_category")).isZero();
        assertThat(count("catalog_sync_state")).isZero();

        // PostgreSQL rejects the name after the first category was inserted;
        // the enclosing transaction must roll the category back too.
        CatalogSnapshot insertFails = snapshot(true);
        assertThatThrownBy(() -> apply(insertFails)).isInstanceOf(RuntimeException.class);
        assertThat(count("professional_category")).isZero();
        assertThat(count("professional")).isZero();
        assertThat(count("catalog_sync_state")).isZero();
        assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
    }

    @Test
    void failedFetchLeavesCatalogUninitialized() {
        failSnapshotRequest.set(true);
        assertThatThrownBy(() -> initialize.initializeIfEmpty())
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("catalog_sync_state")).isZero();
        assertThat(health.health().getStatus().getCode()).isEqualTo("DOWN");
    }

    private void apply(CatalogSnapshot snapshot) {
        applySnapshot.applyIfEmpty(snapshot);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private static CatalogSnapshot snapshot(boolean invalidProfessionalName) {
        Instant now = Instant.parse("2026-09-10T13:40:00Z");
        return new CatalogSnapshot(7,
                List.of(new CatalogSnapshot.Category(1, "Clinica", null, false, now, now)),
                List.of(new CatalogSnapshot.Professional(15, 1,
                        invalidProfessionalName ? null : "Ana", "Gomez", true, now, now)),
                List.of(new CatalogSnapshot.WeeklySchedule(21, 15, DayOfWeek.MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(12, 0), 30, true, now, now)));
    }
}
