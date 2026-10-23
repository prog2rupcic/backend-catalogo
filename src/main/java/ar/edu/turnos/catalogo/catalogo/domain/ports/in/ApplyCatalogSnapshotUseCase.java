package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;

/** Aplica el snapshot inicial solamente si no existe una versión local. */
public interface ApplyCatalogSnapshotUseCase {
    void applyIfEmpty(CatalogSnapshot snapshot);
}
