package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.*;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogIncrementUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.InitializeCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.CatalogDiagnosticsUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.RebuildCatalogUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogChangesSource;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogIncrementStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SynchronizeCatalogTests {
    final CatalogChangesSource source = mock(CatalogChangesSource.class);
    final CatalogIncrementStore store = mock(CatalogIncrementStore.class);
    final ApplyCatalogIncrementUseCase apply = mock(ApplyCatalogIncrementUseCase.class);
    final CatalogSnapshotStore snapshots = mock(CatalogSnapshotStore.class);
    final InitializeCatalogUseCase initialize = mock(InitializeCatalogUseCase.class);
    final RebuildCatalogUseCase rebuild = mock(RebuildCatalogUseCase.class);
    final CatalogDiagnosticsUseCase diagnostics = mock(CatalogDiagnosticsUseCase.class);
    final SynchronizeCatalogUseCaseImpl sync = new SynchronizeCatalogUseCaseImpl(source, store, apply,
            snapshots, initialize, rebuild, diagnostics);
    final CatalogNotification event = new CatalogNotification("event", 4);

    @BeforeEach
    void initialized() {
        when(snapshots.isInitialized()).thenReturn(true);
    }

    @Test
    void processesAllPendingVersionsInOrderAndCompletesInLastTransaction() {
        when(store.appliedVersion()).thenReturn(2L);
        when(source.history()).thenReturn(new CatalogHistory(4, 1));
        var v3 = new CatalogSnapshot(3, List.of(), List.of(), List.of());
        var v4 = new CatalogSnapshot(4, List.of(), List.of(), List.of());
        when(source.changes(3)).thenReturn(v3);
        when(source.changes(4)).thenReturn(v4);
        sync.synchronize(event);
        var order = inOrder(apply);
        order.verify(apply).apply(v3);
        order.verify(apply).applyAndComplete(v4, event);
    }

    @Test
    void duplicateDoesNotReadRedisOrRepeatWrites() {
        when(store.processed("event")).thenReturn(true);
        sync.synchronize(event);
        verifyNoInteractions(source, apply);
    }

    @Test
    void outdatedNotificationOnlyRegistersEvent() {
        when(store.appliedVersion()).thenReturn(5L);
        sync.synchronize(event);
        verify(apply).complete(event);
        verifyNoInteractions(source);
    }

    @Test
    void insufficientWindowDoesNotStartAtFirstRetainedVersion() {
        when(store.appliedVersion()).thenReturn(3L);
        when(source.history()).thenReturn(new CatalogHistory(7, 4));
        sync.synchronize(event);
        verify(rebuild).rebuild(7, Optional.of(event));
        verifyNoInteractions(apply);
    }

    @Test
    void publicationChangeDuringReadDoesNotApplyMixedData() {
        when(store.appliedVersion()).thenReturn(3L);
        when(source.history()).thenReturn(new CatalogHistory(4, 1), new CatalogHistory(5, 1));
        when(source.changes(4)).thenReturn(new CatalogSnapshot(4, List.of(), List.of(), List.of()));
        sync.synchronize(event);
        verify(rebuild).rebuild(4, Optional.of(event));
        verifyNoInteractions(apply);
    }

    @Test
    void failedVersionDoesNotMarkEventAndReplayContinuesFromPersistedProgress() {
        when(store.appliedVersion()).thenReturn(2L, 3L);
        when(source.history()).thenReturn(new CatalogHistory(4, 1));
        var v3 = new CatalogSnapshot(3, List.of(), List.of(), List.of());
        var v4 = new CatalogSnapshot(4, List.of(), List.of(), List.of());
        when(source.changes(3)).thenReturn(v3);
        when(source.changes(4)).thenReturn(v4);
        doThrow(new IllegalStateException("DB unavailable")).doNothing().when(apply).applyAndComplete(v4, event);

        assertThatThrownBy(() -> sync.synchronize(event)).isInstanceOf(IllegalStateException.class);
        sync.synchronize(event);

        verify(apply, times(1)).apply(v3);
        verify(apply, times(2)).applyAndComplete(v4, event);
        verify(apply, never()).complete(any());
    }

    @Test
    void periodicReconciliationRecoversWithoutCreatingAnEvent() {
        when(store.appliedVersion()).thenReturn(3L);
        when(source.history()).thenReturn(new CatalogHistory(4, 1));
        var v4 = new CatalogSnapshot(4, List.of(), List.of(), List.of());
        when(source.changes(4)).thenReturn(v4);
        sync.reconcile();
        verify(apply).apply(v4);
        verify(apply, never()).complete(any());
        verify(apply, never()).applyAndComplete(any(), any());
    }

    @Test
    void upToDateReconciliationDoesNotWriteOrDownloadSnapshot() {
        when(store.appliedVersion()).thenReturn(4L);
        when(source.history()).thenReturn(new CatalogHistory(4, 1));
        sync.reconcile();
        verifyNoInteractions(apply, rebuild, initialize);
    }

    @Test
    void initialReconciliationLoadsSnapshotBeforeComparingVersions() {
        when(snapshots.isInitialized()).thenReturn(false);
        when(store.appliedVersion()).thenReturn(4L);
        when(source.history()).thenReturn(new CatalogHistory(4, 1));
        sync.reconcile();
        var order = inOrder(initialize, source);
        order.verify(initialize).initializeIfEmpty();
        order.verify(source).history();
    }

    @Test
    void missingIncrementAndRemoteVersionRegressionRequireSnapshot() {
        when(store.appliedVersion()).thenReturn(3L);
        when(source.history()).thenReturn(new CatalogHistory(4, 1));
        when(source.changes(4)).thenThrow(new CatalogRebuildRequiredException("missing"));
        sync.reconcile();
        verify(rebuild).rebuild(4, Optional.empty());
        verifyNoInteractions(apply);

        when(store.appliedVersion()).thenReturn(5L);
        sync.reconcile();
        verify(rebuild).rebuild(5, Optional.empty());
    }

    @Test
    void outdatedSnapshotFailurePropagatesWithoutRecursiveDownloadsOrAcknowledgment() {
        when(store.appliedVersion()).thenReturn(3L);
        when(source.history()).thenReturn(new CatalogHistory(7, 4));
        doThrow(new CatalogRebuildRequiredException("stale snapshot")).when(rebuild).rebuild(7, Optional.of(event));
        assertThatThrownBy(() -> sync.synchronize(event)).isInstanceOf(CatalogRebuildRequiredException.class);
        verify(rebuild, times(1)).rebuild(7, Optional.of(event));
        verifyNoInteractions(apply);
    }

    @Test
    void kafkaWaitsForPeriodicReconciliationInsteadOfWritingConcurrently() throws Exception {
        var local = new AtomicLong(3);
        when(store.appliedVersion()).thenAnswer(invocation -> local.get());
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var kafkaThread = new AtomicReference<Thread>();
        when(source.history()).thenAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test synchronization timeout");
            }
            return new CatalogHistory(4, 1);
        });
        var data = new CatalogSnapshot(4, List.of(), List.of(), List.of());
        when(source.changes(4)).thenReturn(data);
        doAnswer(invocation -> { local.set(4); return null; }).when(apply).apply(data);

        try (var threads = Executors.newFixedThreadPool(2)) {
            var periodic = threads.submit(sync::reconcile);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var message = threads.submit(() -> {
                    kafkaThread.set(Thread.currentThread());
                    sync.synchronize(event);
                });
                await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                        assertThat(kafkaThread.get()).isNotNull().satisfies(thread ->
                                assertThat(thread.getState()).isEqualTo(Thread.State.BLOCKED)));
                verifyNoInteractions(apply);
                release.countDown();
                periodic.get(5, TimeUnit.SECONDS);
                message.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        verify(apply, times(1)).apply(data);
        verify(apply).complete(event);
        verify(apply, never()).applyAndComplete(any(), any());
    }
}
