package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

// Contrato de entrada para inicializar si no hay catálogo local.

public interface InitializeCatalogUseCase {
    void initializeIfEmpty();
}
