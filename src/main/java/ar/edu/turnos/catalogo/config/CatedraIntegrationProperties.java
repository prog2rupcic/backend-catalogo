package ar.edu.turnos.catalogo.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuración técnica compartida por los adaptadores del catálogo. */
@Validated
@ConfigurationProperties(prefix = "catalog.catedra")
public final class CatedraIntegrationProperties {
    @NotNull
    private final URI baseUrl;
    @NotBlank
    private final String jwt;
    @NotBlank
    private final String kafkaCatalogTopic;

    public CatedraIntegrationProperties(URI baseUrl, String jwt, String kafkaCatalogTopic) {
        this.baseUrl = baseUrl;
        this.jwt = jwt;
        this.kafkaCatalogTopic = kafkaCatalogTopic;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }

    public String getJwt() {
        return jwt;
    }

    public String getKafkaCatalogTopic() {
        return kafkaCatalogTopic;
    }
}
