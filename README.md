# backend-catalogo

Servicio de catálogo y sincronización (Java 25, Spring Boot, PostgreSQL). Actualmente implementa **CAT-01**: inicialización automática desde el snapshot REST de la cátedra cuando no existe una versión local aplicada. Redis y Kafka están configurados para la próxima etapa; aún no se procesan cambios incrementales.

## Configuración

Se necesita Docker para ejecutar el servicio con PostgreSQL y para las pruebas con Testcontainers. Copiar `.env.example` a `.env` y reemplazar los valores de ejemplo por la configuración de integración vigente entregada por cátedra, además de una contraseña local de PostgreSQL. Si ya existe `.env`, agregar las variables Redis y Kafka de `.env.example` que falten. El archivo `.env` está excluido de Git y del contexto de construcción Docker.

| Variable | Uso |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base propia del catálogo en Compose. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexión de Spring a PostgreSQL. En Compose el host de la URL es `db`. |
| `CATEDRA_REST_URL` | URL base REST de cátedra. |
| `CATEDRA_TECHNICAL_JWT` | JWT técnico de cátedra; nunca se envía a la app Android. |
| `REDIS_HOST`, `REDIS_PORT`, `REDIS_USERNAME`, `REDIS_PASSWORD` | Conexión ACL de solo lectura al catálogo Redis de cátedra. |
| `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_CONSUMER_GROUP_ID` | Conexión y grupo de consumidores asignados por cátedra. |
| `KAFKA_CATALOG_TOPIC` | Topic asignado para `CatalogUpdated`; no se suscribe todavía. |

Spring no carga `.env` por sí solo: `compose.yaml` inyecta esas variables con `env_file`. Para ejecutar Spring fuera de Compose, exportarlas al entorno y usar `jdbc:postgresql://localhost:5433/catalogo` como URL de base.

```bash
docker compose up --build
```

Compose levanta solo este servicio y su PostgreSQL; **REST, Redis y Kafka de cátedra son externos** y deben ser accesibles desde el contenedor (por ejemplo, con ZeroTier activo). Flyway crea el esquema al iniciar. Si la base no tiene versión aplicada, se solicita `GET /api/synchronization/snapshot` con el JWT técnico y se guarda el catálogo y `snapshotVersion` en una transacción. Las entidades deshabilitadas se conservan. Tras una carga correcta, un reinicio no vuelve a descargar el snapshot.

Si falla la primera carga, el servicio queda **no inicializado** (sin fila en `catalog_sync_state`). El indicador propio de inicialización marca `DOWN` y `/actuator/health` responde con estado general `DOWN`; el error se registra sin imprimir credenciales. Un nuevo arranque puede volver a intentar la carga. La versión aplicada puede verificarse en `catalog_sync_state`.

La conexión Redis ya está configurada y su indicador de salud está habilitado: `/actuator/health` también quedará `DOWN` si Redis no está accesible, aun cuando el snapshot ya esté aplicado. Kafka tiene bootstrap servers, consumer group y topic configurados, pero **no hay un consumidor ni se confirman offsets todavía**. La recuperación automática de cambios posteriores y de fallos transitorios se incorpora en CAT-02–CAT-04. Todavía no hay APIs de búsqueda ni una acción administrativa de sincronización.

## Organización

`config/CatedraIntegrationProperties` reúne los valores propios de la integración técnica utilizados por los adaptadores, sin registrar secretos en logs. Los adaptadores del slice se agrupan según su función en `catalogo/infrastructure/rest/`, `catalogo/infrastructure/persistence/jdbc/` y `catalogo/infrastructure/bootstrap/`. Los futuros lectores Redis y consumidores Kafka se ubicarán en `catalogo/infrastructure/redis/` y `catalogo/infrastructure/messaging/`; no se crean carpetas vacías. La conexión Redis y los parámetros estándar de Kafka usan las propiedades de autoconfiguración de Spring Boot.

## Pruebas

```bash
./mvnw test
```

Las pruebas usan PostgreSQL y Redis mediante Testcontainers y una respuesta REST de prueba; no necesitan acceso a cátedra ni secretos reales. Verifican el contrato del snapshot, la carga al iniciar, la conservación de bajas lógicas, el rollback, la conexión Redis y los valores de configuración Kafka sin iniciar un consumidor.

Casos de uso: [docs/casos-de-uso.md](docs/casos-de-uso.md).
