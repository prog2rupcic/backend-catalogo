package ar.edu.turnos.catalogo.catalogo.infrastructure.messaging;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap.CatalogDiagnosticsRecorder;
import ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap.CatalogSynchronizationMonitor;
import java.util.UUID;
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
    private final CatalogDiagnosticsRecorder diagnostics;
    private final CatalogSynchronizationMonitor monitor;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public CatalogUpdatedKafkaListener(SynchronizeCatalogUseCase sync, CatalogKafkaLifecycle lifecycle,
                                       CatalogDiagnosticsRecorder diagnostics, CatalogSynchronizationMonitor monitor) {
        this.sync = sync;
        this.lifecycle = lifecycle;
        this.diagnostics = diagnostics;
        this.monitor = monitor;
    }

    @KafkaListener(id = "catalog-updates", idIsGroup = false,
            topics = "${catalog.catedra.kafka-catalog-topic}", autoStartup = "false")
    public void receive(String payload, Acknowledgment acknowledgment) {
        synchronized (monitor) {
            UUID attemptId = diagnostics.begin(CatalogDiagnostic.Source.KAFKA);
            try {
                CatalogUpdated event = json.readValue(payload, CatalogUpdated.class);
                if (event == null || !"CatalogUpdated".equals(event.eventType())
                        || event.schemaVersion() == null || event.schemaVersion() != 1 || event.newVersion() == null) {
                    throw new IllegalArgumentException("Evento CatalogUpdated invalido");
                }
                sync.synchronize(new CatalogNotification(event.eventId(), event.newVersion()));
                diagnostics.succeed(attemptId);
                acknowledgment.acknowledge();
            } catch (RuntimeException error) {
                diagnostics.fail(attemptId, error);
                lifecycle.recordFailure(error);
                throw error;
            }
        }
    }

    record CatalogUpdated(String eventId, String eventType, Long newVersion, Integer schemaVersion) { }
}
