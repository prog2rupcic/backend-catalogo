package ar.edu.turnos.catalogo.catalogo.infrastructure.persistence.jdbc;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalSearch;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.ProfessionalSearchStore;
import java.sql.Types;
import java.util.Locale;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalSearchJdbcAdapter implements ProfessionalSearchStore {
    private static final String COUNT_SQL = """
            SELECT COUNT(*) FROM professional p
            WHERE (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:enabled IS NULL OR p.enabled = :enabled)
              AND (:name IS NULL OR LOWER(p.first_name || ' ' || p.last_name) LIKE :name ESCAPE '!')
              AND (:dayOfWeek IS NULL OR EXISTS (
                  SELECT 1 FROM weekly_schedule s
                  WHERE s.professional_id = p.id AND s.enabled = true AND s.day_of_week = :dayOfWeek))
            """;
    private static final String PAGE_SQL = """
            SELECT p.id, p.category_id, p.first_name, p.last_name, p.enabled
            FROM professional p
            WHERE (:categoryId IS NULL OR p.category_id = :categoryId)
              AND (:enabled IS NULL OR p.enabled = :enabled)
              AND (:name IS NULL OR LOWER(p.first_name || ' ' || p.last_name) LIKE :name ESCAPE '!')
              AND (:dayOfWeek IS NULL OR EXISTS (
                  SELECT 1 FROM weekly_schedule s
                  WHERE s.professional_id = p.id AND s.enabled = true AND s.day_of_week = :dayOfWeek))
            ORDER BY LOWER(p.last_name), LOWER(p.first_name), p.id
            LIMIT :limit OFFSET :offset
            """;
    private final NamedParameterJdbcTemplate jdbc;

    public ProfessionalSearchJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ProfessionalSearch.Page search(ProfessionalSearch.Filter filter) {
        String name = null;
        if (filter.name() != null) {
            String escaped = filter.name().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            name = "%" + escaped + "%";
        }
        var parameters = new MapSqlParameterSource()
                .addValue("categoryId", filter.categoryId(), Types.BIGINT)
                .addValue("enabled", filter.enabled(), Types.BOOLEAN)
                .addValue("name", name, Types.VARCHAR)
                .addValue("dayOfWeek", filter.dayOfWeek() == null ? null : filter.dayOfWeek().name(), Types.VARCHAR);
        long total = jdbc.queryForObject(COUNT_SQL, parameters, Long.class);
        parameters.addValue("limit", filter.size());
        parameters.addValue("offset", (long) filter.page() * filter.size());
        var rows = jdbc.query(PAGE_SQL, parameters, (rs, number) -> new ProfessionalSearch.Professional(rs.getLong("id"),
                        rs.getLong("category_id"), rs.getString("first_name"), rs.getString("last_name"),
                        rs.getBoolean("enabled")));
        return new ProfessionalSearch.Page(rows, total, filter.page(), filter.size());
    }
}
