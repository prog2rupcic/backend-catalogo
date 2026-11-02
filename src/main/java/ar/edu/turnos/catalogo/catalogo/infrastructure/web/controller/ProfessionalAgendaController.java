package ar.edu.turnos.catalogo.catalogo.infrastructure.web.controller;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogUnavailableException;
import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalNotFoundException;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.GetProfessionalAgendaUseCase;
import ar.edu.turnos.catalogo.catalogo.infrastructure.web.dto.ProfessionalAgendaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/professionals")
public class ProfessionalAgendaController {
    private final GetProfessionalAgendaUseCase getAgenda;

    public ProfessionalAgendaController(GetProfessionalAgendaUseCase getAgenda) {
        this.getAgenda = getAgenda;
    }

    @GetMapping("/{id}")
    public ProfessionalAgendaResponse get(@PathVariable long id) {
        return ProfessionalAgendaResponse.from(getAgenda.get(id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> invalidId() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(ProfessionalNotFoundException.class)
    public ResponseEntity<Void> notFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    public ResponseEntity<Void> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
