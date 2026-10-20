package ar.edu.turnos.catalogo.catalogo.infrastructure.redis;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Comprueba una lectura contractual sin exigir permisos administrativos Redis. */
@Component("catalogRedisHealthIndicator")
public class CatalogRedisHealthIndicator implements HealthIndicator {
    private static final String CURRENT_VERSION_KEY = "catedra:sync:current-version";
    private final StringRedisTemplate redis;

    public CatalogRedisHealthIndicator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Health health() {
        try {
            String value = redis.opsForValue().get(CURRENT_VERSION_KEY);
            if (value == null) {
                return Health.down().withDetail("reason", "CURRENT_VERSION_MISSING").build();
            }
            if (!value.matches("[0-9]+")) {
                return invalidVersion();
            }
            long version = Long.parseLong(value);
            return Health.up().withDetail("currentVersion", version).build();
        } catch (NumberFormatException e) {
            return invalidVersion();
        } catch (RuntimeException e) {
            // No publicar excepciones que puedan incluir datos de conexión o credenciales.
            return Health.down().withDetail("reason", "REDIS_READ_FAILED").build();
        }
    }

    private Health invalidVersion() {
        return Health.down().withDetail("reason", "CURRENT_VERSION_INVALID").build();
    }
}
