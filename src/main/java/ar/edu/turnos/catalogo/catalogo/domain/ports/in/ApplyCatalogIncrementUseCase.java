package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;

public interface ApplyCatalogIncrementUseCase {
    void apply(CatalogSnapshot changes);
    void applyAndComplete(CatalogSnapshot changes, CatalogNotification notification);
    void complete(CatalogNotification notification);
}
