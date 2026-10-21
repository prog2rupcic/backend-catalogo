package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import java.util.ArrayList;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogIncrementUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogChangesSource;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogIncrementStore;
import org.springframework.stereotype.Component;

@Component
public class SynchronizeCatalogUseCaseImpl implements SynchronizeCatalogUseCase {
    private final CatalogChangesSource source;
    private final CatalogIncrementStore store;
    private final ApplyCatalogIncrementUseCase apply;

    public SynchronizeCatalogUseCaseImpl(CatalogChangesSource source, CatalogIncrementStore store,
                                         ApplyCatalogIncrementUseCase apply) {
        this.source = source;
        this.store = store;
        this.apply = apply;
    }

    @Override
    public synchronized void synchronize(CatalogNotification notification) {
        if (store.processed(notification.eventId())) {
            return;
        }
        long local = store.appliedVersion();
        if (local < notification.newVersion()) {
            var history = source.history();
            history.requireContinuousHistory(local, notification.newVersion());
            // Tomar los datos antes de persistir; los hashes contienen estado vigente.
            // Si cambió la publicación durante las lecturas, no aplicar una mezcla.
            var pending = new ArrayList<CatalogSnapshot>();
            for (long version = local; version < history.currentVersion();) {
                pending.add(source.changes(++version));
            }
            if (!history.equals(source.history())) {
                throw new CatalogRebuildRequiredException("Catalogo remoto cambio durante la lectura");
            }
            for (int i = 0; i < pending.size() - 1; i++) {
                apply.apply(pending.get(i));
            }
            apply.applyAndComplete(pending.getLast(), notification);
            return;
        }
        // Un evento atrasado no cambia entidades, pero se registra duraderamente.
        apply.complete(notification);
    }
}
