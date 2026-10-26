package ar.edu.turnos.catalogo.catalogo.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogHistoryTests {
    @Test
    void acceptsContinuousWindowIncludingItsLowerBoundary() {
        assertThatCode(() -> new CatalogHistory(7, 4).requireContinuousHistory(4, 7)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingHistoryAndImpossibleMetadata() {
        assertThatThrownBy(() -> new CatalogHistory(7, 4).requireContinuousHistory(3, 7))
                .isInstanceOf(CatalogRebuildRequiredException.class);
        assertThatThrownBy(() -> new CatalogHistory(7, 4).requireContinuousHistory(8, 7))
                .isInstanceOf(CatalogRebuildRequiredException.class);
        assertThatThrownBy(() -> new CatalogHistory(7, 4).requireContinuousHistory(4, 8))
                .isInstanceOf(CatalogRebuildRequiredException.class);
        assertThatThrownBy(() -> new CatalogHistory(4, 7)).isInstanceOf(CatalogRebuildRequiredException.class);
    }
}
