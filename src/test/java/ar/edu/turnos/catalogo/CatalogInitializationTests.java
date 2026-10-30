package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.InitializeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogSnapshotUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogIncrementUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.RebuildCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogChangesSource;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogIncrementStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogHistory;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import ar.edu.turnos.catalogo.catalogo.application.usecases.SynchronizeCatalogUseCaseImpl;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withStartupTimeout(Duration.ofMinutes(2));

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
        AtomicReference<CatalogSnapshot> remoteSnapshot() {
            return new AtomicReference<>(snapshot(false));
        }

        @Bean
        AtomicReference<CatalogHistory> remoteHistory() {
            return new AtomicReference<>(new CatalogHistory(12, 10));
        }

        @Bean
        @Primary
        CatalogChangesSource historySource(AtomicReference<CatalogHistory> remoteHistory) {
            return new CatalogChangesSource() {
                public CatalogHistory history() { return remoteHistory.get(); }
                public CatalogSnapshot changes(long version) {
                    return new CatalogSnapshot(version, List.of(), List.of(), List.of());
                }
            };
        }

        @Bean
        @Primary
        CatalogSnapshotSource snapshotSource(AtomicInteger requests, AtomicBoolean fail,
                                             AtomicReference<CatalogSnapshot> remoteSnapshot) {
            return () -> {
                requests.incrementAndGet();
                if (fail.get()) {
                    throw new IllegalStateException("Catedra no disponible");
                }
                return remoteSnapshot.get();
            };
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired InitializeCatalogUseCase initialize;
    @Autowired AtomicInteger snapshotRequests;
    @Autowired AtomicBoolean failSnapshotRequest;
    @Autowired @Qualifier("catalogInitializationHealth") HealthIndicator health;
    @Autowired ApplyCatalogSnapshotUseCase applySnapshot;
    @Autowired ApplyCatalogIncrementUseCase increment;
    @Autowired SynchronizeCatalogUseCase sync;
    @Autowired RebuildCatalogUseCase rebuild;
    @Autowired CatalogDiagnosticsUseCase diagnostics;
    @Autowired CatalogChangesSource changes;
    @Autowired CatalogIncrementStore incrementStore;
    @Autowired CatalogSnapshotStore snapshotStore;
    @Autowired AtomicReference<CatalogSnapshot> remoteSnapshot;
    @Autowired AtomicReference<CatalogHistory> remoteHistory;

    @BeforeEach
    void clear() {
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");
        jdbc.update("DELETE FROM catalog_sync_state");
        jdbc.update("DELETE FROM processed_catalog_event");
        jdbc.update("UPDATE catalog_diagnostics SET attempt_id=NULL, source=NULL, activity='IDLE', "
                + "outcome='UNINITIALIZED', started_at=NULL, finished_at=NULL, last_success_at=NULL, "
                + "remote_version=NULL, remote_observed_at=NULL, last_error_code=NULL, last_error_at=NULL WHERE id=1");
        snapshotRequests.set(0);
        failSnapshotRequest.set(false);
        remoteSnapshot.set(snapshot(false));
        remoteHistory.set(new CatalogHistory(12, 10));
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

    @Test
    void incrementalUpsertRetainsUnchangedRowsAndMarksEventAtomically() {
        initialize.initializeIfEmpty();
        var changed = new CatalogSnapshot(8,
                List.of(new CatalogSnapshot.Category(1, "Actualizada", null, false, Instant.EPOCH, Instant.EPOCH)),
                List.of(), List.of());
        increment.applyAndComplete(changed, new CatalogNotification("event-8", 8));
        increment.applyAndComplete(changed, new CatalogNotification("event-8", 8));

        assertThat(jdbc.queryForObject("SELECT name FROM professional_category WHERE id=1", String.class))
                .isEqualTo("Actualizada");
        assertThat(count("professional")).isEqualTo(1);
        assertThat(count("weekly_schedule")).isEqualTo(1);
        assertThat(count("processed_catalog_event")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(8);
    }

    @Test
    void failedIncrementRollsBackEntitiesVersionAndEventMark() {
        initialize.initializeIfEmpty();
        var bad = new CatalogSnapshot(8,
                List.of(new CatalogSnapshot.Category(1, "No debe quedar", null, true, Instant.EPOCH, Instant.EPOCH)),
                List.of(new CatalogSnapshot.Professional(15, 999, "Ana", "Gomez", true, Instant.EPOCH, Instant.EPOCH)),
                List.of());
        assertThatThrownBy(() -> increment.applyAndComplete(bad, new CatalogNotification("failed-8", 8)))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT name FROM professional_category WHERE id=1", String.class)).isEqualTo("Clinica");
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(7);
        assertThat(count("processed_catalog_event")).isZero();
    }

    @Test
    void cannotSkipVersionOrMarkAnUnfinishedEvent() {
        initialize.initializeIfEmpty();
        assertThatThrownBy(() -> increment.apply(new CatalogSnapshot(9, List.of(), List.of(), List.of())))
                .isInstanceOf(CatalogRebuildRequiredException.class);
        assertThatThrownBy(() -> increment.complete(new CatalogNotification("future", 9)))
                .isInstanceOf(CatalogRebuildRequiredException.class);
        assertThat(count("processed_catalog_event")).isZero();
    }

    @Test
    void reconstructionReplacesAllCollectionsAndPreservesPreviousEventMarks() {
        initialize.initializeIfEmpty();
        increment.complete(new CatalogNotification("already-processed", 7));
        var rebuilt = new CatalogSnapshot(10,
                List.of(new CatalogSnapshot.Category(2, "Otra categoria", null, false, Instant.EPOCH, Instant.EPOCH)),
                List.of(), List.of());
        applySnapshot.replace(rebuilt, 9, Optional.of(new CatalogNotification("rebuilt", 9)));

        assertThat(count("professional_category")).isEqualTo(1);
        assertThat(count("professional")).isZero();
        assertThat(count("weekly_schedule")).isZero();
        assertThat(count("processed_catalog_event")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(10);
    }

    @Test
    void failedReplacementRestoresPreviousDataVersionAndEventHistory() {
        initialize.initializeIfEmpty();
        increment.complete(new CatalogNotification("previous", 7));
        var rebuilt = new CatalogSnapshot(9, snapshot(false).categories(),
                List.of(new CatalogSnapshot.Professional(15, 1, null, "Gomez", true, Instant.EPOCH, Instant.EPOCH)),
                List.of());

        assertThatThrownBy(() -> applySnapshot.replace(rebuilt, 9,
                Optional.of(new CatalogNotification("must-not-persist", 9)))).isInstanceOf(RuntimeException.class);
        assertThat(count("professional_category")).isEqualTo(1);
        assertThat(count("professional")).isEqualTo(1);
        assertThat(count("weekly_schedule")).isEqualTo(1);
        assertThat(count("processed_catalog_event")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(7);
    }

    @Test
    void staleSnapshotCannotRegressLocalVersionOrCompleteFutureEvent() {
        initialize.initializeIfEmpty();
        var stale = new CatalogSnapshot(6, List.of(), List.of(), List.of());
        assertThatThrownBy(() -> applySnapshot.replace(stale, 0, Optional.empty()))
                .isInstanceOf(CatalogRebuildRequiredException.class);
        assertThatThrownBy(() -> applySnapshot.replace(snapshot(false), 7,
                Optional.of(new CatalogNotification("future", 8))))
                .isInstanceOf(CatalogRebuildRequiredException.class);
        assertThat(count("professional")).isEqualTo(1);
        assertThat(count("processed_catalog_event")).isZero();
        assertThat(jdbc.queryForObject("SELECT applied_version FROM catalog_sync_state", Long.class)).isEqualTo(7);
    }

    @Test
    void discontinuityRebuildsOnceAndNewCoordinatorResumesFromPersistedVersion() {
        initialize.initializeIfEmpty();
        var data = snapshot(false);
        remoteSnapshot.set(new CatalogSnapshot(12, data.categories(), data.professionals(), data.schedules()));
        var event = new CatalogNotification("rebuild-12", 12);
        sync.synchronize(event);
        sync.synchronize(event);
        assertThat(snapshotRequests).hasValue(2); // Una carga inicial y una reconstrucción.
        assertThat(count("processed_catalog_event")).isEqualTo(1);
        assertThat(incrementStore.appliedVersion()).isEqualTo(12);

        // Simular un coordinador nuevo sobre la misma persistencia, como al reiniciar.
        var restarted = new SynchronizeCatalogUseCaseImpl(changes, incrementStore, increment,
                snapshotStore, initialize, rebuild, diagnostics);
        remoteHistory.set(new CatalogHistory(14, 10));
        restarted.reconcile();
        assertThat(incrementStore.appliedVersion()).isEqualTo(14);
        assertThat(snapshotRequests).hasValue(2);
        assertThat(count("processed_catalog_event")).isEqualTo(1);
    }

    @Test
    void diagnosticSurvivesFailureAndRestartWithoutChangingCatalogVersion() {
        initialize.initializeIfEmpty();
        var first = diagnostics.begin(CatalogDiagnostic.Source.PERIODIC);
        diagnostics.observed(7);
        diagnostics.succeed(first);
        var success = diagnostics.status();
        assertThat(success.appliedVersion()).isEqualTo(7);
        assertThat(success.outcome()).isEqualTo(CatalogDiagnostic.Outcome.CURRENT);
        assertThat(success.kafka()).isEqualTo(CatalogDiagnostic.KafkaState.DISABLED);

        var failure = diagnostics.begin(CatalogDiagnostic.Source.KAFKA);
        diagnostics.observed(8);
        diagnostics.fail(failure, CatalogDiagnostic.ErrorCode.REBUILD_REQUIRED);
        var failed = diagnostics.status();
        assertThat(failed.outcome()).isEqualTo(CatalogDiagnostic.Outcome.ERROR);
        assertThat(failed.lastSuccessAt()).isEqualTo(success.lastSuccessAt());
        assertThat(failed.lastErrorCode()).isEqualTo(CatalogDiagnostic.ErrorCode.REBUILD_REQUIRED);
        assertThat(failed.appliedVersion()).isEqualTo(7);

        var interrupted = diagnostics.begin(CatalogDiagnostic.Source.PERIODIC);
        diagnostics.activity(CatalogDiagnostic.Activity.REBUILDING);
        diagnostics.recoverInterrupted();
        assertThat(diagnostics.status().attemptId()).isEqualTo(interrupted);
        assertThat(diagnostics.status().outcome()).isEqualTo(CatalogDiagnostic.Outcome.INTERRUPTED);
        assertThat(diagnostics.status().activity()).isEqualTo(CatalogDiagnostic.Activity.IDLE);
        assertThat(diagnostics.status().lastSuccessAt()).isEqualTo(success.lastSuccessAt());
    }

    @Test
    void oldAttemptCannotOverrideLaterResultAndFailureDiagnosticsSurviveCatalogRollback() {
        initialize.initializeIfEmpty();
        var old = diagnostics.begin(CatalogDiagnostic.Source.PERIODIC);
        var current = diagnostics.begin(CatalogDiagnostic.Source.KAFKA);
        diagnostics.observed(9);
        var bad = new CatalogSnapshot(9, snapshot(false).categories(),
                List.of(new CatalogSnapshot.Professional(15, 1, null, "Gomez", true, Instant.EPOCH, Instant.EPOCH)),
                List.of());
        assertThatThrownBy(() -> applySnapshot.replace(bad, 9, Optional.empty()))
                .isInstanceOf(RuntimeException.class);
        diagnostics.fail(old, CatalogDiagnostic.ErrorCode.OTHER);
        diagnostics.fail(current, CatalogDiagnostic.ErrorCode.PERSISTENCE_UNAVAILABLE);
        assertThat(diagnostics.status().attemptId()).isEqualTo(current);
        assertThat(diagnostics.status().lastErrorCode())
                .isEqualTo(CatalogDiagnostic.ErrorCode.PERSISTENCE_UNAVAILABLE);
        assertThat(diagnostics.status().appliedVersion()).isEqualTo(7);
        assertThat(count("professional")).isEqualTo(1);
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
