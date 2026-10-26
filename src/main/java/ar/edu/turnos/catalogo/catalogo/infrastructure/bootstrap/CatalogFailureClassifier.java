package ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogRebuildRequiredException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.kafka.KafkaException;

/** Clasificación técnica: no confundir un mensaje inválido con una caída temporal. */
public final class CatalogFailureClassifier {
    private CatalogFailureClassifier() { }

    public static boolean isRetryable(RuntimeException error) {
        if (error instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 408 || status == 429 || status >= 500;
        }
        return error instanceof TransientDataAccessException
                || error instanceof DataAccessResourceFailureException
                || error instanceof RecoverableDataAccessException
                || error instanceof CannotCreateTransactionException
                || error instanceof TransactionTimedOutException
                || error instanceof RestClientException
                || error instanceof KafkaException
                || error instanceof CatalogRebuildRequiredException;
    }
}
