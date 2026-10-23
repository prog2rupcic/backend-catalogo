package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.ports.in.InitializeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogSnapshotUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.springframework.stereotype.Component;


// Comprueba si ya hay catálogo y, si no, solicita el snapshot.
// Hace la llamada de red antes de abrir la transacción de escritura.
@Component
public class InitializeCatalogUseCaseImpl implements InitializeCatalogUseCase {
    private final CatalogSnapshotSource source;
    private final CatalogSnapshotStore store;
    private final ApplyCatalogSnapshotUseCase apply;

    public InitializeCatalogUseCaseImpl(CatalogSnapshotSource source, CatalogSnapshotStore store, ApplyCatalogSnapshotUseCase apply) {
        this.source = source;
        this.store = store;
        this.apply = apply;
    }

    @Override
    public void initializeIfEmpty() {
        if (!store.isInitialized()) {
            apply.applyIfEmpty(source.fetch());
        }
    }
}
