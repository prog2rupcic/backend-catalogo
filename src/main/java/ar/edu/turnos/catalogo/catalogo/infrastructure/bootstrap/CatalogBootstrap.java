package ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap;

import ar.edu.turnos.catalogo.catalogo.domain.ports.in.InitializeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


// Conecta el arranque de Spring con el caso de uso y define el indicador de inicialización.
// Si falla la carga, registra el fallo sin marcar el catálogo como inicializado.
@Configuration
public class CatalogBootstrap {
    private static final Logger log = LoggerFactory.getLogger(CatalogBootstrap.class);

    @Bean
    @ConditionalOnProperty(name = "catalog.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner initializeCatalog(InitializeCatalogUseCase initialize) {
        return args -> {
            try {
                initialize.initializeIfEmpty();
            } catch (RuntimeException e) {
                // A failed initial fetch must not mark the catalog as initialized.
                log.error("No se pudo inicializar el catalogo; permanece sin version aplicada ({})",
                        e.getClass().getSimpleName());
            }
        };
    }

    @Bean
    HealthIndicator catalogInitializationHealth(CatalogSnapshotStore store) {
        return () -> store.isInitialized()
                ? Health.up().withDetail("catalog", "INITIALIZED").build()
                : Health.down().withDetail("catalog", "NOT_INITIALIZED").build();
    }
}
