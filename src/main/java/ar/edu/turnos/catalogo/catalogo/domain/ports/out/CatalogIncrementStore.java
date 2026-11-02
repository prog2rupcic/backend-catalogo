package ar.edu.turnos.catalogo.catalogo.domain.ports.out;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogSnapshot;

public interface CatalogIncrementStore {
    long appliedVersion();
    long lockAppliedVersion();
    boolean processed(String eventId);
    void upsert(CatalogSnapshot changes);
    void markProcessed(String eventId);
}
