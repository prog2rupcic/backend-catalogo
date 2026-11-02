package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.ports.in.InitializeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
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
    private final CatalogDiagnosticsUseCase diagnostics;

    public InitializeCatalogUseCaseImpl(CatalogSnapshotSource source, CatalogSnapshotStore store,
                                        ApplyCatalogSnapshotUseCase apply, CatalogDiagnosticsUseCase diagnostics) {
        this.source = source;
        this.store = store;
        this.apply = apply;
        this.diagnostics = diagnostics;
    }

    @Override
    public void initializeIfEmpty() {
        if (!store.isInitialized()) {
            var snapshot = source.fetch();
            try {
                diagnostics.observed(snapshot.version());
            } catch (RuntimeException ignored) {
                // Observabilidad no afecta la inicialización.
            }
            apply.applyIfEmpty(snapshot);
        }
    }
}
