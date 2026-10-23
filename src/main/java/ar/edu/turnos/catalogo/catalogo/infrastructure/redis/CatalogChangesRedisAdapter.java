package ar.edu.turnos.catalogo.catalogo.infrastructure.redis;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogHistory;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogChangesSource;
import java.util.List;
import java.util.function.ToLongFunction;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CatalogChangesRedisAdapter implements CatalogChangesSource {
    private static final String PREFIX = "catedra:sync:";
    private final StringRedisTemplate redis;
    private final JsonMapper json = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    public CatalogChangesRedisAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public CatalogHistory history() {
        var metadata = redis.<String, String>opsForHash().entries(PREFIX + "metadata");
        try {
            return new CatalogHistory(Long.parseLong(metadata.get("currentVersion")),
                    Long.parseLong(metadata.get("oldestAvailableVersion")));
        } catch (NumberFormatException e) {
            throw new CatalogRebuildRequiredException("Metadata Redis ausente o invalida");
        }
    }

    @Override
    public CatalogSnapshot changes(long version) {
        ChangesEnvelope envelope = decode(redis.opsForValue().get(PREFIX + "changes:" + version), ChangesEnvelope.class);
        if (envelope.version() == null || envelope.version() != version || envelope.changes() == null) {
            throw new CatalogRebuildRequiredException("Incremento ausente o inconsistente");
        }
        var changes = envelope.changes();
        return new CatalogSnapshot(version,
                entities("professional-categories", changes.professionalCategories(), CatalogSnapshot.Category.class,
                        CatalogSnapshot.Category::id),
                entities("professionals", changes.professionals(), CatalogSnapshot.Professional.class,
                        CatalogSnapshot.Professional::id),
                entities("weekly-schedules", changes.weeklySchedules(), CatalogSnapshot.WeeklySchedule.class,
                        CatalogSnapshot.WeeklySchedule::id));
    }

    private <T> List<T> entities(String hash, List<Long> ids, Class<T> type, ToLongFunction<T> identity) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0)
                || ids.stream().distinct().count() != ids.size()) {
            throw new CatalogRebuildRequiredException("Lista de IDs invalida");
        }
        return ids.stream().map(id -> {
            T entity = decode(redis.<String, String>opsForHash().get(PREFIX + hash, id.toString()), type);
            if (identity.applyAsLong(entity) != id) {
                throw new CatalogRebuildRequiredException("ID de entidad no coincide con el hash");
            }
            return entity;
        }).toList();
    }

    private <T> T decode(String value, Class<T> type) {
        if (value == null) {
            throw new CatalogRebuildRequiredException("Datos Redis incompletos");
        }
        try {
            T decoded = json.readValue(value, type);
            if (decoded == null) {
                throw new CatalogRebuildRequiredException("Datos Redis nulos");
            }
            return decoded;
        } catch (tools.jackson.core.JacksonException e) {
            throw new CatalogRebuildRequiredException("JSON Redis invalido");
        }
    }

    record ChangesEnvelope(Long version, Changes changes) { }
    record Changes(List<Long> professionalCategories, List<Long> professionals, List<Long> weeklySchedules) { }
}
