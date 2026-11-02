package ar.edu.turnos.catalogo.catalogo.domain.model;

public class CatalogUnavailableException extends RuntimeException {
    public CatalogUnavailableException() {
        super("Catalogo aun no inicializado");
    }
}
