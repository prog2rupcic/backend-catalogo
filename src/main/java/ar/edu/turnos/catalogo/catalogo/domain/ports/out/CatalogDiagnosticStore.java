package ar.edu.turnos.catalogo.catalogo.domain.ports.out;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import java.time.Instant;
import java.util.UUID;

public interface CatalogDiagnosticStore {
    void begin(UUID id, CatalogDiagnostic.Source source, Instant at);
    void activity(UUID id, CatalogDiagnostic.Activity activity);
    void observed(UUID id, long remoteVersion, Instant at);
    void succeed(UUID id, Instant at);
    void fail(UUID id, CatalogDiagnostic.ErrorCode code, Instant at);
    void interruptRunning(Instant at);
    CatalogDiagnostic read();
}
