package ar.edu.turnos.catalogo.catalogo.infrastructure.persistence.jdbc;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalAgenda;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.ProfessionalAgendaStore;
import java.time.DayOfWeek;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalAgendaJdbcAdapter implements ProfessionalAgendaStore {
    private static final String PROFESSIONAL_SQL = """
            SELECT p.id, p.first_name, p.last_name, p.enabled, c.id AS category_id,
                   c.name AS category_name, c.enabled AS category_enabled
            FROM professional p JOIN professional_category c ON c.id = p.category_id
            WHERE p.id = ?
            """;
    private static final String SCHEDULES_SQL = """
            SELECT id, day_of_week, start_time, end_time, slot_duration_minutes
            FROM weekly_schedule
            WHERE professional_id = ? AND enabled = true
            ORDER BY CASE day_of_week
                WHEN 'MONDAY' THEN 1 WHEN 'TUESDAY' THEN 2 WHEN 'WEDNESDAY' THEN 3
                WHEN 'THURSDAY' THEN 4 WHEN 'FRIDAY' THEN 5 WHEN 'SATURDAY' THEN 6
                WHEN 'SUNDAY' THEN 7 END, start_time, id
            """;

    private final JdbcTemplate jdbc;

    public ProfessionalAgendaJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ProfessionalAgenda> findById(long id) {
        var professionals = jdbc.query(PROFESSIONAL_SQL, (rs, row) -> new ProfessionalRow(
                rs.getLong("id"), rs.getString("first_name"), rs.getString("last_name"), rs.getBoolean("enabled"),
                new ProfessionalAgenda.Category(rs.getLong("category_id"), rs.getString("category_name"),
                        rs.getBoolean("category_enabled"))), id);
        if (professionals.isEmpty()) return Optional.empty();

        var schedules = jdbc.query(SCHEDULES_SQL, (rs, row) -> new ProfessionalAgenda.WeeklySchedule(
                rs.getLong("id"), DayOfWeek.valueOf(rs.getString("day_of_week")),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
                rs.getInt("slot_duration_minutes")), id);
        var professional = professionals.getFirst();
        return Optional.of(new ProfessionalAgenda(professional.id(), professional.firstName(),
                professional.lastName(), professional.enabled(), professional.category(), schedules));
    }

    private record ProfessionalRow(long id, String firstName, String lastName, boolean enabled,
                                   ProfessionalAgenda.Category category) { }
}
