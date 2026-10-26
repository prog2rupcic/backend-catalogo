package ar.edu.turnos.catalogo.catalogo.infrastructure.persistence.jdbc;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogIncrementStore;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CatalogSnapshotJdbcAdapter implements CatalogSnapshotStore, CatalogIncrementStore {
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
        // Order matters because schedules and professionals reference their parents.
        jdbc.update("DELETE FROM weekly_schedule");
        jdbc.update("DELETE FROM professional");
        jdbc.update("DELETE FROM professional_category");

        upsert(snapshot);
    }

    @Override
    public void upsert(CatalogSnapshot snapshot) {
        for (CatalogSnapshot.Category c : snapshot.categories()) {
            jdbc.update("INSERT INTO professional_category (id, name, description, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET name=EXCLUDED.name, description=EXCLUDED.description, enabled=EXCLUDED.enabled, created_at=EXCLUDED.created_at, updated_at=EXCLUDED.updated_at",
                    c.id(), c.name(), c.description(), c.enabled(), Timestamp.from(c.createdAt()), Timestamp.from(c.updatedAt()));
        }
        for (CatalogSnapshot.Professional p : snapshot.professionals()) {
            jdbc.update("INSERT INTO professional (id, category_id, first_name, last_name, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET category_id=EXCLUDED.category_id, first_name=EXCLUDED.first_name, last_name=EXCLUDED.last_name, enabled=EXCLUDED.enabled, created_at=EXCLUDED.created_at, updated_at=EXCLUDED.updated_at",
                    p.id(), p.categoryId(), p.firstName(), p.lastName(), p.enabled(), Timestamp.from(p.createdAt()), Timestamp.from(p.updatedAt()));
        }
        for (CatalogSnapshot.WeeklySchedule s : snapshot.schedules()) {
            jdbc.update("INSERT INTO weekly_schedule (id, professional_id, day_of_week, start_time, end_time, slot_duration_minutes, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET professional_id=EXCLUDED.professional_id, day_of_week=EXCLUDED.day_of_week, start_time=EXCLUDED.start_time, end_time=EXCLUDED.end_time, slot_duration_minutes=EXCLUDED.slot_duration_minutes, enabled=EXCLUDED.enabled, created_at=EXCLUDED.created_at, updated_at=EXCLUDED.updated_at",
                    s.id(), s.professionalId(), s.dayOfWeek().name(), s.startTime(), s.endTime(),
                    s.slotDurationMinutes(), s.enabled(), Timestamp.from(s.createdAt()), Timestamp.from(s.updatedAt()));
        }
        jdbc.update("INSERT INTO catalog_sync_state (id, applied_version, applied_at) VALUES (1, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET applied_version = EXCLUDED.applied_version, applied_at = EXCLUDED.applied_at",
                snapshot.version(), Timestamp.from(Instant.now()));
    }

    @Override
    public long appliedVersion() {
        return readVersion(false);
    }

    @Override
    public long lockAppliedVersion() {
        return readVersion(true);
    }

    private long readVersion(boolean lock) {
        var versions = jdbc.queryForList("SELECT applied_version FROM catalog_sync_state WHERE id=1"
                + (lock ? " FOR UPDATE" : ""), Long.class);
        if (versions.isEmpty()) {
            throw new CatalogRebuildRequiredException("Catalogo no inicializado");
        }
        return versions.getFirst();
    }

    @Override
    public boolean processed(String eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM processed_catalog_event WHERE event_id=?)", Boolean.class, eventId));
    }

    @Override
    public void markProcessed(String eventId) {
        jdbc.update("INSERT INTO processed_catalog_event (event_id) VALUES (?) ON CONFLICT DO NOTHING", eventId);
    }

}
