package ar.edu.turnos.catalogo.catalogo.infrastructure.messaging;

import ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap.CatalogFailureClassifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogKafkaLifecycle {
    public static final String LISTENER_ID = "catalog-updates";
    private final KafkaListenerEndpointRegistry registry;
    private volatile boolean permanentFailure;

    public CatalogKafkaLifecycle(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public void recordFailure(RuntimeException error) {
        if (!CatalogFailureClassifier.isRetryable(error)) {
            permanentFailure = true;
        }
    }

    public void resumeAfterReconciliation() {
        var container = registry.getListenerContainer(LISTENER_ID);
        if (!permanentFailure && container != null && !container.isRunning()) {
            container.start();
        }
    }
}
