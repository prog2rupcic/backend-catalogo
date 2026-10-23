package ar.edu.turnos.catalogo.catalogo.infrastructure.messaging;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "catalog.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogUpdatedKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(CatalogUpdatedKafkaListener.class);
    private final SynchronizeCatalogUseCase sync;
    private final CatalogSnapshotStore store;
    private final KafkaListenerEndpointRegistry registry;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public CatalogUpdatedKafkaListener(SynchronizeCatalogUseCase sync, CatalogSnapshotStore store,
                                       KafkaListenerEndpointRegistry registry) {
        this.sync = sync;
        this.store = store;
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startAfterInitialization() {
        if (store.isInitialized()) {
            registry.getListenerContainer("catalog-updates").start();
        } else {
            log.warn("Consumidor de catalogo detenido: falta snapshot inicial");
        }
    }

    @KafkaListener(id = "catalog-updates", idIsGroup = false,
            topics = "${catalog.catedra.kafka-catalog-topic}", autoStartup = "false")
    public void receive(String payload, Acknowledgment acknowledgment) {
        CatalogUpdated event = json.readValue(payload, CatalogUpdated.class);
        if (event == null || !"CatalogUpdated".equals(event.eventType())
                || event.schemaVersion() == null || event.schemaVersion() != 1 || event.newVersion() == null) {
            throw new IllegalArgumentException("Evento CatalogUpdated invalido");
        }
        sync.synchronize(new CatalogNotification(event.eventId(), event.newVersion()));
        acknowledgment.acknowledge();
    }

    record CatalogUpdated(String eventId, String eventType, Long newVersion, Integer schemaVersion) { }
}
