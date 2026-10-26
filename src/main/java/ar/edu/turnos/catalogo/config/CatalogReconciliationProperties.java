package ar.edu.turnos.catalogo.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "catalog.reconciliation")
public record CatalogReconciliationProperties(
        @DefaultValue("60s") Duration interval,
        @DefaultValue("3") @Min(1) @Max(5) int maxAttempts,
        @DefaultValue("1s") Duration retryDelay) {
    public CatalogReconciliationProperties {
        if (interval == null || interval.isNegative() || interval.isZero()
                || retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()
                || retryDelay.compareTo(Duration.ofSeconds(10)) > 0) {
            throw new IllegalArgumentException("Intervalos de reconciliacion invalidos");
        }
    }
}
