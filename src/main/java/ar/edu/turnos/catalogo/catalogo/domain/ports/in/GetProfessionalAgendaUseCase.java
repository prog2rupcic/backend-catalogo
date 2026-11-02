package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalAgenda;

public interface GetProfessionalAgendaUseCase {
    ProfessionalAgenda get(long id);
}
