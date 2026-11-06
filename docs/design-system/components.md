# Componentes

Los componentes compartidos viven en `com.mar.gym.ui.components`.

| Componente | Uso |
|---|---|
| `AppPrimaryButton` | Acción principal, una por contexto |
| `AppSecondaryButton` | Acción tonal secundaria |
| `AppOutlinedButton` | Acción de menor énfasis o API flexible |
| `AppIconButton` | Acción solo icono con objetivo táctil accesible |
| `AppCard` | Entidad o agrupación accionable; borde tonal sutil, opcionalmente interactiva |
| `AppListRow` | Fila estándar; soporta selected, disabled y destructive |
| `AppMetricRow` | Par label/valor |
| `AppSettingRow` | Preferencia con leading/trailing content |
| `AppSectionHeader` | Título de sección y acción opcional |
| `AppTopBar` | Barra superior con back y acciones |
| `AppBottomSheet` | Contenedor y padding coherentes para sheets |
| `AppChip` | Filtro o selección binaria |
| `AppEmptyState` | Empty state con icono, mensaje y CTA opcionales |
| `AppDivider` | Separador semántico consistente |

`PrimaryButton`, `SecondaryButton`, `SectionHeader` y `EmptyState` se mantienen como aliases compatibles durante la migración.

## Regla de composición

Una rutina con título, metadata, menú y CTA justifica `AppCard`. Una carpeta, setting, destino de sheet o acción simple debe usar `AppListRow` sobre el fondo. `AppSectionHeader` nunca se envuelve en card. Los sheets se construyen con `AppBottomSheet`, `AppListRow` y `AppDivider`.

`TrainingScreen` y `RoutineListScreen` son las pantallas piloto y la referencia para nuevas migraciones: padding exterior de 16 dp, separación vertical de 12 dp, cards solo para rutinas, folders integrados y CTA primario consistente.

## Cuándo crear otro componente

Crear un componente compartido cuando aparezca el mismo patrón visual en al menos dos features y su API pueda describirse sin modelos de dominio. Si representa una entidad concreta (rutina, ejercicio, workout), mantenerlo en la feature y construirlo con primitivos `App*`.

## Catálogo y previews

`app/src/debug/java/com/mar/gym/ui/catalog/DesignSystemCatalog.kt` reúne colores, tipografía, spacing, botones, cards, filas, chips, sheets, headers y empty/error states. Está en el source set debug y no participa en la navegación release. Sus previews cubren dark, ancho estrecho y font scale 1.5.

Antes de cambiar un componente compartido, revisar el catálogo y compilar `assembleDebug` y `compileDebugAndroidTestKotlin`.

La comparación del piloto se documenta en [pilot.md](pilot.md).
