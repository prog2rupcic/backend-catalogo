package ar.edu.turnos.catalogo.catalogo.domain.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record ProfessionalAgenda(long id, String firstName, String lastName, boolean enabled,
                                 Category category, List<WeeklySchedule> weeklySchedules) {
    public ProfessionalAgenda {
        weeklySchedules = List.copyOf(weeklySchedules);
    }

    public record Category(long id, String name, boolean enabled) { }

    public record WeeklySchedule(long id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                                 int slotDurationMinutes) { }
}
