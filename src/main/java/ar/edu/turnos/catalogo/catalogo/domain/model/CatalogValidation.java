package ar.edu.turnos.catalogo.catalogo.domain.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CatalogValidation {
    private CatalogValidation() { }

    public static void validate(CatalogSnapshot data, boolean complete) {
        if (data == null || data.version() < 0 || data.categories() == null
                || data.professionals() == null || data.schedules() == null) {
            throw new IllegalArgumentException("Catalogo incompleto");
        }
        var categories = unique(data.categories().stream().map(CatalogSnapshot.Category::id).toList());
        var professionals = unique(data.professionals().stream().map(CatalogSnapshot.Professional::id).toList());
        unique(data.schedules().stream().map(CatalogSnapshot.WeeklySchedule::id).toList());
        if (complete && data.professionals().stream().anyMatch(p -> !categories.contains(p.categoryId()))) {
            throw new IllegalArgumentException("Profesional con categoria ausente");
        }
        for (var s : data.schedules()) {
            if ((complete && !professionals.contains(s.professionalId())) || s.dayOfWeek() == null
                    || s.startTime() == null || s.endTime() == null || !s.startTime().isBefore(s.endTime())
                    || s.slotDurationMinutes() <= 0) {
                throw new IllegalArgumentException("Horario semanal invalido");
            }
        }
    }

    private static Set<Long> unique(List<Long> ids) {
        var set = new HashSet<>(ids);
        if (set.size() != ids.size() || ids.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("IDs invalidos o duplicados");
        }
        return set;
    }
}
