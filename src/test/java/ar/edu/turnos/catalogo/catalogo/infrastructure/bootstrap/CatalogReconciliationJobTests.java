package ar.edu.turnos.catalogo.catalogo.infrastructure.bootstrap;

import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SynchronizeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.infrastructure.messaging.CatalogKafkaLifecycle;
import ar.edu.turnos.catalogo.config.CatalogReconciliationProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CatalogReconciliationJobTests {
    final SynchronizeCatalogUseCase sync = mock(SynchronizeCatalogUseCase.class);
    final CatalogKafkaLifecycle kafka = mock(CatalogKafkaLifecycle.class);
    final CatalogReconciliationProperties properties = new CatalogReconciliationProperties(
            Duration.ofSeconds(60), 3, Duration.ofSeconds(1));

    @Test
    void boundsRetriesAndNextCycleCanRecoverAndResumeKafka() {
        var waits = new ArrayList<Duration>();
        var job = new CatalogReconciliationJob(sync, Optional.of(kafka), properties, waits::add, true);
        doThrow(new TransientDataAccessResourceException("offline")).when(sync).reconcile();
        job.onReady();
        verify(sync, times(3)).reconcile();
        assertThat(waits).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        verifyNoInteractions(kafka);

        doNothing().when(sync).reconcile();
        job.periodically();
        verify(sync, times(4)).reconcile();
        verify(kafka).resumeAfterReconciliation();
    }

    @Test
    void permanentFailuresDoNotRetryWithinCycle() {
        var waits = new ArrayList<Duration>();
        var job = new CatalogReconciliationJob(sync, Optional.empty(), properties, waits::add, true);
        doThrow(new IllegalArgumentException("bad response")).when(sync).reconcile();
        job.runCycle();
        verify(sync).reconcile();
        assertThat(waits).isEmpty();
    }

    @Test
    void interruptedBackoffPreservesInterruptAndReleasesCycleGuard() {
        var job = new CatalogReconciliationJob(sync, Optional.empty(), properties,
                duration -> { throw new InterruptedException(); }, true);
        doThrow(new TransientDataAccessResourceException("offline")).when(sync).reconcile();
        try {
            job.runCycle();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        doNothing().when(sync).reconcile();
        job.runCycle();
        verify(sync, times(2)).reconcile();
    }

    @Test
    void startupCanBeDisabledButPeriodicWorkWaitsUntilReady() {
        var job = new CatalogReconciliationJob(sync, Optional.empty(), properties, duration -> { }, false);
        job.periodically();
        verifyNoInteractions(sync);
        job.onReady();
        verifyNoInteractions(sync);
        job.periodically();
        verify(sync).reconcile();
    }
}
