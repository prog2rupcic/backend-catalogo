package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import java.util.Optional;

/** Escritura atómica del snapshot inicial o de reconstrucción. */
public interface ApplyCatalogSnapshotUseCase {
    void applyIfEmpty(CatalogSnapshot snapshot);
    void replace(CatalogSnapshot snapshot, long minimumVersion, Optional<CatalogNotification> notification);
}
