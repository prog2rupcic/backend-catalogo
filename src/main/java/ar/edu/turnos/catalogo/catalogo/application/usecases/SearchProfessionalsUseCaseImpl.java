package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalSearch;
import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogUnavailableException;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.SearchProfessionalsUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.ProfessionalSearchStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SearchProfessionalsUseCaseImpl implements SearchProfessionalsUseCase {
    private final ProfessionalSearchStore store;
    private final CatalogSnapshotStore snapshot;

    public SearchProfessionalsUseCaseImpl(ProfessionalSearchStore store, CatalogSnapshotStore snapshot) {
        this.store = store;
        this.snapshot = snapshot;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProfessionalSearch.Page search(ProfessionalSearch.Filter filter) {
        if (!snapshot.isInitialized()) throw new CatalogUnavailableException();
        return store.search(filter);
    }
}
