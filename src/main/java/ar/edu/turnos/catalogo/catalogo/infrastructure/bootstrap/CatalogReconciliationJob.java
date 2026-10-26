package ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap;

import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.infrastructure.messaging.CatalogKafkaLifecycle;
import ar.edu.turnos.catalogo.config.CatalogReconciliationProperties;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CatalogReconciliationJob {
    private static final Logger log = LoggerFactory.getLogger(CatalogReconciliationJob.class);
    private final SynchronizeCatalogUseCase sync;
    private final Optional<CatalogKafkaLifecycle> kafka;
    private final CatalogReconciliationProperties properties;
    private final RetryPause pause;
    private final boolean bootstrap;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean ready;

    @Autowired
    public CatalogReconciliationJob(SynchronizeCatalogUseCase sync, Optional<CatalogKafkaLifecycle> kafka,
                                    CatalogReconciliationProperties properties,
                                    @Value("${catalog.bootstrap.enabled:true}") boolean bootstrap) {
        this(sync, kafka, properties, Thread::sleep, bootstrap);
    }

    CatalogReconciliationJob(SynchronizeCatalogUseCase sync, Optional<CatalogKafkaLifecycle> kafka,
                             CatalogReconciliationProperties properties, RetryPause pause, boolean bootstrap) {
        this.sync = sync;
        this.kafka = kafka;
        this.properties = properties;
        this.pause = pause;
        this.bootstrap = bootstrap;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        ready = true;
        if (bootstrap) {
            runCycle();
        }
    }

    @Scheduled(fixedDelayString = "${catalog.reconciliation.interval:60s}",
            initialDelayString = "${catalog.reconciliation.interval:60s}")
    public void periodically() {
        if (ready) {
            runCycle();
        }
    }

    public void runCycle() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
                try {
                    sync.reconcile();
                    kafka.ifPresent(CatalogKafkaLifecycle::resumeAfterReconciliation);
                    log.info("Reconciliacion del catalogo completada (intento {})", attempt);
                    return;
                } catch (RuntimeException error) {
                    log.warn("Reconciliacion fallida (intento {}/{}; tipo {})", attempt,
                            properties.maxAttempts(), error.getClass().getSimpleName());
                    if (!CatalogFailureClassifier.isRetryable(error) || attempt == properties.maxAttempts()) {
                        return;
                    }
                }
                // Fuera del monitor del caso de uso y de cualquier transacción.
                try {
                    pause.sleep(properties.retryDelay().multipliedBy(1L << (attempt - 1)));
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        } finally {
            running.set(false);
        }
    }

    @FunctionalInterface
    interface RetryPause {
        void sleep(Duration duration) throws InterruptedException;
    }
}
