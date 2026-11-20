<!-- evaluacion:inicio -->
# Evaluación — prog2rupcic/backend-catalogo

> Documento **generado automáticamente** por la cátedra de Programación II (UM): RepoSense + análisis de calidad de **7 dimensiones**. **No editar a mano**: se regenera en cada evaluación.
> Rama evaluada: `main`. Análisis **heurístico** (regex + métricas) + build: es una criba para orientar la revisión, **no una nota**. La **documentación** del repositorio no se evalúa.
> Generado: 2026-11-20.

## 1. Veredicto

| Aspecto | Resultado |
|---|---|
| Índice de calidad | **61/100** |
| Tipo de repositorio | `backend` |
| Build (`mvnw verify` / `gradlew check`) | OK |
| Código (LOC) | 3,357 |
| Archivos de código | 77 |
| Clases | 106 |
| Código dentro de clases (%POO) | 80% |
| Funciones fuera de clases | 0 |
| Duplicación (puerta) | 0.9% |
| Duplicación lógica | 0.9% |
| Duplicación de boilerplate | 3.8% |
| Cobertura | 95% |
| Tope duro aplicado a D1 | `POO_BAJO` → D1 ≤ 60 |

> Este informe **describe** lo que el análisis midió con los criterios ya establecidos de la materia. No es una nota.

## 2. Semáforo de aprobación

Criterios de la cátedra (`CRITERIOS-CALIDAD.md`): **Aprobar** se juega con el perfil `estandar`; **destacar**, con el `exigente` (meta de excelencia).

| Semáforo | Resultado |
|---|---|
| Aprobar (`estandar`) | **no cumple** — 7/11 |
| Destacar (`exigente`) | **no cumple** — 5/11 |

**Para aprobar, falta cumplir:** Tests

**Para destacar, falta cumplir:** SOLID; POO; Tests

## 3. Autoría (RepoSense)

| Autor | Commits | Código (LOC) | Tests (LOC) | Docs (LOC) | Días activos | % del código |
|---|---:|---:|---:|---:|---:|---:|
| Juan Cruz Rupcic | 19 | 1,961 | 1,828 | 159 | 14 | 100% |

> _El LOC de autoría lo calcula RepoSense sobre el historial y puede diferir del LOC de código del análisis (que mira el estado de `main`)._

## 4. Dimensiones evaluadas

Cada dimensión indica **qué mide**, **por qué importa** y **tu resultado**. Cuando el puntaje queda por debajo de la referencia, se agregan preguntas para **investigar la causa** (no hay recetas).

| Dimensión | Peso | Puntaje |
|:--|--:|--:|
| **D1** Diseño OO, SOLID y patrones (20%) | 20% | 5 |
| **D2** Arquitectura hexagonal — backend (15%) | 15% | 100 |
| **D3** Frontend KMP: MVVM y buenas prácticas (10%) | 10% | N/A |
| **D4** Integración y contrato (10%) | 10% | 60 |
| **D5** Seguridad y robustez (10%) | 10% | 100 |
| **D6** Pruebas y cobertura (20%) | 20% | 40 |
| **D7** Proceso y entrega (15%) | 15% | 100 |

### D1. Diseño OO, SOLID y patrones (20%)

- **Qué mide:** Métricas orientadas a objetos (clases, métodos, campos, cohesión, acoplamiento, herencia) y proxies de SOLID, más patrones de diseño.
- **Por qué importa:** El eje de la materia es el buen uso de la POO y su arquitectura: un diseño con clases claras y poco acopladas es más fácil de entender, probar y cambiar.
- **Tu resultado:** **5/100**
- **Para investigar la causa:**
    - ¿Se pensó el modelo de clases antes de codificar o se agrupó después?
    - ¿Qué clases concentran muchas responsabilidades a la vez?
    - ¿Los colaboradores llegan como parámetros o se crean por dentro?

### D2. Arquitectura hexagonal — backend (15%)

- **Qué mide:** Separación en capas (dominio, puertos de entrada/salida, adaptadores) y dirección de las dependencias del backend.
- **Por qué importa:** Aislar el dominio de la infraestructura permite cambiar la base de datos, el framework o el transporte sin tocar las reglas de negocio.
- **Tu resultado:** **100/100**

### D4. Integración y contrato (10%)

- **Qué mide:** Cumplimiento del contrato de integración de la cátedra (formato de errores, paginación, autenticación, endpoints esperados).
- **Por qué importa:** Los servicios deben poder integrarse entre sí y con la app sin sorpresas; el contrato es lo que hacen cumplir las pruebas de integración.
- **Tu resultado:** **60/100**

### D5. Seguridad y robustez (10%)

- **Qué mide:** Credenciales embebidas, consultas SQL por concatenación, y presencia de config de entorno; aislamiento de secretos.
- **Por qué importa:** Un secreto en el repositorio o una consulta construida por concatenación son riesgos concretos, no hipotéticos.
- **Tu resultado:** **100/100**

### D6. Pruebas y cobertura (20%)

- **Qué mide:** Cantidad y resultado de pruebas y cobertura de línea (JaCoCo/Kover, Surefire/Failsafe) cuando hay build.
- **Por qué importa:** Las pruebas son la red de seguridad para refactorizar e integrar; la cobertura acota las zonas sin verificar.
- **Tu resultado:** **40/100**
- **Para investigar la causa:**
    - ¿Qué partes del sistema no tienen pruebas?
    - ¿Las pruebas cubren caminos de error o solo el caso feliz?
    - ¿Qué falla al ejecutar la suite completa (no solo la del módulo propio)?

### D7. Proceso y entrega (15%)

- **Qué mide:** Artefactos verificables de proceso/entrega: `compose`, `.env.example`, CI (`.github/workflows/`), `CODEOWNERS` y wrapper de build (`mvnw`/`gradlew`). **No** puntúa documentación.
- **Por qué importa:** Un proyecto que compila, se levanta y se valida solo es más fácil de revisar y de integrar en equipo.
- **Tu resultado:** **100/100**

## 5. Señales detectadas

El análisis encontró las siguientes señales, con el archivo y la línea donde se originan cuando están disponibles:

**Archivos con más señales**

| Archivo | Señales |
|---|---:|
| `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java` | 9 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogReconciliationJob.java` | 7 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogSnapshot.java` | 6 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalSearch.java` | 6 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/SynchronizeCatalogUseCaseImpl.java` | 4 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/redis/CatalogChangesRedisAdapter.java` | 3 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/CatalogSnapshotJdbcAdapter.java` | 3 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/ProfessionalAgendaJdbcAdapter.java` | 3 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/dto/ProfessionalAgendaResponse.java` | 3 |
| `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalAgenda.java` | 3 |

**DIP/concretos** (29)
- clase 'ApplyCatalogIncrementUseCaseImpl' muy acoplada (CBO=8) — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/ApplyCatalogIncrementUseCaseImpl.java:13`
- clase 'ApplyCatalogSnapshotUseCaseImpl' muy acoplada (CBO=8) — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/ApplyCatalogSnapshotUseCaseImpl.java:18`
- clase 'CatalogDiagnosticsUseCaseImpl' muy acoplada (CBO=14) — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/CatalogDiagnosticsUseCaseImpl.java:15`
- clase 'GetProfessionalAgendaUseCaseImpl' muy acoplada (CBO=10) — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/GetProfessionalAgendaUseCaseImpl.java:14`
- clase 'RebuildCatalogUseCaseImpl' muy acoplada (CBO=8) — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/RebuildCatalogUseCaseImpl.java:12`
- clase 'SearchProfessionalsUseCaseImpl' muy acoplada (CBO=9) — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/SearchProfessionalsUseCaseImpl.java:13`
- clase 'SynchronizeCatalogUseCaseImpl' muy acoplada (CBO=25) — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/SynchronizeCatalogUseCaseImpl.java:20`
- clase 'CatalogDiagnostic' muy acoplada (CBO=24) — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogDiagnostic.java:7`
- clase 'CatalogValidation' muy acoplada (CBO=9) — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogValidation.java:7`
- clase 'CatalogValidation' instancia 4 concretos (CBO=9) — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogValidation.java:7`
- clase 'CatalogDiagnosticsRecorder' muy acoplada (CBO=22) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogDiagnosticsRecorder.java:13`
- clase 'CatalogFailureClassifier' muy acoplada (CBO=10) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogFailureClassifier.java:14`
- clase 'CatalogReconciliationJob' muy acoplada (CBO=20) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogReconciliationJob.java:21`
- clase 'CatalogKafkaLifecycle' muy acoplada (CBO=9) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/messaging/CatalogKafkaLifecycle.java:12`
- clase 'CatalogUpdatedKafkaListener' muy acoplada (CBO=16) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/messaging/CatalogUpdatedKafkaListener.java:18`
- clase 'CatalogDiagnosticJdbcAdapter' muy acoplada (CBO=39) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/CatalogDiagnosticJdbcAdapter.java:14`
- clase 'CatalogSnapshotJdbcAdapter' muy acoplada (CBO=31) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/CatalogSnapshotJdbcAdapter.java:14`
- clase 'ProfessionalAgendaJdbcAdapter' muy acoplada (CBO=26) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/ProfessionalAgendaJdbcAdapter.java:11`
- clase 'ProfessionalSearchJdbcAdapter' muy acoplada (CBO=29) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/ProfessionalSearchJdbcAdapter.java:12`
- clase 'CatalogChangesRedisAdapter' muy acoplada (CBO=22) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/redis/CatalogChangesRedisAdapter.java:15`
- clase 'CatalogChangesRedisAdapter' instancia 9 concretos (CBO=22) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/redis/CatalogChangesRedisAdapter.java:15`
- clase 'CatalogRedisHealthIndicator' muy acoplada (CBO=9) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/redis/CatalogRedisHealthIndicator.java:10`
- clase 'CatedraSnapshotRestAdapter' muy acoplada (CBO=19) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:18`
- clase 'CatedraSnapshotRestAdapter' instancia 4 concretos (CBO=19) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:18`
- clase 'ProfessionalAgendaController' muy acoplada (CBO=8) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/controller/ProfessionalAgendaController.java:17`
- clase 'ProfessionalSearchController' muy acoplada (CBO=10) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/controller/ProfessionalSearchController.java:18`
- clase 'CatalogDiagnosticsResponse' muy acoplada (CBO=8) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/dto/CatalogDiagnosticsResponse.java:7`
- clase 'CatalogSecurityConfiguration' muy acoplada (CBO=23) — `src/main/java/ar/edu/turnos/catalogo/config/CatalogSecurityConfiguration.java:24`
- clase 'CatalogSecurityConfiguration' instancia 4 concretos (CBO=23) — `src/main/java/ar/edu/turnos/catalogo/config/CatalogSecurityConfiguration.java:24`

**SRP/responsabilidad** (45)
- método 'SynchronizeCatalogUseCaseImpl.SynchronizeCatalogUseCaseImpl' con 7 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/SynchronizeCatalogUseCaseImpl.java:29`
- método 'SynchronizeCatalogUseCaseImpl.reconcileInternal' de 40 líneas — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/SynchronizeCatalogUseCaseImpl.java:70`
- método 'SynchronizeCatalogUseCaseImpl.reconcileInternal' con complejidad 8 — `src/main/java/ar/edu/turnos/catalogo/catalogo/application/usecases/SynchronizeCatalogUseCaseImpl.java:70`
- método 'CatalogDiagnostic.CatalogDiagnostic' con 13 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogDiagnostic.java:7`
- método 'CatalogSnapshot.Category' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogSnapshot.java:14`
- método 'Category.Category' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogSnapshot.java:14`
- método 'CatalogSnapshot.Professional' con 7 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogSnapshot.java:18`
- método 'Professional.Professional' con 7 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogSnapshot.java:18`
- método 'CatalogSnapshot.WeeklySchedule' con 9 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogSnapshot.java:22`
- método 'WeeklySchedule.WeeklySchedule' con 9 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/CatalogSnapshot.java:22`
- método 'ProfessionalAgenda.ProfessionalAgenda' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalAgenda.java:7`
- método 'ProfessionalAgenda.WeeklySchedule' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalAgenda.java:15`
- método 'WeeklySchedule.WeeklySchedule' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalAgenda.java:15`
- método 'ProfessionalSearch.Filter' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalSearch.java:9`
- método 'ProfessionalSearch.Filter' con complejidad 6 — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalSearch.java:9`
- método 'Filter.Filter' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalSearch.java:9`
- método 'Filter.Filter' con complejidad 6 — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalSearch.java:9`
- método 'ProfessionalSearch.Professional' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalSearch.java:21`
- método 'Professional.Professional' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/domain/model/ProfessionalSearch.java:21`
- clase 'CatalogReconciliationJob' grande (LOC=87, métodos=6, campos=10) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogReconciliationJob.java:21`
- clase 'CatalogReconciliationJob' con baja cohesión (47%) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogReconciliationJob.java:21`
- método 'CatalogReconciliationJob.CatalogReconciliationJob' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogReconciliationJob.java:34`
- método 'CatalogReconciliationJob.CatalogReconciliationJob' con 7 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogReconciliationJob.java:41`
- método 'CatalogReconciliationJob.runCycle' de 35 líneas — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogReconciliationJob.java:74`
- método 'CatalogReconciliationJob.runCycle' con complejidad 6 — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogReconciliationJob.java:74`
- clase 'CatalogUpdatedKafkaListener' con baja cohesión (17%) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/messaging/CatalogUpdatedKafkaListener.java:18`
- clase 'CatalogDiagnosticJdbcAdapter' grande (LOC=68, métodos=11, campos=1) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/CatalogDiagnosticJdbcAdapter.java:14`
- clase 'CatalogSnapshotJdbcAdapter' grande (LOC=71, métodos=10, campos=3) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/CatalogSnapshotJdbcAdapter.java:14`
- clase 'CatalogSnapshotJdbcAdapter' con baja cohesión (47%) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/CatalogSnapshotJdbcAdapter.java:14`
- método 'ProfessionalAgendaJdbcAdapter.ProfessionalRow' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/ProfessionalAgendaJdbcAdapter.java:51`
- método 'ProfessionalRow.ProfessionalRow' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/persistence/jdbc/ProfessionalAgendaJdbcAdapter.java:51`
- clase 'CatalogChangesRedisAdapter' con baja cohesión (21%) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/redis/CatalogChangesRedisAdapter.java:15`
- clase 'CatedraSnapshotRestAdapter' grande (LOC=55, métodos=10, campos=1) — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:18`
- método 'CatedraSnapshotRestAdapter.CategoryResponse' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:54`
- método 'CategoryResponse.CategoryResponse' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:54`
- método 'CatedraSnapshotRestAdapter.ProfessionalResponse' con 7 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:62`
- método 'ProfessionalResponse.ProfessionalResponse' con 7 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:62`
- método 'CatedraSnapshotRestAdapter.ScheduleResponse' con 9 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:70`
- método 'ScheduleResponse.ScheduleResponse' con 9 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/rest/CatedraSnapshotRestAdapter.java:70`
- método 'CatalogDiagnosticsResponse.CatalogDiagnosticsResponse' con 13 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/dto/CatalogDiagnosticsResponse.java:7`
- método 'ProfessionalAgendaResponse.ProfessionalAgendaResponse' con 6 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/dto/ProfessionalAgendaResponse.java:8`
- método 'ProfessionalAgendaResponse.WeeklySchedule' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/dto/ProfessionalAgendaResponse.java:22`
- método 'WeeklySchedule.WeeklySchedule' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/dto/ProfessionalAgendaResponse.java:22`
- método 'ProfessionalSearchResponse.Professional' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/dto/ProfessionalSearchResponse.java:12`
- método 'Professional.Professional' con 5 parámetros — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/web/dto/ProfessionalSearchResponse.java:12`

**OCP/type-switch** (2)
- clase 'CatalogDiagnosticsRecorder' con 6 type-checks — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogDiagnosticsRecorder.java:13`
- clase 'CatalogFailureClassifier' con 9 type-checks — `src/main/java/ar/edu/turnos/catalogo/catalogo/infrastructure/bootstrap/CatalogFailureClassifier.java:14`

## 6. Evolución por commit

Métricas calculadas sobre el estado de `main` en **cada commit** (6 commits con trabajo), del primero al último. La calidad usa el perfil `exigente`; el aporte de código lo mide RepoSense. El índice puede **bajar** al crecer el proyecto: se acumulan señales.

```mermaid
xychart-beta
    title "Índice de calidad a lo largo de los commits"
    x-axis ["fbbe0bd", "7d25fc0", "7561111", "d09480f", "37c2526", "c4f9322"]
    y-axis "Índice" 0 --> 100
    line [0, 15, 33, 61, 61, 61]
```

| # | Fecha | Commit | Autor | LOC | Índice | %POO | Duplic. | Sueltas |
|---|---|---|---|---:|---:|---:|---:|---:|
| 1 | 2026-09-07 | `fbbe0bd` | juancruz0912 | 0 | 0 | 0% | 0.0% | 0 |
| 2 | 2026-09-15 | `7d25fc0` | Ing. Daniel Quinteros | 0 | 15 | 0% | 0.0% | 0 |
| 3 | 2026-10-13 | `7561111` | Juan Cruz Rupcic | 18 | 33 | 56% | 0.0% | 0 |
| 4 | 2026-11-02 | `d09480f` | Juan Cruz Rupcic | 3,357 | 61 | 80% | 0.9% | 0 |
| 5 | 2026-11-02 | `37c2526` | Juan Cruz Rupcic | 3,357 | 61 | 80% | 0.9% | 0 |
| 6 | 2026-11-04 | `c4f9322` | Juan Cruz Rupcic | 3,357 | 61 | 80% | 0.9% | 0 |

---
_Informe generado por la cátedra. Las dimensiones y umbrales corresponden a `calidad.py` (perfil `exigente`); el semáforo, a `CRITERIOS-CALIDAD.md`. La documentación del repositorio no se evalúa. Ante dudas, revisá el archivo señalado._
<!-- evaluacion:fin -->
