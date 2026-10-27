package ar.edu.turnos.catalogo.catalogo.infrastructure.persistence.jdbc;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogDiagnosticStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CatalogDiagnosticJdbcAdapter implements CatalogDiagnosticStore {
    private final JdbcTemplate jdbc;

    public CatalogDiagnosticJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void begin(UUID id, CatalogDiagnostic.Source source, Instant at) {
        jdbc.update("UPDATE catalog_diagnostics SET attempt_id=?, source=?, activity='CHECKING', "
                        + "started_at=?, finished_at=NULL WHERE id=1",
                id, source.name(), Timestamp.from(at));
    }

    @Override
    public void activity(UUID id, CatalogDiagnostic.Activity activity) {
        jdbc.update("UPDATE catalog_diagnostics SET activity=? WHERE id=1 AND attempt_id=? AND activity<>'IDLE'",
                activity.name(), id);
    }

    @Override
    public void observed(UUID id, long version, Instant at) {
        jdbc.update("UPDATE catalog_diagnostics SET remote_version=?, remote_observed_at=? "
                        + "WHERE id=1 AND attempt_id=? AND activity<>'IDLE'",
                version, Timestamp.from(at), id);
    }

    @Override
    public void succeed(UUID id, Instant at) {
        jdbc.update("UPDATE catalog_diagnostics SET activity='IDLE', "
                        + "outcome=CASE WHEN (SELECT applied_version FROM catalog_sync_state WHERE id=1) IS NULL THEN 'UNINITIALIZED' "
                        + "WHEN catalog_diagnostics.remote_version IS NOT NULL "
                        + "AND (SELECT applied_version FROM catalog_sync_state WHERE id=1) "
                        + "< catalog_diagnostics.remote_version THEN 'BEHIND' ELSE 'CURRENT' END, "
                        + "finished_at=?, last_success_at=? "
                        + "WHERE id=1 AND attempt_id=? AND activity<>'IDLE'",
                Timestamp.from(at), Timestamp.from(at), id);
    }

    @Override
    public void fail(UUID id, CatalogDiagnostic.ErrorCode code, Instant at) {
        jdbc.update("UPDATE catalog_diagnostics SET activity='IDLE', outcome='ERROR', finished_at=?, "
                        + "last_error_code=?, last_error_at=? WHERE id=1 AND attempt_id=? AND activity<>'IDLE'",
                Timestamp.from(at), code.name(), Timestamp.from(at), id);
    }

    @Override
    public void interruptRunning(Instant at) {
        jdbc.update("UPDATE catalog_diagnostics SET activity='IDLE', outcome='INTERRUPTED', finished_at=? "
                + "WHERE id=1 AND activity<>'IDLE'", Timestamp.from(at));
    }

    @Override
    public CatalogDiagnostic read() {
        return jdbc.queryForObject("SELECT s.applied_version, d.* FROM catalog_diagnostics d "
                + "LEFT JOIN catalog_sync_state s ON s.id=1 WHERE d.id=1", (rs, row) -> map(rs));
    }

    private CatalogDiagnostic map(ResultSet rs) throws SQLException {
        Long applied = rs.getObject("applied_version", Long.class);
        Long remote = rs.getObject("remote_version", Long.class);
        return new CatalogDiagnostic(applied, remote, instant(rs, "remote_observed_at"),
                rs.getObject("attempt_id", UUID.class), enumValue(CatalogDiagnostic.Source.class, rs.getString("source")),
                CatalogDiagnostic.Activity.valueOf(rs.getString("activity")),
                CatalogDiagnostic.Outcome.valueOf(rs.getString("outcome")),
                instant(rs, "started_at"), instant(rs, "finished_at"), instant(rs, "last_success_at"),
                enumValue(CatalogDiagnostic.ErrorCode.class, rs.getString("last_error_code")),
                instant(rs, "last_error_at"), CatalogDiagnostic.KafkaState.DISABLED);
    }

    private Instant instant(ResultSet rs, String name) throws SQLException {
        Timestamp value = rs.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
