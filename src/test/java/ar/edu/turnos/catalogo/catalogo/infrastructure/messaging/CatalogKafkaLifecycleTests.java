package ar.edu.turnos.catalogo.catalogo.infrastructure.messaging;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic.KafkaState;
import java.util.List;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogKafkaLifecycleTests {
    final KafkaListenerEndpointRegistry registry = mock(KafkaListenerEndpointRegistry.class);
    final MessageListenerContainer container = mock(MessageListenerContainer.class);

    @Test
    void resumesAfterTransientFailureButDoesNotRestartRunningContainer() {
        when(registry.getListenerContainer("catalog-updates")).thenReturn(container);
        var lifecycle = new CatalogKafkaLifecycle(registry);
        assertThat(lifecycle.state()).isEqualTo(KafkaState.WAITING);
        lifecycle.recordFailure(new TransientDataAccessResourceException("offline"));
        assertThat(lifecycle.state()).isEqualTo(KafkaState.STOPPED_RETRYABLE);
        lifecycle.resumeAfterReconciliation();
        verify(container).start();
        when(container.isRunning()).thenReturn(true);
        assertThat(lifecycle.state()).isEqualTo(KafkaState.WAITING);
        when(container.getAssignedPartitions()).thenReturn(List.of(new TopicPartition("catedra.catalog.test", 0)));
        assertThat(lifecycle.state()).isEqualTo(KafkaState.CONSUMING);
        lifecycle.resumeAfterReconciliation();
        verify(container, times(1)).start();
    }

    @Test
    void invalidEventDoesNotCauseAnAutomaticRestartLoop() {
        when(registry.getListenerContainer("catalog-updates")).thenReturn(container);
        var lifecycle = new CatalogKafkaLifecycle(registry);
        lifecycle.recordFailure(new IllegalArgumentException("invalid event"));
        assertThat(lifecycle.state()).isEqualTo(KafkaState.STOPPED_INVALID);
        lifecycle.resumeAfterReconciliation();
        lifecycle.resumeAfterReconciliation();
        verify(container, never()).start();
    }
}
