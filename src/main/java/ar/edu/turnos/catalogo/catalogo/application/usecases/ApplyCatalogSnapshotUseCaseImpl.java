package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogValidation;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogSnapshotUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogIncrementStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


// Vuelve a comprobar el estado y aplica el snapshot bajo @Transactional.
// Está separado para que Spring ejecute esa llamada a través de su mecanismo transaccional. Si una escritura falla, se revierte todo.
@Component
public class ApplyCatalogSnapshotUseCaseImpl implements ApplyCatalogSnapshotUseCase {
    private final CatalogSnapshotStore store;
    private final CatalogIncrementStore increments;

    public ApplyCatalogSnapshotUseCaseImpl(CatalogSnapshotStore store, CatalogIncrementStore increments) {
        this.store = store;
        this.increments = increments;
    }

    @Override
    @Transactional
    public void applyIfEmpty(CatalogSnapshot snapshot) {
        if (!store.isInitialized()) {
            CatalogValidation.validate(snapshot, true);
            store.replace(snapshot);
        }
    }

    @Override
    @Transactional
    public void replace(CatalogSnapshot snapshot, long minimumVersion, Optional<CatalogNotification> notification) {
        CatalogValidation.validate(snapshot, true);
        long required = minimumVersion;
        if (store.isInitialized()) {
            required = Math.max(required, increments.lockAppliedVersion());
        }
        if (notification.isPresent()) {
            required = Math.max(required, notification.get().newVersion());
        }
        if (snapshot.version() < required) {
            throw new CatalogRebuildRequiredException("Snapshot anterior a la version requerida");
        }
        store.replace(snapshot);
        notification.ifPresent(event -> increments.markProcessed(event.eventId()));
    }
}
