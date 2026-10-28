package ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap;

import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


// La reconciliación de arranque y periódica vive en CatalogReconciliationJob.
@Configuration
public class CatalogBootstrap {
    @Bean
    HealthIndicator catalogInitializationHealth(CatalogSnapshotStore store) {
        return () -> store.isInitialized()
                ? Health.up().withDetail("catalog", "INITIALIZED").build()
                : Health.down().withDetail("catalog", "NOT_INITIALIZED").build();
    }
}
