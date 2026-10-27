package ar.edu.turnos.catalogo.catalogo.domain.ports.out;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic.KafkaState;

public interface CatalogKafkaStatusSource {
    KafkaState state();
}
