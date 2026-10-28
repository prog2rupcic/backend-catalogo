package ar.edu.turnos.catalogo.catalogo.domain.model;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

// Modelo del snapshot: versión y colecciones de categorías, profesionales y horarios.
// Representa los datos con los que trabaja el caso de uso.

public record CatalogSnapshot(long version, List<Category> categories,
                              List<Professional> professionals, List<WeeklySchedule> schedules) {

    public record Category(long id, String name, String description, boolean enabled,
                           Instant createdAt, Instant updatedAt) {
    }

    public record Professional(long id, long categoryId, String firstName, String lastName,
                               boolean enabled, Instant createdAt, Instant updatedAt) {
    }

    public record WeeklySchedule(long id, long professionalId, DayOfWeek dayOfWeek,
                                 LocalTime startTime, LocalTime endTime, int slotDurationMinutes,
                                 boolean enabled, Instant createdAt, Instant updatedAt) {
    }
}
