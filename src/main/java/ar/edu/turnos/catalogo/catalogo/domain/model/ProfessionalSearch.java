package ar.edu.turnos.catalogo.catalogo.domain.model;

import java.time.DayOfWeek;
import java.util.List;

public final class ProfessionalSearch {
    private ProfessionalSearch() { }

    public record Filter(Long categoryId, String name, Boolean enabled, DayOfWeek dayOfWeek, int page, int size) {
        public Filter {
            if (categoryId != null && categoryId <= 0) throw new IllegalArgumentException("Categoria invalida");
            if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Paginacion invalida");
            if (name != null) {
                name = name.trim();
                if (name.length() > 100) throw new IllegalArgumentException("Nombre demasiado largo");
                if (name.isEmpty()) name = null;
            }
        }
    }

    public record Professional(long id, long categoryId, String firstName, String lastName, boolean enabled) { }

    public record Page(List<Professional> content, long totalElements, int page, int size) {
        public Page {
            content = List.copyOf(content);
        }
    }
}
