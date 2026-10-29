package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
import ar.edu.turnos.catalogo.catalogo.infrastructure.web.controller.CatalogDiagnosticsController;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"catalog.bootstrap.enabled=false", "catalog.catedra.base-url=http://localhost:9999",
        "catalog.catedra.jwt=test-only"})
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-integration.properties")
@Testcontainers
class CatalogDiagnosticsHttpTests {
    private static final String PATH = "/api/catalog/synchronization/status";
    private static final String SECRET = "test-only-shared-hs256-secret-32-bytes-long";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine")
            .withStartupTimeout(Duration.ofMinutes(2));

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogDiagnosticsUseCase diagnostics;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM catalog_sync_state");
        jdbc.update("UPDATE catalog_diagnostics SET attempt_id=NULL, source=NULL, activity='IDLE', "
                + "outcome='UNINITIALIZED', started_at=NULL, finished_at=NULL, last_success_at=NULL, "
                + "remote_version=NULL, remote_observed_at=NULL, last_error_code=NULL, last_error_at=NULL WHERE id=1");
    }

    @Test
    void restrictsFullDiagnosticsToAdminJwt() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("backend-catalogo", "ROLE_USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("another-service", "ROLE_ADMIN")))
                .andExpect(status().isUnauthorized());

        var result = mvc.perform(get(PATH).header("Authorization", "Bearer " + token("backend-catalogo", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedVersion", nullValue()))
                .andExpect(jsonPath("$.remoteVersion", nullValue()))
                .andExpect(jsonPath("$.outcome").value("UNINITIALIZED"))
                .andExpect(jsonPath("$.activity").value("IDLE"))
                .andExpect(jsonPath("$.kafka").value("DISABLED"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(SECRET, "test-only");
    }

    @Test
    void exposesAppliedVersionAndLastObservedRemoteVersionWithoutLiveFetch() throws Exception {
        var attempt = diagnostics.begin(CatalogDiagnostic.Source.STARTUP);
        diagnostics.observed(9);
        jdbc.update("INSERT INTO catalog_sync_state (id, applied_version, applied_at) VALUES (1, 9, ?)",
                Timestamp.from(Instant.now()));
        diagnostics.succeed(attempt);

        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("backend-catalogo", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedVersion").value(9))
                .andExpect(jsonPath("$.remoteVersion").value(9))
                .andExpect(jsonPath("$.remoteObservedAt", notNullValue()))
                .andExpect(jsonPath("$.attemptId").value(attempt.toString()))
                .andExpect(jsonPath("$.source").value("STARTUP"))
                .andExpect(jsonPath("$.outcome").value("CURRENT"))
                .andExpect(jsonPath("$.startedAt", notNullValue()))
                .andExpect(jsonPath("$.finishedAt", notNullValue()))
                .andExpect(jsonPath("$.lastSuccessAt", notNullValue()))
                .andExpect(jsonPath("$.lastErrorCode", nullValue()));

        var failed = diagnostics.begin(CatalogDiagnostic.Source.PERIODIC);
        diagnostics.fail(failed, CatalogDiagnostic.ErrorCode.REMOTE_UNAVAILABLE);
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("backend-catalogo", "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appliedVersion").value(9))
                .andExpect(jsonPath("$.remoteVersion").value(9))
                .andExpect(jsonPath("$.source").value("PERIODIC"))
                .andExpect(jsonPath("$.outcome").value("ERROR"))
                .andExpect(jsonPath("$.lastSuccessAt", notNullValue()))
                .andExpect(jsonPath("$.lastErrorCode").value("REMOTE_UNAVAILABLE"))
                .andExpect(jsonPath("$.lastErrorAt", notNullValue()));
    }

    @Test
    void databaseFailureReturnsOnlyServiceUnavailable() throws Exception {
        var useCase = mock(CatalogDiagnosticsUseCase.class);
        when(useCase.status()).thenThrow(new DataAccessResourceFailureException("secret database connection detail"));
        var isolated = MockMvcBuilders.standaloneSetup(new CatalogDiagnosticsController(useCase)).build();
        isolated.perform(get(PATH)).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(""));
    }

    private String token(String audience, String authority) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        var claims = JwtClaimsSet.builder().issuer("backend-turnos").audience(List.of(audience))
                .subject("user-42").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("authorities", List.of(authority)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
