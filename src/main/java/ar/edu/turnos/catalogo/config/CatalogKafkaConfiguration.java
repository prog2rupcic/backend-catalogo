package ar.edu.turnos.catalogo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;

@Configuration
public class CatalogKafkaConfiguration {
    /** No confirmar registros fallidos: el reconciliador reanuda solo fallos recuperables. */
    @Bean
    CommonContainerStoppingErrorHandler catalogKafkaErrorHandler() {
        return new CommonContainerStoppingErrorHandler();
    }
}
