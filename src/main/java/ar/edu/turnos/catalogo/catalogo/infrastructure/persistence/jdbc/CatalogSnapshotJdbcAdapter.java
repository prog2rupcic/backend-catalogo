package ar.edu.turnos.catalogo.catalogo.infrastructure.persistence.jdbc;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CatalogSnapshotJdbcAdapter implements CatalogSnapshotStore {
    private final JdbcTemplate jdbc;

    public CatalogSnapshotJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isInitialized() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM catalog_sync_state WHERE id = 1)", Boolean.class));
    }

    @Override
    public void replace(CatalogSnapshot snapshot) {
        validate(snapshot);
        // Order matters because schedules and professionals reference their parents.
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");

        for (CatalogSnapshot.Category c : snapshot.categories()) {
            jdbc.update("INSERT INTO professional_category (id, name, description, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    c.id(), c.name(), c.description(), c.enabled(), Timestamp.from(c.createdAt()), Timestamp.from(c.updatedAt()));
        }
        for (CatalogSnapshot.Professional p : snapshot.professionals()) {
            jdbc.update("INSERT INTO professional (id, category_id, first_name, last_name, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    p.id(), p.categoryId(), p.firstName(), p.lastName(), p.enabled(), Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()));
        }
        for (CatalogSnapshot.WeeklySchedule s : snapshot.schedules()) {
            jdbc.update("INSERT INTO weekly_schedule (id, professional_id, day_of_week, start_time, end_time, slot_duration_minutes, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    s.id(), s.professionalId(), s.dayOfWeek().name(), s.startTime(), s.endTime(),
                    s.slotDurationMinutes(), s.enabled(), Timestamp.from(s.createdAt()), Timestamp.from(s.updatedAt()));
        }
        jdbc.update("INSERT INTO catalog_sync_state (id, applied_version, applied_at) VALUES (1, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET applied_version = EXCLUDED.applied_version, applied_at = EXCLUDED.applied_at",
                snapshot.version(), Timestamp.from(Instant.now()));
    }

    private void validate(CatalogSnapshot snapshot) {
        if (snapshot == null || snapshot.version() < 0 || snapshot.categories() == null
                || snapshot.professionals() == null || snapshot.schedules() == null) {
            throw new IllegalArgumentException("Snapshot incompleto");
        }
        Set<Long> categoryIds = uniqueIds(snapshot.categories().stream().map(CatalogSnapshot.Category::id).toList());
        Set<Long> professionalIds = uniqueIds(snapshot.professionals().stream().map(CatalogSnapshot.Professional::id).toList());
        uniqueIds(snapshot.schedules().stream().map(CatalogSnapshot.WeeklySchedule::id).toList());
        for (CatalogSnapshot.Professional p : snapshot.professionals()) {
            if (!categoryIds.contains(p.categoryId())) {
                throw new IllegalArgumentException("Profesional con categoria ausente");
            }
        }
        for (CatalogSnapshot.WeeklySchedule s : snapshot.schedules()) {
            if (!professionalIds.contains(s.professionalId()) || s.dayOfWeek() == null
                    || s.startTime() == null || s.endTime() == null
                    || !s.startTime().isBefore(s.endTime()) || s.slotDurationMinutes() <= 0) {
                throw new IllegalArgumentException("Horario semanal invalido");
            }
        }
    }

    private Set<Long> uniqueIds(List<Long> ids) {
        Set<Long> seen = new HashSet<>(ids);
        if (seen.size() != ids.size()) {
            throw new IllegalArgumentException("IDs duplicados en el snapshot");
        }
        return seen;
    }
}
