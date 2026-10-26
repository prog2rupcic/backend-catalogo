package ar.edu.turnos.catalogo.catalogo.domain.ports.in;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogNotification;
import java.util.Optional;

public interface RebuildCatalogUseCase {
    void rebuild(long minimumVersion, Optional<CatalogNotification> notification);
}
