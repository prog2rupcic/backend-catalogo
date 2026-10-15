# backend-catalogo

Servicio de catálogo y sincronización (Java 25, Spring Boot, PostgreSQL). Esta etapa implementa **CAT-01**: inicialización automática desde el snapshot REST de la cátedra cuando no existe una versión local aplicada.

## Configuración

Se necesita Docker para ejecutar el servicio con PostgreSQL y para las pruebas con Testcontainers. Copiar `.env.example` a `.env` y reemplazar los valores de ejemplo por la URL y el JWT técnico entregados por cátedra, además de una contraseña local de PostgreSQL. El archivo `.env` está excluido de Git y del contexto de construcción Docker.

| Variable | Uso |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base propia del catálogo en Compose. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexión de Spring a PostgreSQL. En Compose el host de la URL es `db`. |
| `CATEDRA_REST_URL` | URL base REST de cátedra. |
| `CATEDRA_TECHNICAL_JWT` | JWT técnico de cátedra; nunca se envía a la app Android. |

Spring no carga `.env` por sí solo: `compose.yaml` inyecta esas variables con `env_file`. Para ejecutar Spring fuera de Compose, exportarlas al entorno y usar `jdbc:postgresql://localhost:5433/catalogo` como URL de base.

```bash
docker compose up --build
```

Compose levanta solo este servicio y su PostgreSQL; **la instancia central de cátedra es externa**. Flyway crea el esquema al iniciar. Si la base no tiene versión aplicada, se solicita `GET /api/synchronization/snapshot` con el JWT técnico y se guarda el catálogo y `snapshotVersion` en una transacción. Las entidades deshabilitadas se conservan. Tras una carga correcta, un reinicio no vuelve a descargar el snapshot.

Si falla la primera carga, el servicio queda **no inicializado** (sin fila en `catalog_sync_state`). El indicador propio de inicialización marca `DOWN` y `/actuator/health` responde con estado general `DOWN`; el error se registra sin imprimir credenciales. Un nuevo arranque puede volver a intentar la carga. La versión aplicada puede verificarse en `catalog_sync_state`.

En CAT-01 Redis todavía no se utiliza: su indicador de salud automático está desactivado para que no señale como fallida una carga correcta por intentar conectarse a `localhost:6379`. Se configurará y habilitará al implementar la sincronización incremental (CAT-02). La recuperación automática de cambios posteriores y de fallos transitorios se incorpora en CAT-02–CAT-04. Todavía no hay APIs de búsqueda ni una acción administrativa de sincronización.

## Pruebas

```bash
./mvnw test
```

Las pruebas usan PostgreSQL mediante Testcontainers y una respuesta REST de prueba; no necesitan acceso a cátedra ni secretos reales. Verifican el contrato del snapshot, la carga al iniciar, la conservación de bajas lógicas y el rollback si la escritura falla.

Casos de uso: [docs/casos-de-uso.md](docs/casos-de-uso.md).
