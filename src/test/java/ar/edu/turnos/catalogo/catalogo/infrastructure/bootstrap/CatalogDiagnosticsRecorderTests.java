package ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CatalogDiagnosticsRecorderTests {
    @Test
    void diagnosticWriteFailureDoesNotPropagateToCatalogOperation() {
        var diagnostics = mock(CatalogDiagnosticsUseCase.class);
        when(diagnostics.begin(CatalogDiagnostic.Source.KAFKA)).thenThrow(new IllegalStateException("unavailable"));
        var recorder = new CatalogDiagnosticsRecorder(diagnostics);
        assertThat(recorder.begin(CatalogDiagnostic.Source.KAFKA)).isNull();
        recorder.activity(CatalogDiagnostic.Activity.APPLYING);
        recorder.succeed(null);
    }

    @Test
    void categorizesRemoteFailureWithoutPersistingItsMessage() {
        var diagnostics = mock(CatalogDiagnosticsUseCase.class);
        var recorder = new CatalogDiagnosticsRecorder(diagnostics);
        var id = UUID.randomUUID();
        recorder.fail(id, new RedisConnectionFailureException("credentials and connection details"));
        verify(diagnostics).fail(id, CatalogDiagnostic.ErrorCode.REMOTE_UNAVAILABLE);
    }
}
