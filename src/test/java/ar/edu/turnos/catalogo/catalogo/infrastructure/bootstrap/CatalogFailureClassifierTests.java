package ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogFailureClassifierTests {
    @Test
    void databaseConnectionFailuresAndHttpServiceOutagesAreRecoverable() {
        assertThat(CatalogFailureClassifier.isRetryable(new CannotGetJdbcConnectionException("offline"))).isTrue();
        assertThat(CatalogFailureClassifier.isRetryable(new CannotCreateTransactionException("offline"))).isTrue();
        assertThat(CatalogFailureClassifier.isRetryable(new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)))
                .isTrue();
        assertThat(CatalogFailureClassifier.isRetryable(new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)))
                .isTrue();
    }

    @Test
    void invalidInputAndUnauthorizedResponsesDoNotTriggerImmediateRetries() {
        assertThat(CatalogFailureClassifier.isRetryable(new IllegalArgumentException("invalid"))).isFalse();
        assertThat(CatalogFailureClassifier.isRetryable(new HttpClientErrorException(HttpStatus.UNAUTHORIZED)))
                .isFalse();
    }
}
