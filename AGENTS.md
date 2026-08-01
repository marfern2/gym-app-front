# Guía de trabajo para agentes

## Alcance y disciplina de cambios

- Inspeccionar el proyecto y sus instrucciones antes de modificar archivos.
- Mantener cada cambio pequeño y limitado al incremento solicitado.
- No implementar funcionalidades no solicitadas ni anticipar incrementos posteriores.
- No modificar las versiones generadas por Android Studio sin una necesidad demostrada.
- No añadir dependencias sin justificar su necesidad y comprobar su compatibilidad con la configuración existente.
- Mantener Kotlin y Jetpack Compose como tecnologías de implementación.
- Mantener inicialmente un único módulo `app`; no modularizar prematuramente.
- Extraer módulos solamente cuando existan una frontera arquitectónica clara y volumen real que lo justifique.
- No hacer commits.
- No ejecutar comandos destructivos.

## Arquitectura y estado

- Mantener un flujo unidireccional de datos.
- El estado de UI baja hacia los composables y los eventos suben hacia el `ViewModel`.
- Los composables no contienen lógica de red, persistencia ni autenticación.
- Los `ViewModel` no conocen `Activity`, `Fragment` ni componentes visuales.
- No pasar `Context` al `ViewModel` salvo mediante una abstracción imprescindible y justificada.
- Utilizar `StateFlow` para estado observable cuando corresponda.
- Representar explícitamente los estados `loading`, `content`, `empty` y `error`.
- Separar modelos de red, dominio y persistencia cuando tengan responsabilidades diferentes.
- No crear `BaseViewModel`, `BaseUseCase`, `BaseRepository` ni jerarquías genéricas equivalentes.

## Autenticación y sesión

- Utilizar Credential Manager para Sign in with Google.
- Utilizar el nonce entregado por el backend y pasarlo a Google durante la autenticación.
- Configurar el Web/server client ID mediante `setServerClientId`.
- Enviar al backend el Google ID token junto con el `challengeId`.
- No validar el Google ID token como fuente de autorización dentro de Android; el backend realiza su validación definitiva.
- Los access y refresh tokens locales son emitidos por el backend, y el access token se envía como Bearer token.
- No utilizar Firebase Authentication, el `GoogleSignInClient` antiguo ni Facebook por ahora.
- No registrar ID tokens, access tokens ni refresh tokens.
- No incluir secretos ni client secrets en el proyecto.
- El Web/server client ID no es un secreto, pero debe configurarse externamente por variante.
- No guardar tokens en texto plano ni en `SharedPreferences` sin cifrado.
- Diseñar el almacenamiento de sesión mediante una abstracción segura.

## Red y configuración por entorno

- Centralizar la URL base por build type.
- `debug` utilizará una URL local configurable; desde el emulador, la URL prevista es `http://10.0.2.2:8080`.
- `release` utilizará HTTPS; la URL prevista es `https://api.<YOUR_DOMAIN>`.
- No permitir tráfico HTTP en `release`.
- No desactivar globalmente la validación TLS.
- No crear interceptores que registren cabeceras `Authorization`.

El backend expone inicialmente estos contratos:

- `POST /api/v1/auth/google/challenge`
- `POST /api/v1/auth/google`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/users/me`

## UI, pruebas y calidad

- Añadir pruebas unitarias para `ViewModel`, repositorios y reglas de negocio.
- Añadir pruebas Compose solo para comportamientos relevantes.
- No depender de un emulador para todas las pruebas.
- Mantener previews de los estados principales.
- Aplicar accesibilidad, `contentDescription` y tamaños táctiles apropiados.
- Ejecutar `./gradlew test` después de los cambios.
- Ejecutar `./gradlew lint` y `./gradlew assembleDebug` al terminar cada incremento.
- Informar al finalizar de los archivos modificados, dependencias añadidas, comandos ejecutados y resultados.

## Contexto estable del proyecto

- Aplicación Android para registrar entrenamientos.
- Package name y namespace definitivos: `com.mar.gym`.
- Minimum SDK: 26.
- Backend independiente implementado con Spring Boot.

## Orden general de desarrollo

1. Reglas e inspección.
2. Configuración de build types y URL base.
3. Base arquitectónica mínima.
4. Cliente HTTP y contrato de errores.
5. Almacenamiento seguro de sesión.
6. Credential Manager y login con Google.
7. Consulta de `/users/me`.
8. Refresh y logout.
9. Navegación autenticada.
10. Catálogo de ejercicios.
11. Rutinas.
12. Entrenamientos.
13. Historial y progreso.
14. Caché local y sincronización.
