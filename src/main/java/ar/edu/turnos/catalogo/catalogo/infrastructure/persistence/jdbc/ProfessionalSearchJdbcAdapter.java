package ar.edu.turnos.catalogo.catalogo.infrastructure.persistence.jdbc;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalSearch;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.ProfessionalSearchStore;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProfessionalSearchJdbcAdapter implements ProfessionalSearchStore {
    private final NamedParameterJdbcTemplate jdbc;

    public ProfessionalSearchJdbcAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ProfessionalSearch.Page search(ProfessionalSearch.Filter filter) {
        var where = new StringBuilder(" FROM professional p WHERE 1=1");
        var parameters = new MapSqlParameterSource();
        if (filter.categoryId() != null) {
            where.append(" AND p.category_id=:categoryId");
            parameters.addValue("categoryId", filter.categoryId());
        }
        if (filter.enabled() != null) {
            where.append(" AND p.enabled=:enabled");
            parameters.addValue("enabled", filter.enabled());
        }
        if (filter.name() != null) {
            where.append(" AND LOWER(p.first_name || ' ' || p.last_name) LIKE :name ESCAPE '!'");
            String escaped = filter.name().toLowerCase(java.util.Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parameters.addValue("name", "%" + escaped + "%");
        }
        if (filter.dayOfWeek() != null) {
            where.append(" AND EXISTS (SELECT 1 FROM weekly_schedule s WHERE s.professional_id=p.id")
                    .append(" AND s.enabled=true AND s.day_of_week=:dayOfWeek)");
            parameters.addValue("dayOfWeek", filter.dayOfWeek().name());
        }
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, parameters, Long.class);
        parameters.addValue("limit", filter.size());
        parameters.addValue("offset", (long) filter.page() * filter.size());
        var rows = jdbc.query("SELECT p.id, p.category_id, p.first_name, p.last_name, p.enabled" + where
                        + " ORDER BY LOWER(p.last_name), LOWER(p.first_name), p.id LIMIT :limit OFFSET :offset",
                parameters, (rs, number) -> new ProfessionalSearch.Professional(rs.getLong("id"),
                        rs.getLong("category_id"), rs.getString("first_name"), rs.getString("last_name"),
                        rs.getBoolean("enabled")));
        return new ProfessionalSearch.Page(rows, total, filter.page(), filter.size());
    }
}
