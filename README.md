# backend-catalogo

Servicio de catálogo y sincronización (Java 25, Spring Boot, PostgreSQL). Implementa **CAT-01** (snapshot inicial), **CAT-02** (incrementos Redis notificados por Kafka), **CAT-03** (reconstrucción), **CAT-04** (reconciliación al iniciar y periódica) y **CAT-05** (búsqueda local protegida). Las APIs de agenda y diagnóstico autorizado continúan en sus issues respectivos.

## Configuración

Se necesita Docker para ejecutar el servicio con PostgreSQL y para las pruebas con Testcontainers. Copiar `.env.example` a `.env` y reemplazar los valores de ejemplo por la configuración de integración vigente entregada por cátedra, además de una contraseña local de PostgreSQL. Si ya existe `.env`, agregar las variables Redis y Kafka de `.env.example` que falten. El archivo `.env` está excluido de Git y del contexto de construcción Docker.

| Variable | Uso |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base propia del catálogo en Compose. |
| `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` | Conexión de Spring a PostgreSQL. En Compose el host de la URL es `db`. |
| `CATALOG_USER_JWT_SECRET` | Clave HS256 compartida con `backend-turnos`, de al menos 32 bytes UTF-8. Obligatoria para iniciar el servicio; diferente del JWT técnico de cátedra. |
| `CATALOG_USER_JWT_ISSUER` | Emisor esperado del JWT de usuario; por defecto `backend-turnos`. |
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
En instalaciones existentes, agregar `CATALOG_USER_JWT_SECRET` al `.env` local antes de reconstruir la imagen (`openssl rand -hex 32` genera un valor apto). Configurar **el mismo valor** al implementar el emisor en `backend-turnos`; nunca publicarlo ni incluirlo en Android.

```bash
docker compose up --build
```

Compose levanta solo este servicio y su PostgreSQL; **REST, Redis y Kafka de cátedra son externos** y deben ser accesibles desde el contenedor (por ejemplo, con ZeroTier activo). Flyway crea el esquema al iniciar. Si la base no tiene versión aplicada, se solicita `GET /api/synchronization/snapshot` con el JWT técnico y se guarda el catálogo y `snapshotVersion` en una transacción. Las entidades deshabilitadas se conservan. En cada arranque se compara además la versión local con la publicada: reiniciar no descarga un snapshot innecesario si el catálogo ya está actualizado, pero sí recupera el atraso.

Si falla la primera carga, el servicio queda **no inicializado** (sin fila en `catalog_sync_state`). El indicador propio de inicialización marca `DOWN` y `/actuator/health` responde con estado general `DOWN`; el error se registra sin imprimir credenciales. Los ciclos posteriores pueden recuperar la carga sin reiniciar manualmente. La versión aplicada puede verificarse en `catalog_sync_state`.

La conexión Redis tiene un indicador propio (`catalogRedis`) que ejecuta únicamente `GET catedra:sync:current-version`: el indicador predeterminado de Spring está desactivado porque la ACL de cátedra no permite los comandos `PING`/`INFO`. El indicador propio marca `UP` si puede leer una versión decimal no negativa representable como `long`; marca `DOWN` ante una clave ausente, contenido inválido, fallo de autenticación, permisos o conectividad. No publica credenciales ni mensajes internos de excepciones. Es una comprobación de acceso al catálogo remoto, no una garantía de que la versión local esté actualizada.

`/actuator/health` combina este indicador con PostgreSQL y la inicialización local: puede quedar `DOWN` aunque el snapshot ya esté aplicado si falla Redis. No se exponen detalles adicionales del endpoint público. No hay una acción administrativa HTTP de sincronización.

### Búsqueda local de profesionales (CAT-05)

`GET /api/professionals` consulta únicamente PostgreSQL, sin solicitar datos a cátedra ni consultar ocupaciones. Requiere `Authorization: Bearer <jwt-de-usuario>` firmado con HS256 por `backend-turnos`, `iss=backend-turnos` (o el emisor configurado), `aud` que incluya `backend-catalogo`, `sub` no vacío, vencimiento vigente y `authorities` con `ROLE_USER`. Catálogo **solo valida**, no emite tokens. La clave de usuarios y el JWT técnico de cátedra son independientes. `/actuator/health` permanece público; los demás endpoints no publicados están denegados por defecto. Hasta implementar el login en turnos, el contrato se verifica con tokens ficticios en pruebas automatizadas.

Parámetros opcionales y combinables: `categoryId` (positivo), `name` (subcadena del nombre completo, sin distinguir mayúsculas, hasta 100 caracteres), `enabled` (por defecto `true`; usar `false` para consultar bajas lógicas), `dayOfWeek` (`MONDAY` a `SUNDAY`), `page` (desde 0, por defecto 0) y `size` (1 a 100, por defecto 20). El día exige al menos un horario semanal **habilitado** y no significa que haya turnos libres en una fecha. Los caracteres `%` y `_` en el nombre se tratan como texto literal. Los resultados se ordenan por apellido, nombre e ID de forma estable, sin duplicar profesionales con varios horarios.

La respuesta JSON contiene `content` (`id`, `categoryId`, `firstName`, `lastName`, `enabled`), `totalElements`, `page` y `size`. Una página fuera de rango devuelve una lista vacía. Parámetros inválidos devuelven `400`; falta o invalidez del token `401`; un token sin `ROLE_USER`, `403`; catálogo todavía no inicializado, `503`. El health puede estar `DOWN` por Redis aunque la búsqueda local, con una copia ya inicializada, siga disponible.

### Profesional y agenda vigente para turnos (CAT-06)

`GET /api/professionals/{id}` consulta solo la copia PostgreSQL y devuelve el profesional y su categoría (`id`, `name`, `enabled`), junto con `weeklySchedules` habilitados (`id`, `dayOfWeek`, `startTime`, `endTime`, `slotDurationMinutes`). El profesional incluye `id`, `firstName`, `lastName` y `enabled`. La agenda se ordena por día de lunes a domingo, hora de inicio e ID; las horas son locales y no incluyen ocupaciones ni disponibilidad real de una fecha. La lectura de profesional, categoría y horarios comparte una transacción de solo lectura con snapshot consistente.

`backend-turnos` propagará el JWT del usuario: se aplican las mismas exigencias HS256, `iss`, `aud=backend-catalogo`, `sub` y `ROLE_USER` que en CAT-05. Cuando turnos emita tokens para sus propios endpoints y catálogo, la audiencia deberá incluir `backend-catalogo`. Este servicio no emite JWT ni consulta las tablas de turnos.

Una entidad con `enabled=false` sigue existiendo: el endpoint devuelve `200` e informa ese estado tanto para el profesional como para la categoría; los horarios deshabilitados se omiten de la agenda vigente. Un profesional sin horarios habilitados devuelve `weeklySchedules: []`. Turnos debe rechazar nuevas reservas si el profesional o la categoría están deshabilitados o no hay un horario aplicable; `200` aquí no significa que se pueda reservar. Un ID inexistente devuelve `404`, uno no positivo `400`, catálogo aún no inicializado `503`, JWT ausente o inválido `401` y token sin `ROLE_USER` `403`. Una caída de Redis puede poner el health en `DOWN`, pero la lectura local inicializada sigue disponible; corresponde a turnos decidir cómo manejar la frescura de esos datos al iniciar una reserva.

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

Al reiniciar, la versión y las marcas persistidas permiten retomar el progreso, y se compara nuevamente con cátedra. No existe una acción administrativa HTTP para sincronizar. `catalog.bootstrap.enabled=false` desactiva la comprobación inicial, y `catalog.reconciliation.enabled=false` desactiva las siguientes; en producción se mantienen ambas activadas. El health actual no garantiza que Kafka esté ejecutándose o que la copia esté al día; para ello existe el diagnóstico autorizado.

## Diagnóstico de sincronización (CAT-07)

Flyway V3 crea `catalog_diagnostics`, una única fila de resumen durable. Se registra por separado cada intento de arranque, ciclo periódico y notificación Kafka (`attempt_id`, origen, actividad y fechas), la última versión remota **observada**, el último éxito y un código de error funcional. Los reintentos acotados de un ciclo se registran como intentos individuales; no se guarda un historial ilimitado. `applied_version` se obtiene siempre de `catalog_sync_state` mediante JOIN, no se duplica en el diagnóstico. La versión remota observada conserva su timestamp: un éxito antiguo no demuestra que hoy siga actualizado.

La actividad puede ser `IDLE`, `CHECKING`, `APPLYING` o `REBUILDING`; el último resultado puede ser `UNINITIALIZED`, `CURRENT`, `BEHIND`, `ERROR` o `INTERRUPTED`. Un error no borra la fecha del último éxito. Al arrancar, una actividad que quedó abierta por una caída se marca `INTERRUPTED`; el inicio de un nuevo intento no presenta el viejo como trabajo activo. Los eventos duplicados no implican cambios nuevos, aunque sí dejan constancia de una comprobación. No se guardan excepciones completas, payloads ni credenciales, y un fallo del propio diagnóstico no impide guardar un catálogo válido ni confirmar su procesamiento.

El estado Kafka se calcula **en la ejecución actual**, separado del resultado durable del catálogo: `DISABLED`, `WAITING` (incluye consumidor iniciado sin particiones), `CONSUMING` (particiones asignadas), `STOPPED_RETRYABLE` o `STOPPED_INVALID`. No se persiste `CONSUMING` entre reinicios. El registro del intento y su resultado se coordinan junto con Kafka y scheduler en una sola instancia, sin mantener el monitor durante el backoff. Las transacciones de diagnóstico son independientes de las escrituras de catálogo: si falla una escritura se conserva la evidencia del intento; si PostgreSQL está caído, solo los logs sin secretos podrán registrar el fallo. El health sigue siendo un indicador de dependencias, no de frescura o suscripción Kafka.

`GET /api/catalog/synchronization/status` expone la consulta `CatalogDiagnosticsUseCase.status()` **solo a JWT de usuario firmados por `backend-turnos` con `ROLE_ADMIN`**. Valida HS256, `iss`, `aud=backend-catalogo`, `sub` y expiración con las mismas reglas que las otras APIs; el JWT técnico de cátedra no autoriza este endpoint. Un `ROLE_USER` recibe `403`; sin JWT válido, `401`. `backend-turnos` aún no emite tokens administrativos: hasta implementarlo, la autorización se prueba con JWT ficticios aislados. La app de usuarios no accede a este diagnóstico.

La respuesta contiene `appliedVersion` (obtenida de `catalog_sync_state`), `remoteVersion` y `remoteObservedAt` (última lectura remota registrada), `attemptId`, `source`, `activity`, `outcome`, `startedAt`, `finishedAt`, `lastSuccessAt`, `lastErrorCode`, `lastErrorAt` y `kafka`. Los campos aún desconocidos son `null`, las fechas son instantes UTC y los errores son códigos funcionales; no se incluyen excepciones, payloads ni secretos. Con la base disponible, un catálogo todavía sin inicializar devuelve `200` con `appliedVersion=null` y `outcome=UNINITIALIZED`; si falla el acceso a PostgreSQL, devuelve `503` sin detalles internos. Una respuesta `CURRENT` describe la **última comprobación**, no garantiza que la cátedra siga en esa versión ahora: mirar `remoteObservedAt` y `lastSuccessAt` antes de interpretarla. Un error conserva la fecha del último éxito, y `lastErrorCode` puede representar un fallo histórico incluso tras un éxito posterior.

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
