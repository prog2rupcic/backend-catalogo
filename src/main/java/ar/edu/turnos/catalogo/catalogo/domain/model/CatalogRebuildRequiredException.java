package ar.edu.turnos.catalogo.catalogo.domain.model;

/** CAT-03 resolverá este resultado mediante snapshot; nunca permite saltar versiones. */
public class CatalogRebuildRequiredException extends RuntimeException {
    public CatalogRebuildRequiredException(String message) {
        super(message);
    }
}
