package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalSearch;

public interface SearchProfessionalsUseCase {
    ProfessionalSearch.Page search(ProfessionalSearch.Filter filter);
}
