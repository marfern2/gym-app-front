# Spacing y dimensiones

La escala vive en `ui/theme/Dimensions.kt`.

| Token | Valor | Uso habitual |
|---|---:|---|
| `AppSpacing.xxs` | 2 dp | Separación óptica mínima |
| `AppSpacing.xs` | 4 dp | Elementos estrechamente relacionados |
| `AppSpacing.sm` | 8 dp | Separación compacta |
| `AppSpacing.md` | 12 dp | Contenido interno compacto |
| `AppSpacing.lg` | 16 dp | Padding estándar y separación de grupos |
| `AppSpacing.xl` | 24 dp | Secciones y sheets |
| `AppSpacing.xxl` | 32 dp | Estados vacíos y bloques amplios |
| `AppSpacing.xxxl` | 48 dp | Separación de alta jerarquía |

`AppComponentSize` define tamaños táctiles, botones, filas compactas de 56 dp, filas estándar de 64 dp, miniaturas, iconos, bordes y wheel items. `AppElevation` define niveles de elevación; usar el nivel más bajo que comunique la jerarquía.

Reglas:

- Preferir padding exterior de 16 dp y separación interna de 8/12 dp.
- No usar spacing para dimensiones de contenido con significado propio.
- Gráficas y media pueden conservar geometría local cuando dependa de cálculos o proporciones.
- Si un valor se repite en más de un componente compartido, debe convertirse en token con nombre semántico.
