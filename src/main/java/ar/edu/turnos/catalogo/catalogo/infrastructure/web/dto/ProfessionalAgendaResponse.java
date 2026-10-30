package ar.edu.turnos.catalogo.catalogo.infrastructure.web.dto;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalAgenda;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record ProfessionalAgendaResponse(long id, String firstName, String lastName, boolean enabled,
                                         Category category, List<WeeklySchedule> weeklySchedules) {
    public static ProfessionalAgendaResponse from(ProfessionalAgenda agenda) {
        var category = new Category(agenda.category().id(), agenda.category().name(), agenda.category().enabled());
        var schedules = agenda.weeklySchedules().stream()
                .map(schedule -> new WeeklySchedule(schedule.id(), schedule.dayOfWeek(), schedule.startTime(),
                        schedule.endTime(), schedule.slotDurationMinutes()))
                .toList();
        return new ProfessionalAgendaResponse(agenda.id(), agenda.firstName(), agenda.lastName(), agenda.enabled(),
                category, schedules);
    }

    public record Category(long id, String name, boolean enabled) { }

    public record WeeklySchedule(long id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime,
                                 int slotDurationMinutes) { }
}
