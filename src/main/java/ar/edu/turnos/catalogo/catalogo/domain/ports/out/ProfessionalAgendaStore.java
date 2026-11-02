package ar.edu.turnos.catalogo.catalogo.domain.ports.out;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalAgenda;
import java.util.Optional;

public interface ProfessionalAgendaStore {
    Optional<ProfessionalAgenda> findById(long id);
}
