package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogSnapshotUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.RebuildCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class RebuildCatalogUseCaseImpl implements RebuildCatalogUseCase {
    private final CatalogSnapshotSource source;
    private final ApplyCatalogSnapshotUseCase apply;

    public RebuildCatalogUseCaseImpl(CatalogSnapshotSource source, ApplyCatalogSnapshotUseCase apply) {
        this.source = source;
        this.apply = apply;
    }

    @Override
    public void rebuild(long minimumVersion, Optional<CatalogNotification> notification) {
        // La descarga no mantiene abierta la transacción de reemplazo.
        var snapshot = source.fetch();
        apply.replace(snapshot, minimumVersion, notification);
    }
}
