package ar.edu.turnos.catalogo.catalogo.infrastructure.web.controller;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalSearch;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogUnavailableException;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SearchProfessionalsUseCase;
import ar.edu.turnos.catalogo.catalogo.infrastructure.web.dto.ProfessionalSearchResponse;
import java.time.DayOfWeek;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professionals")
public class ProfessionalSearchController {
    private final SearchProfessionalsUseCase search;

    public ProfessionalSearchController(SearchProfessionalsUseCase search) {
        this.search = search;
    }

    @GetMapping
    public ProfessionalSearchResponse search(@RequestParam(required = false) Long categoryId,
                                              @RequestParam(required = false) String name,
                                              @RequestParam(defaultValue = "true") Boolean enabled,
                                              @RequestParam(required = false) DayOfWeek dayOfWeek,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return ProfessionalSearchResponse.from(search.search(
                new ProfessionalSearch.Filter(categoryId, name, enabled, dayOfWeek, page, size)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> invalidFilter() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
