# Principios del design system

## Dirección visual

- Dark-first: fondos y superficies oscuras, texto claro y contraste accesible.
- El morado existente es el color de marca. Se reserva para acciones, selección y énfasis.
- La dirección se denomina **dense editorial fitness**: mucha información útil, jerarquía clara y poca decoración.
- Material 3 aporta semántica y comportamiento, pero no sustituye los tokens de la app.

## Decisiones visuales finales

- Densidad: 16 dp en bordes de pantalla, 12 dp entre bloques relacionados y filas de 56/64 dp.
- Jerarquía: `headlineMedium` para pantalla, `titleLarge` para secciones principales, `titleMedium` para rutinas y `bodySmall/Medium` para metadata.
- Radios: 10 dp en controles compactos, 14 dp en cards, 18 dp en botones y 26 dp en sheets.
- Superficies: el fondo es continuo. `surface` agrupa entidades accionables; `surfaceVariant` se limita a controles secundarios o agrupaciones pequeñas.
- Bordes: `outlineVariant` sutil en cards; elevación solo cuando comunica movimiento, como drag/drop.
- Cards: se usan para una rutina con contenido y CTA, estados o agrupaciones reales. No se usan para folders, headers, settings ni metadata aislada.
- Botones: 52 dp, primario morado para ejecutar, tonal para crear/acciones secundarias y outlined para menor prioridad o disabled futuro.
- Section headers: integrados en el fondo, sin card, con una única acción opcional.
- List rows: integradas, táctiles y separadas con spacing/divider; `primaryContainer` indica selección o drop target.
- Bottom sheets: título fuerte, acciones como filas con icono y destructivas en `error`; sin cards anidadas.
- Chips: selección tonal morada; no introducir otro color de acento.
- Empty states: una explicación breve y un único CTA. Evitar ilustración decorativa pesada.

Estas decisiones siguen la separación theme/componentes de Material 3, el catálogo ejecutable de Now in Android y la composición de design systems propios demostrada por Jetsnack/Jetcaster, sin copiar assets ni branding.

## Cómo diseñar una pantalla

1. Usar `GYmAppTheme` y colores de `MaterialTheme.colorScheme`.
2. Usar `AppSpacing`, `AppComponentSize`, `AppShapeTokens` y `AppElevation`.
3. Buscar primero un componente `App*` en `ui/components`.
4. Crear un componente de feature solo si contiene semántica propia del dominio.
5. Añadir al catálogo debug cualquier estado visual nuevo que deba mantenerse consistente.

No introducir colores hexadecimales, shapes o espaciados arbitrarios en features. Se permiten valores locales para geometría intrínseca (gráficas, media, gestos) cuando un token semántico no tenga sentido; documentar el motivo junto al valor.

## Compatibilidad y migración

El sistema se adopta de forma incremental. Los wrappers antiguos se conservan mientras las pantallas migran. No mezclar una migración visual con cambios de navegación, estado, ViewModels o negocio.

El pressed state global es `AppPressedIndication`/`appPressedState`. Los componentes nuevos deben reutilizarlo y no añadir ripples o animaciones alternativas.

## Referencias de ingeniería

- Material 3 Compose: <https://developer.android.com/develop/ui/compose/designsystems/material3>
- Now in Android: <https://github.com/android/nowinandroid>
- Compose Samples, Jetsnack y Jetcaster: <https://github.com/android/compose-samples>

## Accesibilidad

- Objetivos táctiles de al menos `AppComponentSize.touchTarget`.
- `contentDescription` en iconos accionables; iconos decorativos usan `null`.
- Probar pantalla estrecha y font scale 1.5.
- No depender solo del color para expresar selected, disabled o error.
