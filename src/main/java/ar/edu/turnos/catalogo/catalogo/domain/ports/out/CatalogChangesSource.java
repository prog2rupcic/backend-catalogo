package ar.edu.turnos.catalogo.catalogo.domain.ports.out;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogHistory;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;

public interface CatalogChangesSource {
    CatalogHistory history();
    CatalogSnapshot changes(long version);
}
