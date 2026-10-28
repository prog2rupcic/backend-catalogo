package ar.edu.turnos.catalogo.catalogo.domain.model;

public record CatalogNotification(String eventId, long newVersion) {
    public CatalogNotification {
        if (eventId == null || eventId.isBlank() || newVersion < 0) {
            throw new IllegalArgumentException("Notificacion de catalogo invalida");
        }
    }
}
