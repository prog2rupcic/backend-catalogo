package ar.edu.turnos.catalogo.catalogo.infrastructure.web.controller;

import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
import ar.edu.turnos.catalogo.catalogo.infrastructure.web.dto.CatalogDiagnosticsResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/synchronization")
public class CatalogDiagnosticsController {
    private final CatalogDiagnosticsUseCase diagnostics;

    public CatalogDiagnosticsController(CatalogDiagnosticsUseCase diagnostics) {
        this.diagnostics = diagnostics;
    }

    @GetMapping("/status")
    public CatalogDiagnosticsResponse status() {
        return CatalogDiagnosticsResponse.from(diagnostics.status());
    }

    @ExceptionHandler({DataAccessException.class, CannotCreateTransactionException.class})
    public ResponseEntity<Void> databaseUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
