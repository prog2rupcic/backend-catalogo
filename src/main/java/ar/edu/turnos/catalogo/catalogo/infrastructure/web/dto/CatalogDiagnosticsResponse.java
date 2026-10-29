package ar.edu.turnos.catalogo.catalogo.infrastructure.web.dto;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogDiagnostic;
import java.time.Instant;
import java.util.UUID;

public record CatalogDiagnosticsResponse(Long appliedVersion, Long remoteVersion, Instant remoteObservedAt,
                                         UUID attemptId, CatalogDiagnostic.Source source,
                                         CatalogDiagnostic.Activity activity, CatalogDiagnostic.Outcome outcome,
                                         Instant startedAt, Instant finishedAt, Instant lastSuccessAt,
                                         CatalogDiagnostic.ErrorCode lastErrorCode, Instant lastErrorAt,
                                         CatalogDiagnostic.KafkaState kafka) {
    public static CatalogDiagnosticsResponse from(CatalogDiagnostic diagnostic) {
        return new CatalogDiagnosticsResponse(diagnostic.appliedVersion(), diagnostic.remoteVersion(),
                diagnostic.remoteObservedAt(), diagnostic.attemptId(), diagnostic.source(), diagnostic.activity(),
                diagnostic.outcome(), diagnostic.startedAt(), diagnostic.finishedAt(), diagnostic.lastSuccessAt(),
                diagnostic.lastErrorCode(), diagnostic.lastErrorAt(), diagnostic.kafka());
    }
}
