package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


// Vuelve a comprobar el estado y aplica el snapshot bajo @Transactional.
// Está separado para que Spring ejecute esa llamada a través de su mecanismo transaccional. Si una escritura falla, se revierte todo.
@Component
public class ApplyCatalogSnapshotUseCase {
    private final CatalogSnapshotStore store;

    public ApplyCatalogSnapshotUseCase(CatalogSnapshotStore store) {
        this.store = store;
    }

    @Transactional
    public void applyIfEmpty(CatalogSnapshot snapshot) {
        if (!store.isInitialized()) {
            store.replace(snapshot);
        }
    }
}
