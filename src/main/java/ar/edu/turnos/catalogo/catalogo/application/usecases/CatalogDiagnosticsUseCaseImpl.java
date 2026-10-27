package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogDiagnosticStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogKafkaStatusSource;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CatalogDiagnosticsUseCaseImpl implements CatalogDiagnosticsUseCase {
    private final CatalogDiagnosticStore store;
    private final Optional<CatalogKafkaStatusSource> kafka;
    private final Clock clock;
    private final ThreadLocal<UUID> active = new ThreadLocal<>();

    public CatalogDiagnosticsUseCaseImpl(CatalogDiagnosticStore store,
                                         Optional<CatalogKafkaStatusSource> kafka, Clock clock) {
        this.store = store;
        this.kafka = kafka;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID begin(CatalogDiagnostic.Source source) {
        UUID id = UUID.randomUUID();
        store.begin(id, source, clock.instant());
        active.set(id);
        return id;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activity(CatalogDiagnostic.Activity activity) {
        UUID id = active.get();
        if (id != null) store.activity(id, activity);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void observed(long version) {
        UUID id = active.get();
        if (id != null) store.observed(id, version, clock.instant());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(UUID id) {
        try {
            if (id != null) store.succeed(id, clock.instant());
        } finally {
            active.remove();
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID id, CatalogDiagnostic.ErrorCode code) {
        try {
            if (id != null) store.fail(id, code, clock.instant());
        } finally {
            active.remove();
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recoverInterrupted() {
        try {
            store.interruptRunning(clock.instant());
        } finally {
            active.remove();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogDiagnostic status() {
        var saved = store.read();
        return new CatalogDiagnostic(saved.appliedVersion(), saved.remoteVersion(), saved.remoteObservedAt(),
                saved.attemptId(), saved.source(), saved.activity(), saved.outcome(), saved.startedAt(),
                saved.finishedAt(), saved.lastSuccessAt(), saved.lastErrorCode(), saved.lastErrorAt(),
                kafka.map(CatalogKafkaStatusSource::state).orElse(CatalogDiagnostic.KafkaState.DISABLED));
    }
}
