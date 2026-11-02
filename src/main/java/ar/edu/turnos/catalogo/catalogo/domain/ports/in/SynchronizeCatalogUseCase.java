package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;

public interface SynchronizeCatalogUseCase {
    void synchronize(CatalogNotification notification);
    void reconcile();
}
