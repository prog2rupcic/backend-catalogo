# backend-catalogo

Servicio de catálogo y sincronización (Java 25, Spring Boot, PostgreSQL). Implementa **CAT-01** (snapshot inicial), **CAT-02** (incrementos Redis notificados por Kafka), **CAT-03** (reconstrucción) y **CAT-04** (reconciliación al iniciar y periódica). Las APIs de búsqueda, agenda y diagnóstico autorizado continúan en sus issues respectivos.

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
| `CATALOG_RECONCILIATION_ENABLED` | Habilita el scheduler; por defecto `true`. No desactiva la reconciliación de arranque. |
| `CATALOG_RECONCILIATION_INTERVAL` | Espera entre ciclos periódicos, desde el final del anterior; por defecto `60s`, debe ser positiva. |
| `CATALOG_RECONCILIATION_MAX_ATTEMPTS` | Intentos por ciclo: de 1 a 5, por defecto 3. |
| `CATALOG_RECONCILIATION_RETRY_DELAY` | Espera inicial del backoff exponencial, positiva y hasta `10s`; por defecto `1s`. |

Spring no carga `.env` por sí solo: `compose.yaml` inyecta esas variables con `env_file`. Para ejecutar Spring fuera de Compose, exportarlas al entorno y usar `jdbc:postgresql://localhost:5433/catalogo` como URL de base.

```bash
docker compose up --build
```

Compose levanta solo este servicio y su PostgreSQL; **REST, Redis y Kafka de cátedra son externos** y deben ser accesibles desde el contenedor (por ejemplo, con ZeroTier activo). Flyway crea el esquema al iniciar. Si la base no tiene versión aplicada, se solicita `GET /api/synchronization/snapshot` con el JWT técnico y se guarda el catálogo y `snapshotVersion` en una transacción. Las entidades deshabilitadas se conservan. En cada arranque se compara además la versión local con la publicada: reiniciar no descarga un snapshot innecesario si el catálogo ya está actualizado, pero sí recupera el atraso.

Si falla la primera carga, el servicio queda **no inicializado** (sin fila en `catalog_sync_state`). El indicador propio de inicialización marca `DOWN` y `/actuator/health` responde con estado general `DOWN`; el error se registra sin imprimir credenciales. Los ciclos posteriores pueden recuperar la carga sin reiniciar manualmente. La versión aplicada puede verificarse en `catalog_sync_state`.

La conexión Redis tiene un indicador propio (`catalogRedis`) que ejecuta únicamente `GET catedra:sync:current-version`: el indicador predeterminado de Spring está desactivado porque la ACL de cátedra no permite los comandos `PING`/`INFO`. El indicador propio marca `UP` si puede leer una versión decimal no negativa representable como `long`; marca `DOWN` ante una clave ausente, contenido inválido, fallo de autenticación, permisos o conectividad. No publica credenciales ni mensajes internos de excepciones. Es una comprobación de acceso al catálogo remoto, no una garantía de que la versión local esté actualizada.

`/actuator/health` combina este indicador con PostgreSQL y la inicialización local: puede quedar `DOWN` aunque el snapshot ya esté aplicado si falla Redis. No se exponen detalles adicionales del endpoint público. Todavía no hay APIs de búsqueda ni una acción administrativa de sincronización.

## Sincronización incremental (CAT-02)

El consumidor se inicia después de una reconciliación exitosa, incluida la inicialización cuando sea necesaria. Usa el topic y consumer group asignados por cátedra, `enable.auto.commit=false` y confirmación manual inmediata. No crea topics. Para deshabilitarlo durante una prueba aislada puede utilizarse `CATALOG_KAFKA_ENABLED=false`.

1. Validar `CatalogUpdated` (`schemaVersion=1`) e ignorar campos adicionales compatibles.
2. Si `eventId` ya existe en `processed_catalog_event`, no repetir efectos.
3. Leer `catedra:sync:metadata`, los `changes:{version}` pendientes y las entidades vigentes de los tres hashes. Los incrementos contienen IDs, no entidades completas.
4. Comprobar que la metadata no haya cambiado durante la lectura. Aplicar las versiones en orden, con upserts y bajas lógicas; cada transacción guarda entidades y versión. La fila de versión se bloquea durante cada escritura.
5. Registrar el `eventId` en la transacción de la última versión del lote. Una notificación atrasada solo registra la marca, sin retroceder el catálogo. Si el proceso cae entre versiones, un replay continúa desde la última versión persistida.
6. Confirmar el offset después de finalizar correctamente. PostgreSQL y Kafka no forman una transacción distribuida: la marca durable y la aplicación idempotente hacen seguro el replay si falla el commit del offset.

El procesamiento requiere un historial continuo: por ejemplo, versión local `3` y `oldestAvailableVersion=4` requieren reconstrucción, no empezar en `changes:4`. Un incremento ausente, una entidad faltante, una incompatibilidad con las referencias locales o una publicación que cambia durante la lectura requieren recuperación, nunca saltar versiones.

## Reconstrucción y reconciliación (CAT-03 y CAT-04)

`SynchronizeCatalogUseCase` coordina tres disparadores: arranque, evento Kafka y scheduler. El mismo monitor serializa la inicialización, los incrementos y la reconstrucción en **una instancia**. No desplegar varias réplicas de este servicio con esta coordinación local.

- La reconciliación consulta la metadata Redis incluso sin eventos. Si las versiones coinciden no escribe; si hay historial continuo aplica incrementos; de lo contrario solicita un snapshot REST. La tarea periódica no inventa `eventId` ni marcas Kafka.
- El snapshot se descarga fuera de la transacción. Antes de reemplazar se valida y se exige que cubra, como mínimo, la versión local, la versión remota observada y la del evento que lo motivó. Si Redis retrocede, no se retrocede la copia: se intenta verificar mediante un snapshot que respete ese mínimo. Un snapshot obsoleto se rechaza y puede reintentarse más adelante.
- Categorías, profesionales, horarios y versión se reemplazan en una sola transacción, conservando el estado anterior si algo falla. Si la reconstrucción responde a Kafka, su marca de evento se guarda en la misma transacción; las marcas anteriores se conservan.
- Un ciclo usa por defecto hasta tres intentos con backoff de 1 y 2 segundos. Las esperas quedan fuera del monitor de sincronización y de las transacciones. Al agotar intentos, el siguiente ciclo periódico vuelve a comprobar el estado. El intervalo usa `fixedDelay` (60 segundos por defecto); los ciclos periódicos no se solapan.
- Un fallo de conexión/base, HTTP 408/429/5xx o snapshot temporalmente obsoleto es reintentable. Una entrada inválida o HTTP 4xx permanente no se reintenta dentro de ese ciclo. Los logs registran intento, resultado y tipo del error, sin payloads ni credenciales.
- Kafka confirma solamente al terminar correctamente. Ante un fallo recuperable se detiene sin confirmar el registro; después de una reconciliación exitosa se reanuda y hace replay desde el offset confirmado. La idempotencia permite que el catálogo ya esté actualizado cuando llegue ese replay.
- Un evento inválido bloquea la reanudación automática del consumidor en esa ejecución: no se descarta ni se confirma silenciosamente y no se crea un bucle de reinicios. Requiere diagnóstico/corrección operativa. La reconciliación periódica del catálogo puede continuar independientemente.

Al reiniciar, la versión y las marcas persistidas permiten retomar el progreso, y se compara nuevamente con cátedra. No existe una acción administrativa HTTP para sincronizar. `catalog.bootstrap.enabled=false` desactiva la comprobación inicial, y `catalog.reconciliation.enabled=false` desactiva las siguientes; en producción se mantienen ambas activadas. CAT-07 agregará el diagnóstico autorizado; el health actual no garantiza que Kafka esté ejecutándose o que la copia esté al día.

## Diagnóstico de sincronización (estado interno para CAT-07)

Flyway V3 crea `catalog_diagnostics`, una única fila de resumen durable. Se registra por separado cada intento de arranque, ciclo periódico y notificación Kafka (`attempt_id`, origen, actividad y fechas), la última versión remota **observada**, el último éxito y un código de error funcional. Los reintentos acotados de un ciclo se registran como intentos individuales; no se guarda un historial ilimitado. `applied_version` se obtiene siempre de `catalog_sync_state` mediante JOIN, no se duplica en el diagnóstico. La versión remota observada conserva su timestamp: un éxito antiguo no demuestra que hoy siga actualizado.

La actividad puede ser `IDLE`, `CHECKING`, `APPLYING` o `REBUILDING`; el último resultado puede ser `UNINITIALIZED`, `CURRENT`, `BEHIND`, `ERROR` o `INTERRUPTED`. Un error no borra la fecha del último éxito. Al arrancar, una actividad que quedó abierta por una caída se marca `INTERRUPTED`; el inicio de un nuevo intento no presenta el viejo como trabajo activo. Los eventos duplicados no implican cambios nuevos, aunque sí dejan constancia de una comprobación. No se guardan excepciones completas, payloads ni credenciales, y un fallo del propio diagnóstico no impide guardar un catálogo válido ni confirmar su procesamiento.

El estado Kafka se calcula **en la ejecución actual**, separado del resultado durable del catálogo: `DISABLED`, `WAITING` (incluye consumidor iniciado sin particiones), `CONSUMING` (particiones asignadas), `STOPPED_RETRYABLE` o `STOPPED_INVALID`. No se persiste `CONSUMING` entre reinicios. El registro del intento y su resultado se coordinan junto con Kafka y scheduler en una sola instancia, sin mantener el monitor durante el backoff. Las transacciones de diagnóstico son independientes de las escrituras de catálogo: si falla una escritura se conserva la evidencia del intento; si PostgreSQL está caído, solo los logs sin secretos podrán registrar el fallo. La consulta está disponible mediante `CatalogDiagnosticsUseCase.status()` para una futura API autenticada; **no hay endpoint HTTP de diagnóstico en esta etapa**. El health sigue siendo un indicador de dependencias, no de frescura o suscripción Kafka.

## Organización

`config/CatedraIntegrationProperties` reúne los valores propios de la integración técnica; `config/CatalogKafkaConfiguration` define la política de errores Kafka. Los adaptadores del slice están en `catalogo/infrastructure/rest/`, `persistence/jdbc/`, `bootstrap/`, `redis/` y `messaging/`. Las reglas de validez e historial viven en dominio y los casos de uso orquestan lecturas y transacciones mediante puertos. La conexión Redis y los parámetros estándar de Kafka usan las propiedades de autoconfiguración de Spring Boot.

## Pruebas

Requisitos: JDK 25 y Docker en ejecución, accesible por el usuario que ejecuta Maven.

```bash
./mvnw --batch-mode --no-transfer-progress verify
```

Las pruebas usan PostgreSQL y Redis mediante Testcontainers, Kafka embebido solo para tests y fuentes remotas ficticias; no necesitan cátedra, ZeroTier ni secretos reales. Cubren snapshot, ACL restringida, incrementos, rollback de datos/versión/evento, orden, duplicados, reconstrucción y rechazo de snapshots obsoletos. Un scheduler real recupera una carga inicial fallida y detecta cambios sin Kafka; también se verifica exclusión mutua, intentos limitados e interrupción del backoff. Kafka prueba replay tras recuperación y que un mensaje inválido no provoque reinicios automáticos. Un coordinador nuevo sobre la persistencia existente verifica la reanudación del progreso.

El diagnóstico se prueba sobre PostgreSQL: conservación del último éxito tras un fallo, rechazo de resultados de intentos antiguos, detección de un intento interrumpido, códigos de error sin secretos y lectura independiente del estado Kafka. El reloj puede sustituirse en pruebas; no se requieren secretos ni la instancia central para ejecutar `verify`.

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
