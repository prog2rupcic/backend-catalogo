package ar.edu.turnos.catalogo;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogIncrementUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogSnapshotUseCase;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
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
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"catalog.bootstrap.enabled=false", "catalog.catedra.base-url=http://localhost:9999",
        "catalog.catedra.jwt=test-only"})
@AutoConfigureMockMvc
@TestPropertySource("classpath:test-integration.properties")
@Testcontainers
class ProfessionalAgendaTests {
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
    @Autowired ApplyCatalogIncrementUseCase increments;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");
        jdbc.update("DELETE FROM catalog_sync_state");
    }

    @Test
    void returnsActiveAgendaInCalendarOrderWithoutDisabledSchedules() throws Exception {
        snapshots.applyIfEmpty(fixture());
        mvc.perform(get("/api/professionals/10").header("Authorization", "Bearer " + token("backend-catalogo", "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.category.id").value(1))
                .andExpect(jsonPath("$.category.name").value("Clinica"))
                .andExpect(jsonPath("$.category.enabled").value(true))
                .andExpect(jsonPath("$.weeklySchedules.length()").value(3))
                .andExpect(jsonPath("$.weeklySchedules[0].id").value(21))
                .andExpect(jsonPath("$.weeklySchedules[0].startTime", startsWith("09:00")))
                .andExpect(jsonPath("$.weeklySchedules[0].slotDurationMinutes").value(30))
                .andExpect(jsonPath("$.weeklySchedules[1].id").value(20))
                .andExpect(jsonPath("$.weeklySchedules[2].dayOfWeek").value("SUNDAY"));
    }

    @Test
    void distinguishesDisabledProfessionalAndCategoryFromMissingId() throws Exception {
        snapshots.applyIfEmpty(fixture());
        String bearer = "Bearer " + token("backend-catalogo", "ROLE_USER");

        mvc.perform(get("/api/professionals/11").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.weeklySchedules.length()").value(1));
        mvc.perform(get("/api/professionals/12").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.category.enabled").value(false));
        mvc.perform(get("/api/professionals/13").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.weeklySchedules.length()").value(0));
        mvc.perform(get("/api/professionals/999").header("Authorization", bearer))
                .andExpect(status().isNotFound());
    }

    @Test
    void reflectsAppliedIncrementsWithoutConsultingRemoteServices() throws Exception {
        snapshots.applyIfEmpty(fixture());
        var at = Instant.parse("2026-01-02T00:00:00Z");
        increments.apply(new CatalogSnapshot(8,
                List.of(new CatalogSnapshot.Category(1, "Clinica", null, false, at, at)),
                List.of(new CatalogSnapshot.Professional(10, 1, "Ana", "Perez", false, at, at)),
                List.of(new CatalogSnapshot.WeeklySchedule(21, 10, DayOfWeek.MONDAY,
                        LocalTime.of(9, 0), LocalTime.of(11, 0), 30, false, at, at))));

        mvc.perform(get("/api/professionals/10")
                        .header("Authorization", "Bearer " + token("backend-catalogo", "ROLE_USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.category.enabled").value(false))
                .andExpect(jsonPath("$.weeklySchedules.length()").value(2))
                .andExpect(jsonPath("$.weeklySchedules[0].id").value(20));
    }

    @Test
    void rejectsInvalidIdAndRequiresInitializedCatalog() throws Exception {
        String bearer = "Bearer " + token("backend-catalogo", "ROLE_USER");
        mvc.perform(get("/api/professionals/10").header("Authorization", bearer))
                .andExpect(status().isServiceUnavailable());
        snapshots.applyIfEmpty(fixture());
        mvc.perform(get("/api/professionals/0").header("Authorization", bearer))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAcceptsUserJwtIntendedForCatalog() throws Exception {
        snapshots.applyIfEmpty(fixture());
        mvc.perform(get("/api/professionals/10")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/professionals/10").header("Authorization", "Bearer " + token("backend-turnos", "ROLE_USER")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/professionals/10").header("Authorization", "Bearer " + token("backend-catalogo", "ROLE_ADMIN")))
                .andExpect(status().isForbidden());
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

    private CatalogSnapshot fixture() {
        var at = Instant.parse("2026-01-01T00:00:00Z");
        return new CatalogSnapshot(7, List.of(
                new CatalogSnapshot.Category(1, "Clinica", null, true, at, at),
                new CatalogSnapshot.Category(2, "Pediatria", null, false, at, at)), List.of(
                new CatalogSnapshot.Professional(10, 1, "Ana", "Perez", true, at, at),
                new CatalogSnapshot.Professional(11, 1, "Juan", "Suarez", false, at, at),
                new CatalogSnapshot.Professional(12, 2, "Maria", "Diaz", true, at, at),
                new CatalogSnapshot.Professional(13, 1, "Luz", "Luna", true, at, at)), List.of(
                schedule(20, 10, DayOfWeek.MONDAY, 11, true, at),
                schedule(21, 10, DayOfWeek.MONDAY, 9, true, at),
                schedule(22, 10, DayOfWeek.TUESDAY, 9, false, at),
                schedule(23, 10, DayOfWeek.SUNDAY, 9, true, at),
                schedule(24, 11, DayOfWeek.MONDAY, 9, true, at),
                schedule(25, 12, DayOfWeek.MONDAY, 9, true, at)));
    }

    private CatalogSnapshot.WeeklySchedule schedule(long id, long professionalId, DayOfWeek day, int hour,
                                                     boolean enabled, Instant at) {
        return new CatalogSnapshot.WeeklySchedule(id, professionalId, day, LocalTime.of(hour, 0),
                LocalTime.of(hour + 2, 0), 30, enabled, at, at);
    }
}
