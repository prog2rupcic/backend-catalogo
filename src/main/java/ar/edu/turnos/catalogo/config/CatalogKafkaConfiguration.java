package ar.edu.turnos.catalogo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonContainerStoppingErrorHandler;

@Configuration
public class CatalogKafkaConfiguration {
    /** No descartar ni confirmar un registro fallido: detener y permitir replay al reiniciar. */
    @Bean
    CommonContainerStoppingErrorHandler catalogKafkaErrorHandler() {
        return new CommonContainerStoppingErrorHandler();
    }
}
