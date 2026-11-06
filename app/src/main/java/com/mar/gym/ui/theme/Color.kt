package com.mar.gym.ui.theme

import androidx.compose.ui.graphics.Color

// Color real del botón "Empezar rutina". Es la base de marca y la superficie de
// las acciones principales. En dark, BrandPurpleAccent aporta contraste cuando
// el color se usa como texto, icono, progreso o estado seleccionado.
val BrandPurple = Color(0xFF492473)
val OnBrandPurple = Color(0xFFFFFFFF)
val BrandPurpleAccent = Color(0xFFC9A7E8)
val OnBrandPurpleAccent = Color(0xFF2A1338)
val BrandPurpleContainerLight = Color(0xFFEBDDF2)
val OnBrandPurpleContainerLight = Color(0xFF2B1538)
val BrandPurpleSecondaryDark = Color(0xFFB79AC8)
val OnBrandPurpleSecondaryDark = Color(0xFF2D1D35)
val BrandPurpleSecondaryContainerDark = Color(0xFF3A2A43)
val OnBrandPurpleSecondaryContainerDark = Color(0xFFE8DAEE)
val BrandPurpleSecondaryLight = Color(0xFF6B5277)
val BrandPurpleSecondaryContainerLight = Color(0xFFF0E6F4)
val OnBrandPurpleSecondaryContainerLight = Color(0xFF291E2E)

// Neutros con un matiz morado muy contenido para cohesionar superficies sin
// convertir toda la interfaz en una masa de color de marca.
val InkDark = Color(0xFF111013)
val InkDarkSurface = Color(0xFF19171C)
val InkDarkSurfaceVariant = Color(0xFF27232C)
val InkDarkOnSurface = Color(0xFFF0EBF1)
val InkDarkOnSurfaceVariant = Color(0xFFC9C1CB)
val InkDarkOutline = Color(0xFF928A96)
val InkDarkOutlineVariant = Color(0xFF3B3540)

val InkLight = Color(0xFFFAF8FB)
val InkLightSurface = Color(0xFFFFFFFF)
val InkLightSurfaceVariant = Color(0xFFEDE8EF)
val InkLightOnSurface = Color(0xFF201C22)
val InkLightOnSurfaceVariant = Color(0xFF514A54)
val InkLightOutline = Color(0xFF7D747F)
val InkLightOutlineVariant = Color(0xFFD2CAD5)

// Semantic status colors. Components should consume these through ColorScheme.
val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)
val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Media and data-visualisation colors that intentionally sit outside ColorScheme.
val MediaScrim = Color(0xFF000000)
val ChartPointHighlight = Color(0xFFFFFFFF)

// Acentos de la columna SERIE del workout activo: calentamiento, fallo y drop.
val SetWarmup = Color(0xFFFFC53D)
val SetFailure = Color(0xFFFF5C5C)
val SetDrop = Color(0xFF4DD0E1)
