package ar.edu.turnos.catalogo.catalogo.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import static org.mockito.Mockito.*;

class CatalogKafkaLifecycleTests {
    final KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
    final MessageListenerContainer container = mock(MessageListenerContainer.class);

    @Test
    void resumesAfterTransientFailureButDoesNotRestartRunningContainer() {
        when(registry.getListenerContainer("catalog-updates")).thenReturn(container);
        var lifecycle = new CatalogKafkaLifecycle(registry);
        lifecycle.recordFailure(new TransientDataAccessResourceException("offline"));
        lifecycle.resumeAfterReconciliation();
        verify(container).start();
        when(container.isRunning()).thenReturn(true);
        lifecycle.resumeAfterReconciliation();
        verify(container, times(1)).start();
    }

    @Test
    void invalidEventDoesNotCauseAnAutomaticRestartLoop() {
        when(registry.getListenerContainer("catalog-updates")).thenReturn(container);
        var lifecycle = new CatalogKafkaLifecycle(registry);
        lifecycle.recordFailure(new IllegalArgumentException("invalid event"));
        lifecycle.resumeAfterReconciliation();
        lifecycle.resumeAfterReconciliation();
        verify(container, never()).start();
    }
}
