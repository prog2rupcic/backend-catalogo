package ar.edu.turnos.catalogo.catalogo.domain.model;

/** Solicita recuperación mediante snapshot; nunca permite saltar versiones. */
public class CatalogRebuildRequiredException extends RuntimeException {
    public CatalogRebuildRequiredException(String message) {
        super(message);
    }
}
