package ar.edu.turnos.catalogo.catalogo.infrastructure.web.dto;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalSearch;
import java.util.List;

public record ProfessionalSearchResponse(List<Professional> content, long totalElements, int page, int size) {
    public static ProfessionalSearchResponse from(ProfessionalSearch.Page result) {
        return new ProfessionalSearchResponse(result.content().stream().map(Professional::from).toList(),
                result.totalElements(), result.page(), result.size());
    }

    public record Professional(long id, long categoryId, String firstName, String lastName, boolean enabled) {
        static Professional from(ProfessionalSearch.Professional value) {
            return new Professional(value.id(), value.categoryId(), value.firstName(), value.lastName(), value.enabled());
        }
    }
}
