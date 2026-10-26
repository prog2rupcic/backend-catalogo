package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogHistory;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogChangesSource;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "catalog.catedra.base-url=http://localhost:9999", "catalog.catedra.jwt=test-only",
        "catalog.reconciliation.enabled=true", "catalog.reconciliation.interval=100ms",
        "catalog.reconciliation.retry-delay=5ms"
})
@TestPropertySource("classpath:test-integration.properties")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogRecoverySchedulingTests {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    static class RemoteCatalog implements CatalogSnapshotSource, CatalogChangesSource {
        final AtomicBoolean offline = new AtomicBoolean(true);
        final AtomicLong version = new AtomicLong(7);
        final AtomicLong oldest = new AtomicLong(1);
        final AtomicInteger snapshotCalls = new AtomicInteger();

        private void requireConnection() {
            if (offline.get()) {
                throw new TransientDataAccessResourceException("test outage");
            }
        }

        @Override
        public CatalogSnapshot fetch() {
            snapshotCalls.incrementAndGet();
            requireConnection();
            return data(version.get());
        }

        @Override
        public CatalogHistory history() {
            requireConnection();
            return new CatalogHistory(version.get(), oldest.get());
        }

        @Override
        public CatalogSnapshot changes(long next) {
            requireConnection();
            return data(next);
        }

        private CatalogSnapshot data(long v) {
            return new CatalogSnapshot(v, List.of(new CatalogSnapshot.Category(
                    1, "category-" + v, null, false, Instant.EPOCH, Instant.EPOCH)), List.of(), List.of());
        }
    }

    @TestConfiguration
    static class Fixture {
        @Bean
        @Primary
        RemoteCatalog remoteCatalog() {
            return new RemoteCatalog();
        }
    }

    @Autowired RemoteCatalog remote;
    @Autowired JdbcTemplate jdbc;

    @Test
    void schedulerRecoversInitialOutageRebuildsAndDetectsChangesWithoutKafka() {
        assertThat(remote.snapshotCalls.get()).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_sync_state", Integer.class)).isZero();

        remote.offline.set(false);
        awaitVersion(7);

        int beforeRebuild = remote.snapshotCalls.get();
        remote.oldest.set(8);
        remote.version.set(8);
        awaitVersion(8);
        assertThat(remote.snapshotCalls.get()).isGreaterThan(beforeRebuild);

        // Ningún evento Kafka: el scheduler aplica 9 y 10 al recuperarse la fuente.
        remote.offline.set(true);
        remote.version.set(10);
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(8);
        remote.offline.set(false);
        awaitVersion(10);
        assertThat(jdbc.queryForObject("SELECT name FROM professional_category WHERE id=1", String.class))
                .isEqualTo("category-10");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM processed_catalog_event", Integer.class)).isZero();
    }

    private void awaitVersion(long expected) {
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(jdbc.queryForList("SELECT applied_version FROM catalog_sync_state", Long.class))
                        .containsExactly(expected));
    }
}
