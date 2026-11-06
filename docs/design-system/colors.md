# Colores

La fuente de verdad está en `ui/theme/Color.kt`; el mapeo semántico vive en `Theme.kt`.

## Uso semántico

| Necesidad | Token Material |
|---|---|
| Acción o selección principal | `primary` / `onPrimary` |
| Acción principal de alta intensidad | `primaryActionContainerColor()` |
| Contenedor de marca | `primaryContainer` / `onPrimaryContainer` |
| Selección secundaria | `secondaryContainer` / `onSecondaryContainer` |
| Fondo de pantalla | `background` / `onBackground` |
| Card o sheet | `surface` / `onSurface` |
| Área agrupada o control | `surfaceVariant` / `onSurfaceVariant` |
| Bordes | `outline` o `outlineVariant` |
| Error/destructivo | `error`, `errorContainer` y sus `on*` |

Dark es el modo principal. Light se mantiene funcional, pero no debe determinar las decisiones de marca.

## Excepciones controladas

`SetWarmup`, `SetFailure` y `SetDrop` codifican tipos de serie. `MediaScrim` y `ChartPointHighlight` cubren media/gráficas. No usar estos colores como sustitutos de roles Material.

No usar `Color.Black`, `Color.White` ni `Color(0x...)` en una feature. Si falta un rol reutilizable, añadirlo primero a la paleta y documentarlo.
