package ar.edu.turnos.catalogo.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogClockConfiguration {
    @Bean
    Clock catalogClock() {
        return Clock.systemUTC();
    }
}
