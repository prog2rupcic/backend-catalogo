package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogSnapshotUseCase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"catalog.bootstrap.enabled=false", "catalog.catedra.base-url=http://localhost:9999",
        "catalog.catedra.jwt=test-only"})
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-integration.properties")
@Testcontainers
class ProfessionalSearchTests {
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
    @Autowired ApplyCatalogSnapshotUseCase snapshots;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");
        jdbc.update("DELETE FROM catalog_sync_state");
    }

    @Test
    void requiresValidUserJwtAndKeepsHealthPublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mvc.perform(get("/api/professionals")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/professionals").header("Authorization", "Bearer " + token("wrong-secret-32-bytes-long-xxxxxxxxxx", "backend-turnos", "backend-catalogo", List.of("ROLE_USER"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(authorized(token(SECRET, "another-issuer", "backend-catalogo", List.of("ROLE_USER"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(authorized(token(SECRET, "backend-turnos", "another-service", List.of("ROLE_USER"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(authorized(token(SECRET, "backend-turnos", "backend-catalogo", List.of("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
        mvc.perform(authorized(token(SECRET, "backend-turnos", "backend-catalogo", List.of("ROLE_USER"))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void searchesLocallyWithCombinedFiltersAndStablePagination() throws Exception {
        snapshots.applyIfEmpty(fixture());
        String jwt = token(SECRET, "backend-turnos", "backend-catalogo", List.of("ROLE_USER"));

        mvc.perform(authorized(jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(12))
                .andExpect(jsonPath("$.content[1].id").value(11))
                .andExpect(jsonPath("$.content[2].id").value(10));
        mvc.perform(authorized(jwt).param("categoryId", "1").param("name", " anA ")
                        .param("dayOfWeek", "MONDAY").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.page").value(1));
        mvc.perform(authorized(jwt).param("enabled", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(13));
        mvc.perform(authorized(jwt).param("name", "%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authorized(jwt).param("dayOfWeek", "TUESDAY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(authorized(jwt).param("categoryId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void rejectsInvalidFiltersAndExpiredToken() throws Exception {
        String jwt = token(SECRET, "backend-turnos", "backend-catalogo", List.of("ROLE_USER"));
        mvc.perform(authorized(jwt).param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(authorized(jwt).param("size", "101")).andExpect(status().isBadRequest());
        mvc.perform(authorized(jwt).param("categoryId", "0")).andExpect(status().isBadRequest());
        mvc.perform(authorized(jwt).param("dayOfWeek", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(authorized(token(SECRET, "backend-turnos", "backend-catalogo", List.of("ROLE_USER"),
                Instant.now().minusSeconds(60)))).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorized(String jwt) {
        return get("/api/professionals").header("Authorization", "Bearer " + jwt);
    }

    private String token(String secret, String issuer, String audience, List<String> roles) {
        return token(secret, issuer, audience, roles, Instant.now().plusSeconds(120));
    }

    private String token(String secret, String issuer, String audience, List<String> roles, Instant expiry) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(
                new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).subject("user-42")
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(expiry).claim("authorities", roles).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    private CatalogSnapshot fixture() {
        var at = Instant.parse("2026-01-01T00:00:00Z");
        return new CatalogSnapshot(7, List.of(
                new CatalogSnapshot.Category(1, "Clinica", null, true, at, at),
                new CatalogSnapshot.Category(2, "Pediatria", null, true, at, at)), List.of(
                new CatalogSnapshot.Professional(10, 1, "Ana", "Zeta", true, at, at),
                new CatalogSnapshot.Professional(11, 1, "Ana", "Perez", true, at, at),
                new CatalogSnapshot.Professional(12, 2, "Luis", "Garcia", true, at, at),
                new CatalogSnapshot.Professional(13, 1, "Ines", "Ruiz", false, at, at)), List.of(
                schedule(20, 10, DayOfWeek.MONDAY, true, at),
                schedule(21, 10, DayOfWeek.MONDAY, true, at),
                schedule(22, 11, DayOfWeek.MONDAY, true, at),
                schedule(23, 12, DayOfWeek.TUESDAY, false, at)));
    }

    private CatalogSnapshot.WeeklySchedule schedule(long id, long professionalId, DayOfWeek day,
                                                     boolean enabled, Instant at) {
        return new CatalogSnapshot.WeeklySchedule(id, professionalId, day, LocalTime.of(9, 0),
                LocalTime.of(12, 0), 30, enabled, at, at);
    }
}
