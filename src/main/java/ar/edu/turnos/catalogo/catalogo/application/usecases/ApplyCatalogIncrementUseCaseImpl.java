package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogValidation;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogIncrementUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogIncrementStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ApplyCatalogIncrementUseCaseImpl implements ApplyCatalogIncrementUseCase {
    private final CatalogIncrementStore store;

    public ApplyCatalogIncrementUseCaseImpl(CatalogIncrementStore store) {
        this.store = store;
    }

    @Override
    @Transactional
    public void apply(CatalogSnapshot changes) {
        applyChanges(changes);
    }

    @Override
    @Transactional
    public void applyAndComplete(CatalogSnapshot changes, CatalogNotification notification) {
        applyChanges(changes);
        complete(notification);
    }

    private void applyChanges(CatalogSnapshot changes) {
        CatalogValidation.validate(changes, false);
        long local = store.lockAppliedVersion();
        if (changes.version() <= local) {
            return;
        }
        if (changes.version() - local != 1) {
            throw new CatalogRebuildRequiredException("Incremento fuera de secuencia");
        }
        store.upsert(changes);
    }

    @Override
    @Transactional
    public void complete(CatalogNotification notification) {
        if (store.lockAppliedVersion() < notification.newVersion()) {
            throw new CatalogRebuildRequiredException("Notificacion aun no aplicada");
        }
        store.markProcessed(notification.eventId());
    }
}
