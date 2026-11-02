package ar.edu.turnos.catalogo.catalogo.domain.ports.out;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalSearch;

public interface ProfessionalSearchStore {
    ProfessionalSearch.Page search(ProfessionalSearch.Filter filter);
}
