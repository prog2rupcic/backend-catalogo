package ar.edu.turnos.catalogo.catalogo.domain.ports.out;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;

// Contrato para obtener el snapshot.
public interface CatalogSnapshotSource {
    CatalogSnapshot fetch();
}
