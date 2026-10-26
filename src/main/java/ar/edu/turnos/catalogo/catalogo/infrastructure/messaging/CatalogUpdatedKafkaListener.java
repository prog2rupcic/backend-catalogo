package ar.edu.turnos.catalogo.catalogo.infrastructure.messaging;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "catalog.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogUpdatedKafkaListener {
    private final SynchronizeCatalogUseCase sync;
    private final CatalogKafkaLifecycle lifecycle;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public CatalogUpdatedKafkaListener(SynchronizeCatalogUseCase sync, CatalogKafkaLifecycle lifecycle) {
        this.sync = sync;
        this.lifecycle = lifecycle;
    }

    @KafkaListener(id = "catalog-updates", idIsGroup = false,
            topics = "${catalog.catedra.kafka-catalog-topic}", autoStartup = "false")
    public void receive(String payload, Acknowledgment acknowledgment) {
        try {
            CatalogUpdated event = json.readValue(payload, CatalogUpdated.class);
            if (event == null || !"CatalogUpdated".equals(event.eventType())
                    || event.schemaVersion() == null || event.schemaVersion() != 1 || event.newVersion() == null) {
                throw new IllegalArgumentException("Evento CatalogUpdated invalido");
            }
            sync.synchronize(new CatalogNotification(event.eventId(), event.newVersion()));
            acknowledgment.acknowledge();
        } catch (RuntimeException error) {
            lifecycle.recordFailure(error);
            throw error;
        }
    }

    record CatalogUpdated(String eventId, String eventType, Long newVersion, Integer schemaVersion) { }
}
