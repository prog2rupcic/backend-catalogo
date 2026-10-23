package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.*;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.ApplyCatalogIncrementUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogChangesSource;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogIncrementStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SynchronizeCatalogTests {
    final CatalogChangesSource source = mock(CatalogChangesSource.class);
    final CatalogIncrementStore store = mock(CatalogIncrementStore.class);
    final ApplyCatalogIncrementUseCase apply = mock(ApplyCatalogIncrementUseCase.class);
    final SynchronizeCatalogUseCaseImpl sync = new SynchronizeCatalogUseCaseImpl(source, store, apply);
    final CatalogNotification event = new CatalogNotification("event", 4);

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
        assertThatThrownBy(() -> sync.synchronize(event)).isInstanceOf(CatalogRebuildRequiredException.class);
        verifyNoInteractions(apply);
    }

    @Test
    void publicationChangeDuringReadDoesNotApplyMixedData() {
        when(store.appliedVersion()).thenReturn(3L);
        when(source.history()).thenReturn(new CatalogHistory(4, 1), new CatalogHistory(5, 1));
        when(source.changes(4)).thenReturn(new CatalogSnapshot(4, List.of(), List.of(), List.of()));
        assertThatThrownBy(() -> sync.synchronize(event)).isInstanceOf(CatalogRebuildRequiredException.class);
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
}
