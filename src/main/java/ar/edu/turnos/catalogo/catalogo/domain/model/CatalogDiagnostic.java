package ar.edu.turnos.catalogo.catalogo.domain.model;

import java.time.Instant;
import java.util.UUID;

/** La version aplicada viene del estado del catalogo, nunca de una copia en diagnostico. */
public record CatalogDiagnostic(Long appliedVersion, Long remoteVersion, Instant remoteObservedAt,
                                UUID attemptId, Source source, Activity activity, Outcome outcome,
                                Instant startedAt, Instant finishedAt, Instant lastSuccessAt,
                                ErrorCode lastErrorCode, Instant lastErrorAt, KafkaState kafka) {
    public enum Source { STARTUP, PERIODIC, KAFKA }
    public enum Activity { IDLE, CHECKING, APPLYING, REBUILDING }
    public enum Outcome { UNINITIALIZED, CURRENT, BEHIND, ERROR, INTERRUPTED }
    public enum ErrorCode { REMOTE_UNAVAILABLE, PERSISTENCE_UNAVAILABLE, REBUILD_REQUIRED, INVALID_INPUT, OTHER }
    public enum KafkaState { DISABLED, WAITING, CONSUMING, STOPPED_RETRYABLE, STOPPED_INVALID }
}
