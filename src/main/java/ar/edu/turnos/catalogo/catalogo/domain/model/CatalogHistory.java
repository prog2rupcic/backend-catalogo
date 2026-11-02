package ar.edu.turnos.catalogo.catalogo.domain.model;

public record CatalogHistory(long currentVersion, long oldestAvailableVersion) {
    public CatalogHistory {
        if (currentVersion < 0 || oldestAvailableVersion < 0 || oldestAvailableVersion > currentVersion) {
            throw new CatalogRebuildRequiredException("Metadata de versiones invalida");
        }
    }

    public void requireContinuousHistory(long localVersion, long notifiedVersion) {
        if (localVersion > currentVersion || notifiedVersion > currentVersion
                || (localVersion < currentVersion && localVersion < oldestAvailableVersion)) {
            throw new CatalogRebuildRequiredException("No hay historial continuo verificable");
        }
    }
}
