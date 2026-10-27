package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogSnapshotUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.RebuildCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RebuildCatalogUseCaseImpl implements RebuildCatalogUseCase {
    private final CatalogSnapshotSource source;
    private final ApplyCatalogSnapshotUseCase apply;
    private final CatalogDiagnosticsUseCase diagnostics;

    public RebuildCatalogUseCaseImpl(CatalogSnapshotSource source, ApplyCatalogSnapshotUseCase apply,
                                     CatalogDiagnosticsUseCase diagnostics) {
        this.source = source;
        this.apply = apply;
        this.diagnostics = diagnostics;
    }

    @Override
    public void rebuild(long minimumVersion, Optional<CatalogNotification> notification) {
        // La descarga no mantiene abierta la transacción de reemplazo.
        var snapshot = source.fetch();
        try {
            diagnostics.observed(snapshot.version());
        } catch (RuntimeException ignored) {
            // No convertir una reconstrucción exitosa en fallo de diagnóstico.
        }
        apply.replace(snapshot, minimumVersion, notification);
    }
}
