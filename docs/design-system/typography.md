# Typography

La fuente de verdad es `AppTypography` en `ui/theme/Type.kt`. Se usa mediante `MaterialTheme.typography` para heredar correctamente el tema.

| Rol | Uso |
|---|---|
| `displaySmall` | Métrica o mensaje de máximo énfasis |
| `headlineLarge/Medium/Small` | Títulos de pantalla y bloques destacados |
| `titleLarge` | Top bar, sheet y card principal |
| `titleMedium/Small` | Sección, fila y card |
| `bodyLarge` | Contenido principal y filas |
| `bodyMedium/Small` | Apoyo, metadatos y ayuda |
| `labelLarge` | Botones y chips |
| `labelMedium/Small` | Etiquetas compactas y captions |

No crear `TextStyle` en features para cambiar solo tamaño/peso. Elegir el rol más cercano y aplicar únicamente una variación semántica necesaria, como color de error. Respetar `sp` y validar font scale 1.5.
