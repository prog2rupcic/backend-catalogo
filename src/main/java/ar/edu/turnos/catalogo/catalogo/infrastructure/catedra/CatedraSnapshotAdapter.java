package ar.edu.turnos.catalogo.catalogo.infrastructure.catedra;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotSource;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

//Implementa CatalogSnapshotSource: llama al endpoint contractual con el JWT técnico, interpreta el JSON y lo transforma al modelo del dominio.
// Los DTO de la cátedra viven aquí para que no se filtren al dominio.
@Component
public class CatedraSnapshotAdapter implements CatalogSnapshotSource {
    private final RestClient restClient;

    public CatedraSnapshotAdapter(RestClient.Builder builder,
                                  @Value("${catalog.catedra.base-url}") String baseUrl,
                                  @Value("${catalog.catedra.jwt}") String jwt) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt).build();
    }

    @Override
    public CatalogSnapshot fetch() {
        SnapshotResponse response = restClient.get().uri("/api/synchronization/snapshot")
                .retrieve().body(SnapshotResponse.class);
        if (response == null || response.snapshotVersion() == null) {
            throw new IllegalArgumentException("Respuesta de snapshot incompleta");
        }
        return response.toDomain();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SnapshotResponse(Long snapshotVersion, List<CategoryResponse> professionalCategories,
                            List<ProfessionalResponse> professionals, List<ScheduleResponse> weeklySchedules) {
        CatalogSnapshot toDomain() {
            if (professionalCategories == null || professionals == null || weeklySchedules == null) {
                throw new IllegalArgumentException("Colecciones de snapshot ausentes");
            }
            return new CatalogSnapshot(snapshotVersion,
                    professionalCategories.stream().map(CategoryResponse::toDomain).toList(),
                    professionals.stream().map(ProfessionalResponse::toDomain).toList(),
                    weeklySchedules.stream().map(ScheduleResponse::toDomain).toList());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CategoryResponse(long id, String name, String description, boolean enabled,
                            Instant createdAt, Instant updatedAt) {
        CatalogSnapshot.Category toDomain() {
            return new CatalogSnapshot.Category(id, name, description, enabled, createdAt, updatedAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ProfessionalResponse(long id, long categoryId, String firstName, String lastName,
                                boolean enabled, Instant createdAt, Instant updatedAt) {
        CatalogSnapshot.Professional toDomain() {
            return new CatalogSnapshot.Professional(id, categoryId, firstName, lastName, enabled, createdAt, updatedAt);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ScheduleResponse(long id, long professionalId, DayOfWeek dayOfWeek,
                            LocalTime startTime, LocalTime endTime, int slotDurationMinutes,
                            boolean enabled, Instant createdAt, Instant updatedAt) {
        CatalogSnapshot.WeeklySchedule toDomain() {
            return new CatalogSnapshot.WeeklySchedule(id, professionalId, dayOfWeek, startTime,
                    endTime, slotDurationMinutes, enabled, createdAt, updatedAt);
        }
    }
}
