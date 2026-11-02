package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogDiagnosticStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogKafkaStatusSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CatalogDiagnosticsUseCaseTests {
    @Test
    void usesAnInjectedUtcClockAndIncludesLiveKafkaState() {
        var at = Instant.parse("2026-10-01T12:00:00Z");
        var store = mock(CatalogDiagnosticStore.class);
        var kafka = mock(CatalogKafkaStatusSource.class);
        var useCase = new CatalogDiagnosticsUseCaseImpl(store, Optional.of(kafka), Clock.fixed(at, ZoneOffset.UTC));
        var id = useCase.begin(CatalogDiagnostic.Source.STARTUP);
        useCase.observed(8);
        useCase.succeed(id);
        verify(store).begin(id, CatalogDiagnostic.Source.STARTUP, at);
        verify(store).observed(id, 8, at);
        verify(store).succeed(id, at);

        var saved = new CatalogDiagnostic(8L, 8L, at, id, CatalogDiagnostic.Source.STARTUP,
                CatalogDiagnostic.Activity.IDLE, CatalogDiagnostic.Outcome.CURRENT, at, at, at,
                null, null, CatalogDiagnostic.KafkaState.DISABLED);
        when(store.read()).thenReturn(saved);
        when(kafka.state()).thenReturn(CatalogDiagnostic.KafkaState.WAITING);
        assertThat(useCase.status().kafka()).isEqualTo(CatalogDiagnostic.KafkaState.WAITING);
    }
}
