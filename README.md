# backend-catalogo

Servicio de catálogo y sincronización (Java 25, Spring Boot, PostgreSQL). Implementa **CAT-01** (snapshot inicial) y **CAT-02** (incrementos Redis notificados por Kafka). La reconstrucción automática y la detección periódica de notificaciones perdidas se incorporarán en CAT-03 y CAT-04.

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
| `KAFKA_CATALOG_TOPIC` | Topic asignado para `CatalogUpdated`. |

Spring no carga `.env` por sí solo: `compose.yaml` inyecta esas variables con `env_file`. Para ejecutar Spring fuera de Compose, exportarlas al entorno y usar `jdbc:postgresql://localhost:5433/catalogo` como URL de base.

```bash
docker compose up --build
```

Compose levanta solo este servicio y su PostgreSQL; **REST, Redis y Kafka de cátedra son externos** y deben ser accesibles desde el contenedor (por ejemplo, con ZeroTier activo). Flyway crea el esquema al iniciar. Si la base no tiene versión aplicada, se solicita `GET /api/synchronization/snapshot` con el JWT técnico y se guarda el catálogo y `snapshotVersion` en una transacción. Las entidades deshabilitadas se conservan. Tras una carga correcta, un reinicio no vuelve a descargar el snapshot.

Si falla la primera carga, el servicio queda **no inicializado** (sin fila en `catalog_sync_state`). El indicador propio de inicialización marca `DOWN` y `/actuator/health` responde con estado general `DOWN`; el error se registra sin imprimir credenciales. Un nuevo arranque puede volver a intentar la carga. La versión aplicada puede verificarse en `catalog_sync_state`.

La conexión Redis tiene un indicador propio (`catalogRedis`) que ejecuta únicamente `GET catedra:sync:current-version`: el indicador predeterminado de Spring está desactivado porque la ACL de cátedra no permite los comandos `PING`/`INFO`. El indicador propio marca `UP` si puede leer una versión decimal no negativa representable como `long`; marca `DOWN` ante una clave ausente, contenido inválido, fallo de autenticación, permisos o conectividad. No publica credenciales ni mensajes internos de excepciones. Es una comprobación de acceso al catálogo remoto, no una garantía de que la versión local esté actualizada.

`/actuator/health` combina este indicador con PostgreSQL y la inicialización local: puede quedar `DOWN` aunque el snapshot ya esté aplicado si falla Redis. No se exponen detalles adicionales del endpoint público. Todavía no hay APIs de búsqueda ni una acción administrativa de sincronización.

## Sincronización incremental (CAT-02)

El consumidor se inicia después del arranque y solo si existe snapshot aplicado. Usa el topic y consumer group asignados por cátedra, `enable.auto.commit=false` y confirmación manual inmediata. No crea topics. Para deshabilitarlo durante una prueba aislada puede utilizarse `CATALOG_KAFKA_ENABLED=false`.

1. Validar `CatalogUpdated` (`schemaVersion=1`) e ignorar campos adicionales compatibles.
2. Si `eventId` ya existe en `processed_catalog_event`, no repetir efectos.
3. Leer `catedra:sync:metadata`, los `changes:{version}` pendientes y las entidades vigentes de los tres hashes. Los incrementos contienen IDs, no entidades completas.
4. Comprobar que la metadata no haya cambiado durante la lectura. Aplicar las versiones en orden, con upserts y bajas lógicas; cada transacción guarda entidades y versión. La fila de versión se bloquea durante cada escritura.
5. Registrar el `eventId` en la transacción de la última versión del lote. Una notificación atrasada solo registra la marca, sin retroceder el catálogo. Si el proceso cae entre versiones, un replay continúa desde la última versión persistida.
6. Confirmar el offset después de finalizar correctamente. PostgreSQL y Kafka no forman una transacción distribuida: la marca durable y la aplicación idempotente hacen seguro el replay si falla el commit del offset.

La coordinación actual serializa las sincronizaciones en una instancia; no se debe desplegar más de una réplica durante esta etapa. El procesamiento requiere un historial continuo: por ejemplo, versión local `3` y `oldestAvailableVersion=4` requieren reconstrucción, no empezar en `changes:4`. Un incremento ausente, una entidad faltante o una publicación que cambia durante la lectura se señalan como `CatalogRebuildRequiredException` sin avanzar saltando versiones.

**Límite temporal de CAT-02:** ante un error de lectura/escritura, evento inválido o necesidad de reconstrucción, el error handler detiene el consumidor sin confirmar el registro fallido. No hay ciclos ilimitados de reintento ni descarte automático. Revisar logs y reiniciar el servicio después de resolver el fallo permite volver a procesar desde el offset confirmado. Si el problema es una discontinuidad, reiniciar no la repara: CAT-03 implementará el snapshot de reconstrucción. CAT-04 añadirá reconciliación al inicio y periódica; CAT-07 expondrá el estado del proceso. El health actual verifica dependencias y snapshot inicial, no garantiza que el consumidor siga ejecutándose ni que el catálogo esté actualizado.

## Organización

`config/CatedraIntegrationProperties` reúne los valores propios de la integración técnica; `config/CatalogKafkaConfiguration` define la política de errores Kafka. Los adaptadores del slice están en `catalogo/infrastructure/rest/`, `persistence/jdbc/`, `bootstrap/`, `redis/` y `messaging/`. Las reglas de validez e historial viven en dominio y los casos de uso orquestan lecturas y transacciones mediante puertos. La conexión Redis y los parámetros estándar de Kafka usan las propiedades de autoconfiguración de Spring Boot.

## Pruebas

Requisitos: JDK 25 y Docker en ejecución, accesible por el usuario que ejecuta Maven.

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Las pruebas usan PostgreSQL y Redis mediante Testcontainers, Kafka embebido solo para tests y una respuesta REST ficticia; no necesitan cátedra, ZeroTier ni secretos reales. Cubren snapshot, health con ACL restringida, lectura de cambios Redis, upserts, rollback de datos/versión/evento, orden, duplicados, historial insuficiente y confirmación posterior al caso de uso. La prueba Kafka entrega mensajes repetidos al consumidor real y verifica una sola marca persistida.

### Cobertura JaCoCo

`verify` ejecuta las pruebas, empaqueta la aplicación y genera `target/site/jacoco/index.html` (reporte visual) y `target/site/jacoco/jacoco.xml` (reporte para analizadores). JaCoCo mide líneas y ramas ejecutadas sin excluir código propio ni imponer por ahora un porcentaje mínimo. Los informes y `target/jacoco.exec` son resultados de build y no se versionan. Para una comprobación desde cero se puede ejecutar `./mvnw clean verify`. `./mvnw test` sigue siendo útil para iterar, pero el informe se genera en la fase `verify`.

### Integración continua y revisión

`.github/workflows/backend-ci.yml` ejecuta el mismo `verify` con Temurin 25 y Docker en Ubuntu ante cada push a cualquier rama, ante cambios en PRs hacia `develop`, `test` o `main`, y manualmente desde Actions. No necesita `.env`, ZeroTier ni secretos de cátedra. La caché almacena dependencias Maven, no los resultados de las pruebas. Los informes Surefire y JaCoCo se publican como artefactos por 14 días; si el build falla antes de generarlos, pueden no estar disponibles y el diagnóstico se consulta en los logs. Un fallo de tests mantiene el check en rojo, aunque se publiquen los informes.

Flujo recomendado:

1. Ejecutar `verify` localmente y corregir los fallos.
2. Subir la rama del issue y esperar el check **Java 25 - Maven verify** en Actions.
3. Si falla, corregir en esa misma rama y subir otro commit.
4. Cuando esté verde, abrir el PR hacia `develop` y solicitar revisión. El PR vuelve a verificar el resultado combinado con la rama base.
5. Resolver observaciones en la misma rama y fusionar cuando tenga aprobación y checks correctos.

Las verificaciones push y PR son intencionales: la primera valida la rama antes de pedir revisión y la segunda valida su integración. Configurar ese check como obligatorio antes del merge requiere una regla de protección/ruleset en GitHub; el YAML no modifica esas reglas ni las aprobaciones del profesor. La promoción de hitos validados sigue `develop → test → main`. La prueba manual de Compose contra cátedra es adicional al CI aislado.

Casos de uso: [docs/casos-de-uso.md](docs/casos-de-uso.md).
