package ar.edu.turnos.catalogo.catalogo.domain.ports.out;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;

// Contrato para saber si existe versión aplicada y guardar un snapshot.
public interface CatalogSnapshotStore {
    boolean isInitialized();

    void replace(CatalogSnapshot snapshot);
}
