package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import java.util.UUID;

public interface CatalogDiagnosticsUseCase {
    UUID begin(CatalogDiagnostic.Source source);
    void activity(CatalogDiagnostic.Activity activity);
    void observed(long version);
    void succeed(UUID id);
    void fail(UUID id, CatalogDiagnostic.ErrorCode code);
    void recoverInterrupted();
    CatalogDiagnostic status();
}
