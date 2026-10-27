package ar.edu.turnos.catalogo.catalogo.infrastructure.messaging;

import ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap.CatalogFailureClassifier;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic.KafkaState;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogKafkaStatusSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "catalog.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogKafkaLifecycle implements CatalogKafkaStatusSource {
    public static final String LISTENER_ID = "catalog-updates";
    private final KafkaListenerEndpointRegistry registry;
    private volatile boolean permanentFailure;
    private volatile boolean retryableFailure;

    public CatalogKafkaLifecycle(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public void recordFailure(RuntimeException error) {
        if (CatalogFailureClassifier.isRetryable(error)) {
            retryableFailure = true;
        } else {
            permanentFailure = true;
        }
    }

    public void resumeAfterReconciliation() {
        var container = registry.getListenerContainer(LISTENER_ID);
        if (!permanentFailure && container != null && !container.isRunning()) {
            container.start();
            retryableFailure = false;
        }
    }

    @Override
    public KafkaState state() {
        if (permanentFailure) return KafkaState.STOPPED_INVALID;
        var container = registry.getListenerContainer(LISTENER_ID);
        if (container == null || !container.isRunning()) {
            return retryableFailure ? KafkaState.STOPPED_RETRYABLE : KafkaState.WAITING;
        }
        var partitions = container.getAssignedPartitions();
        return partitions != null && !partitions.isEmpty() ? KafkaState.CONSUMING : KafkaState.WAITING;
    }
}
