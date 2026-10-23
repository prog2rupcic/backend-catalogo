package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogValidation;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogSnapshotUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


// Vuelve a comprobar el estado y aplica el snapshot bajo @Transactional.
// Está separado para que Spring ejecute esa llamada a través de su mecanismo transaccional. Si una escritura falla, se revierte todo.
@Component
public class ApplyCatalogSnapshotUseCaseImpl implements ApplyCatalogSnapshotUseCase {
    private final CatalogSnapshotStore store;

    public ApplyCatalogSnapshotUseCaseImpl(CatalogSnapshotStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void applyIfEmpty(CatalogSnapshot snapshot) {
        if (!store.isInitialized()) {
            CatalogValidation.validate(snapshot, true);
            store.replace(snapshot);
        }
    }
}
