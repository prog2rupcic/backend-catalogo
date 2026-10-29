package ar.edu.turnos.catalogo.catalogo.application.usecases;

import ar.edu.turnos.catalogo.catalogo.domain.model.CatalogUnavailableException;
import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalAgenda;
import ar.edu.turnos.catalogo.catalogo.domain.model.ProfessionalNotFoundException;
import ar.edu.turnos.catalogo.catalogo.domain.ports.in.GetProfessionalAgendaUseCase;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.CatalogSnapshotStore;
import ar.edu.turnos.catalogo.catalogo.domain.ports.out.ProfessionalAgendaStore;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GetProfessionalAgendaUseCaseImpl implements GetProfessionalAgendaUseCase {
    private final CatalogSnapshotStore snapshots;
    private final ProfessionalAgendaStore agendas;

    public GetProfessionalAgendaUseCaseImpl(CatalogSnapshotStore snapshots, ProfessionalAgendaStore agendas) {
        this.snapshots = snapshots;
        this.agendas = agendas;
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProfessionalAgenda get(long id) {
        if (id <= 0) throw new IllegalArgumentException("Identificador de profesional invalido");
        if (!snapshots.isInitialized()) throw new CatalogUnavailableException();
        return agendas.findById(id).orElseThrow(ProfessionalNotFoundException::new);
    }
}
