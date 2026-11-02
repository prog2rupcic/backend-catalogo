package ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** El diagnóstico es auxiliar: su indisponibilidad no cambia el resultado funcional. */
@Component
public class CatalogDiagnosticsRecorder {
    private static final Logger log = LoggerFactory.getLogger(CatalogDiagnosticsRecorder.class);
    private final CatalogDiagnosticsUseCase diagnostics;

    public CatalogDiagnosticsRecorder(CatalogDiagnosticsUseCase diagnostics) {
        this.diagnostics = diagnostics;
    }

    public UUID begin(CatalogDiagnostic.Source source) {
        return safely(() -> diagnostics.begin(source), null);
    }

    public void activity(CatalogDiagnostic.Activity activity) {
        safely(() -> { diagnostics.activity(activity); return null; }, null);
    }

    public void observed(long version) {
        safely(() -> { diagnostics.observed(version); return null; }, null);
    }

    public void succeed(UUID id) {
        safely(() -> { diagnostics.succeed(id); return null; }, null);
    }

    public void fail(UUID id, RuntimeException error) {
        safely(() -> { diagnostics.fail(id, code(error)); return null; }, null);
    }

    public void recoverInterrupted() {
        safely(() -> { diagnostics.recoverInterrupted(); return null; }, null);
    }

    private <T> T safely(Supplier<T> action, T fallback) {
        try {
            return action.get();
        } catch (RuntimeException error) {
            log.warn("Diagnostico no disponible (tipo {})", error.getClass().getSimpleName());
            return fallback;
        }
    }

    private CatalogDiagnostic.ErrorCode code(RuntimeException error) {
        if (error instanceof ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException) {
            return CatalogDiagnostic.ErrorCode.REBUILD_REQUIRED;
        }
        if (error instanceof org.springframework.web.client.RestClientException
                || error instanceof org.springframework.data.redis.RedisConnectionFailureException) {
            return CatalogDiagnostic.ErrorCode.REMOTE_UNAVAILABLE;
        }
        if (error instanceof org.springframework.dao.DataAccessException
                || error instanceof org.springframework.transaction.TransactionException) {
            return CatalogDiagnostic.ErrorCode.PERSISTENCE_UNAVAILABLE;
        }
        if (error instanceof IllegalArgumentException) {
            return CatalogDiagnostic.ErrorCode.INVALID_INPUT;
        }
        return CatalogDiagnostic.ErrorCode.OTHER;
    }
}
