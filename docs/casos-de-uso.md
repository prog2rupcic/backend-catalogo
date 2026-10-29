# Casos de uso — backend-catalogo

## Alcance y vocabulario

Este servicio es propietario de la copia local de categorías, profesionales, horarios semanales y versión aplicada. Busca en esa copia y expone la agenda vigente al servicio de turnos. No administra usuarios finales, holds ni reservas. Para consultar la agenda, turnos propaga el JWT HS256 del usuario con audiencia `backend-catalogo` y `ROLE_USER`.

**«Atiende el día»** (filtro de CAT-05) significa que el profesional atiende el día de la semana indicado. Se resuelve exclusivamente con la copia local; **no significa que tenga un turno libre en una fecha específica**. **«Turnos disponibles»** designa el resultado de combinar la agenda con las ocupaciones (`HELD` y `CONFIRMED`) del servicio central, y pertenece a `backend-turnos/`.

## Casos de uso

| ID | Caso de uso | Actor / disparador | Resultado esperado |
| --- | --- | --- | --- |
| CAT-01 | Inicializar catálogo con snapshot | Servicio al iniciar con base vacía | Obtiene el snapshot REST de la cátedra; aplica categorías, profesionales y horarios junto con `snapshotVersion` de forma consistente. |
| CAT-02 | Sincronizar cambios incrementales | Notificación Kafka `CatalogUpdated` o verificación de versiones | Deduplica por `eventId`, consulta en Redis versiones y cambios, lee las entidades afectadas y aplica cada versión en orden. Avanza la versión local y confirma el offset solo después de persistir. |
| CAT-03 | Reconstruir catálogo ante una discontinuidad | Versión faltante, fuera de la ventana de Redis o estado local no verificable | Solicita un snapshot REST y reemplaza la copia y su versión de forma consistente. |
| CAT-04 | Recuperar una notificación perdida | Arranque o comparación periódica de versiones | Detecta que la versión local está atrasada aunque no haya llegado Kafka y ejecuta sincronización incremental o completa según corresponda. |
| CAT-05 | Buscar y filtrar profesionales | Usuario autenticado desde Android | Busca sobre la copia local por categoría, nombre, estado habilitado y **atiende el día**, con paginación; no consulta ocupaciones ni reservas. Valida el JWT de usuario HS256 emitido por turnos, independiente del JWT técnico de cátedra. |
| CAT-06 | Consultar profesional y agenda vigente | `backend-turnos/` con JWT del usuario propagado | Devuelve el profesional y categoría con sus estados habilitados y solo horarios semanales habilitados de la copia local para construir disponibilidad o validar una nueva reserva. Distingue `404` inexistente de `200` deshabilitado y `503` catálogo no inicializado. |
| CAT-07 | Consultar estado de sincronización | Cliente autorizado / operación y diagnóstico | Informa versión aplicada, estado y errores relevantes sin exponer credenciales de integración. |

## Reglas compartidas

- Las bajas del catálogo son lógicas: se conservan las entidades deshabilitadas.
- Los eventos Kafka notifican cambios; Redis contiene incrementos y REST proporciona el snapshot. No se usan los eventos como fuente de entidades completas.
- Un evento duplicado no debe duplicar efectos; la versión local se actualiza únicamente cuando los datos correspondientes quedaron aplicados correctamente.
- Si la versión local es anterior a `oldest-available-version`, se recupera mediante snapshot, no intentando saltar versiones.
- La cuenta técnica de cátedra se registra una sola vez fuera de Android; este backend usa su configuración y credenciales externalizadas para integrarse, sin exponerlas a clientes.
