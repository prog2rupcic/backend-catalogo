package ar.edu.turnos.catalogo.catalogo.infrastructure.messaging;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.Acknowledgment;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CatalogUpdatedKafkaListenerTests {
    final SynchronizeCatalogUseCase sync = mock(SynchronizeCatalogUseCase.class);
    final Acknowledgment ack = mock(Acknowledgment.class);
    final CatalogUpdatedKafkaListener listener = new CatalogUpdatedKafkaListener(sync,
            mock(CatalogSnapshotStore.class), mock(KafkaListenerEndpointRegistry.class));
    final String payload = """
            {"eventId":"e4","eventType":"CatalogUpdated","newVersion":4,"schemaVersion":1,"extra":"compatible"}
            """;

    @Test
    void acknowledgesOnlyAfterUseCaseReturnsSuccessfully() {
        listener.receive(payload, ack);
        var order = inOrder(sync, ack);
        order.verify(sync).synchronize(new CatalogNotification("e4", 4));
        order.verify(ack).acknowledge();
    }

    @Test
    void doesNotAcknowledgeFailedOrInvalidEvents() {
        doThrow(new IllegalStateException()).when(sync).synchronize(any());
        assertThatThrownBy(() -> listener.receive(payload, ack)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> listener.receive(payload.replace("\"schemaVersion\":1", "\"schemaVersion\":2"), ack))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(ack);
    }
}
