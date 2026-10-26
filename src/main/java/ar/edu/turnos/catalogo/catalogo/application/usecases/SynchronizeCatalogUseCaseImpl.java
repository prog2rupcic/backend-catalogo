package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import java.util.ArrayList;
import java.util.Optional;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogIncrementUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.InitializeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.RebuildCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogChangesSource;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogIncrementStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.springframework.stereotype.Component;

@Component
public class SynchronizeCatalogUseCaseImpl implements SynchronizeCatalogUseCase {
    private final CatalogChangesSource source;
    private final CatalogIncrementStore store;
    private final ApplyCatalogIncrementUseCase apply;
    private final CatalogSnapshotStore snapshots;
    private final InitializeCatalogUseCase initialize;
    private final RebuildCatalogUseCase rebuild;

    public SynchronizeCatalogUseCaseImpl(CatalogChangesSource source, CatalogIncrementStore store,
                                         ApplyCatalogIncrementUseCase apply, CatalogSnapshotStore snapshots,
                                         InitializeCatalogUseCase initialize, RebuildCatalogUseCase rebuild) {
        this.source = source;
        this.store = store;
        this.apply = apply;
        this.snapshots = snapshots;
        this.initialize = initialize;
        this.rebuild = rebuild;
    }

    @Override
    public synchronized void synchronize(CatalogNotification notification) {
        if (store.processed(notification.eventId())) {
            return;
        }
        if (!snapshots.isInitialized()) {
            rebuild.rebuild(notification.newVersion(), Optional.of(notification));
        } else {
            long local = store.appliedVersion();
            if (local >= notification.newVersion()) {
                apply.complete(notification);
            } else {
                reconcileInternal(Optional.of(notification), local);
            }
        }
    }

    @Override
    public synchronized void reconcile() {
        // Este mismo monitor coordina arranque, scheduler y Kafka en una instancia.
        if (!snapshots.isInitialized()) {
            initialize.initializeIfEmpty();
        }
        reconcileInternal(Optional.empty(), store.appliedVersion());
    }

    private void reconcileInternal(Optional<CatalogNotification> notification, long local) {
        long minimumVersion = local;
        if (notification.isPresent()) {
            minimumVersion = Math.max(minimumVersion, notification.get().newVersion());
        }
        try {
            var history = source.history();
            minimumVersion = Math.max(minimumVersion, history.currentVersion());
            history.requireContinuousHistory(local, minimumVersion);
            if (local == history.currentVersion()) {
                notification.ifPresent(apply::complete);
                return;
            }
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
            if (notification.isPresent()) {
                apply.applyAndComplete(pending.getLast(), notification.get());
            } else {
                apply.apply(pending.getLast());
            }
        } catch (CatalogRebuildRequiredException e) {
            // Una sola descarga por intento. Un snapshot obsoleto se propaga al
            // ciclo de reintentos, nunca se convierte en una recursión ilimitada.
            rebuild.rebuild(minimumVersion, notification);
        }
    }
}
