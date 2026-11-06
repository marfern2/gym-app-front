# Piloto visual

## Comparación

| Pantalla | Antes | Después |
|---|---|---|
| Training | Espaciados 7/8/9/10/12/18 dp, botones locales con shapes propios, folders y rutinas con tratamientos distintos y sheets construidos manualmente | Ritmo 16/12/8, headline claro, acciones `App*`, folders integrados, rutinas como `AppCard`, selected/drop target morado y sheets con filas compartidas |
| Routine list | Search/sort sin ritmo común, cards Material por defecto, icon button y CTA locales, sheet de texto sin iconografía | Search y sort alineados al tema, cards idénticas a Training, CTA de 52 dp, metadata secundaria, estados compartidos y sheet con iconos/divisores/destructivo semántico |

## Matriz visual

- Dark: previews explícitas con `darkTheme = true`.
- Narrow: previews a 320 dp en ambas pantallas.
- Font scale: previews a 1.5 con nombres largos.
- Enabled/disabled: Routine List muestra una operación ocupada; Training mantiene Explorar disabled.
- Empty: previews de Training y Routine List sin contenido.
- Sheets: `DesignSystemCatalog` permite abrir el patrón aprobado; los tests existentes cubren acciones y dismiss.

Previews relevantes:

- `TrainingPreview`
- `TrainingActivePreview`
- `TrainingNarrowLargeFontPreview`
- `EmptyRoutineListPreview`
- `RoutineListNarrowLargeFontPreview`
- `DesignSystemCatalogDarkPreview`
- `DesignSystemCatalogNarrowPreview`
- `DesignSystemCatalogLargeFontPreview`

No se mantienen dos implementaciones productivas para simular el “antes”. La comparación anterior documenta el código previo; el catálogo y las previews son la fuente visual vigente.
