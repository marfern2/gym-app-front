# GYmApp Android

Cliente Android en Kotlin y Jetpack Compose para registrar entrenamientos.

## Configuración local

Android Studio crea `local.properties` en la raíz con la ubicación del SDK. Conserva esa línea y añade las propiedades documentadas en `local.properties.example`:

```properties
GOOGLE_SERVER_CLIENT_ID=YOUR_WEB_SERVER_CLIENT_ID.apps.googleusercontent.com
RELEASE_API_BASE_URL=https://api.YOUR_CLOUDFLARE_DOMAIN/
# Opcional; este es el valor predeterminado:
DEBUG_API_BASE_URL=http://10.0.2.2:8080/
```

`local.properties` está ignorado por Git y no debe versionarse. También pueden proporcionarse los valores mediante `-P`, variables `ORG_GRADLE_PROJECT_<NOMBRE>` o variables de entorno con el nombre exacto de la propiedad.

El Web/server OAuth client ID no es un secreto, pero se mantiene fuera del código para centralizar la configuración por entorno. `GOOGLE_SERVER_CLIENT_ID` debe contener el Web/server client ID que se pasa a `setServerClientId`; no debe contener el Android OAuth client ID. No se necesita ni se debe incluir ningún client secret.

## Variantes

- `debug` utiliza `http://10.0.2.2:8080/` de forma predeterminada. `10.0.2.2` permite que el emulador Android acceda al `localhost` del ordenador. Puede establecerse `DEBUG_API_BASE_URL`, pero HTTP solo se admite para ese host.
- `release` exige `RELEASE_API_BASE_URL`, requiere HTTPS y nunca utiliza una URL local como fallback.
- Ambas variantes exponen `BuildConfig.API_BASE_URL` y `BuildConfig.GOOGLE_SERVER_CLIENT_ID`.
- Solo debug incluye una Network Security Config que permite cleartext exclusivamente hacia `10.0.2.2`; release mantiene cleartext desactivado.

## Comandos de verificación

Con las propiedades añadidas a `local.properties`:

```bash
./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Para una ejecución puntual sin modificar archivos locales:

```bash
./gradlew test \
  -PGOOGLE_SERVER_CLIENT_ID=YOUR_WEB_SERVER_CLIENT_ID.apps.googleusercontent.com \
  -PRELEASE_API_BASE_URL=https://api.YOUR_CLOUDFLARE_DOMAIN/

./gradlew lint \
  -PGOOGLE_SERVER_CLIENT_ID=YOUR_WEB_SERVER_CLIENT_ID.apps.googleusercontent.com

./gradlew assembleDebug \
  -PGOOGLE_SERVER_CLIENT_ID=YOUR_WEB_SERVER_CLIENT_ID.apps.googleusercontent.com
```

Una compilación de producción debe proporcionar ambos valores externos:

```bash
./gradlew assembleRelease \
  -PGOOGLE_SERVER_CLIENT_ID=YOUR_WEB_SERVER_CLIENT_ID.apps.googleusercontent.com \
  -PRELEASE_API_BASE_URL=https://api.YOUR_CLOUDFLARE_DOMAIN/
```

## Contrato de autenticación implementado

Los DTO coinciden con el contrato del backend Spring Boot:

```text
POST /api/v1/auth/google/challenge
-> { challengeId: UUID, nonce: String, expiresIn: Long }

POST /api/v1/auth/google
<- { idToken: String, challengeId: UUID }
-> {
     tokenType: "Bearer",
     accessToken: String,
     accessTokenExpiresIn: Long,
     refreshToken: String,
     refreshTokenExpiresIn: Long
   }

POST /api/v1/auth/refresh
<- { refreshToken: String }
-> {
     tokenType: "Bearer",
     accessToken: String,
     accessTokenExpiresIn: Long,
     refreshToken: String,
     refreshTokenExpiresIn: Long
   }

POST /api/v1/auth/logout
Authorization: Bearer <local-access-token>
<- { refreshToken: String }
-> 204 No Content

GET /api/v1/users/me
Authorization: Bearer <local-access-token>
-> { id: UUID, displayName: String, accountStatus: String }
```

`expiresIn`, `accessTokenExpiresIn` y `refreshTokenExpiresIn` están expresados en segundos.
El contrato actual de `/users/me` no expone email ni avatar; la aplicación no los deriva del ID
token de Google ni los inventa, y muestra únicamente el nombre, estado e identificador local que
el backend autoriza.

## Flujo de autenticación

1. Al pulsar **Continuar con Google**, Android solicita un challenge nuevo y bloquea pulsaciones
   adicionales mientras el flujo está activo.
2. Un efecto de UI consumible abre Credential Manager con `GetSignInWithGoogleOption`, el Web/server
   client ID de `BuildConfig.GOOGLE_SERVER_CLIENT_ID` y el nonce exacto del backend.
3. Android acepta solamente un `CustomCredential` del tipo de `GoogleIdTokenCredential` y entrega
   su ID token, junto al `challengeId`, a `POST /api/v1/auth/google`.
4. La respuesta se transforma en una sesión con expiraciones absolutas calculadas desde las
   duraciones del backend y se persiste cifrada antes de publicarla en la caché de memoria.
5. Un interceptor elimina el marcador interno y añade el access token local como Bearer solo a
   llamadas marcadas explícitamente. Challenge, login y refresh son públicos; logout recibe
   Bearer, pero está marcado para no activar refresh automático.
6. La UI considera el login completo únicamente cuando `/users/me` responde y entonces muestra
   el usuario local.

El ID token de Google no se usa como Bearer, no se persiste y no se conserva en estado de UI.
Este incremento no añade navegación general, rutinas ni entrenamientos. La pantalla autenticada
solo ofrece acceso temporal al catálogo de plantillas de ejercicio de solo lectura.

Los mensajes y representaciones textuales de objetos sensibles están redactados. OkHttp no tiene
interceptor de logging y nunca se registran cuerpos, tokens ni `Authorization`.

## Prueba manual de Google real

1. Arranca PostgreSQL y el backend Spring Boot y confirma que escucha en el puerto `8080`.
2. Añade el `GOOGLE_SERVER_CLIENT_ID` Web real a `local.properties`.
3. Configura ese mismo Web client ID real como `GOOGLE_SERVER_CLIENT_ID` en el backend.
4. Configura `GOOGLE_ALLOWED_AZP` en el backend con el Android client ID exacto de debug.
5. Utiliza un emulador API 26 o posterior cuya imagen incluya Google Play.
6. Añade una cuenta de Google al emulador.
7. Si la OAuth consent screen está en estado **Testing**, añade esa cuenta como test user.
8. Desde este repositorio ejecuta `./gradlew installDebug`.
9. Abre GYmApp y pulsa **Continuar con Google**.
10. Elige la cuenta.
11. Confirma que aparece **Sesión iniciada** y el `displayName` recibido de `/users/me`.
12. Comprueba en el backend o en PostgreSQL que el usuario se creó o vinculó, sin imprimir ni
    consultar tokens en logs.

El emulador usa `10.0.2.2` para alcanzar el `localhost` del anfitrión. La sección secundaria
**Diagnóstico del backend** permite comprobar el ping público sin dominar el flujo de login.

### Diagnóstico de autenticación

- **`DEVELOPER_ERROR`:** revisa juntos el package `com.mar.gym`, la huella SHA-1 de la firma debug,
  el Android OAuth client y el Web/server client ID. No intercambies los client IDs.
- **SHA-1 incorrecta:** registra en el Android OAuth client la SHA-1 de la firma con la que se
  instaló realmente la variante debug.
- **Package name incorrecto:** el OAuth client Android debe usar exactamente `com.mar.gym`.
- **Web client ID incorrecto:** `GOOGLE_SERVER_CLIENT_ID` de Android y backend debe ser el client
  de tipo Web, no el Android client ni un client secret.
- **Android client no autorizado en `azp`:** añade el client ID Android debug exacto a
  `GOOGLE_ALLOWED_AZP`; no añadas ahí el Web client ID.
- **Usuario no autorizado:** si la aplicación está en Testing, añade la cuenta como test user.
- **Emulador sin Google Play:** usa una imagen que incluya Google Play y una cuenta configurada.
- **Challenge expirado, consumido o inválido:** vuelve a pulsar el botón; la aplicación solicita
  un challenge nuevo y no reutiliza el anterior.
- **Backend inaccesible:** comprueba desde el anfitrión
  `http://localhost:8080/api/v1/system/ping`, el puerto, firewall/VPN y la URL debug.
- **Reloj incorrecto:** sincroniza fecha, hora y zona tanto del emulador como del servidor; una
  desviación puede invalidar el challenge o los claims temporales de Google.
- **Cleartext bloqueado:** instala debug. Solo debug permite HTTP hacia `10.0.2.2`; release exige
  HTTPS y mantiene cleartext desactivado.

## Ciclo de vida de la sesión local

### Persistencia y Android Keystore

`PersistentSessionStore` mantiene una única caché en memoria para que las peticiones no lean disco.
La copia persistente está en `filesDir/secure_session/local_session.enc` y se escribe mediante
`AtomicFile`: la caché solo cambia después de completar con éxito la escritura atómica.

Antes de escribir, un codec binario serializa exclusivamente `tokenType`, access token, refresh
token y los instantes UTC de expiración. El objeto y los resultados sensibles tienen `toString()`
redactado. El plaintext transitorio se sobrescribe al terminar la operación. El archivo contiene
solo una versión de formato, el IV y ciphertext autenticado; no contiene claves ni tokens legibles.

`AndroidKeystoreSessionCipher` genera una clave AES de 256 bits no exportable en el proveedor
`AndroidKeyStore`, limitada a `AES/GCM/NoPadding`, cifrado/descifrado y uso de IV aleatorio. Cada
escritura inicializa una operación nueva y conserva el IV nuevo de 96 bits junto al ciphertext; la
cabecera de versión está autenticada como AAD y GCM aporta una etiqueta de 128 bits. Estas APIs de
plataforma están disponibles por debajo de minSdk 26, por lo que no se añade una librería de
cifrado. `EncryptedSharedPreferences` no se usa porque su API actual está obsoleta.

Si falta la clave, fue invalidada, el ciphertext está corrupto, falla GCM o el formato es inválido,
la aplicación no intenta recuperar parcialmente credenciales: borra la caché, el archivo y el alias
Keystore. Una copia del ciphertext restaurada en otro dispositivo sin su clave Keystore nunca se
acepta como una sesión válida.

### Copias de seguridad

`backup_rules.xml` excluye `secure_session/` en el formato legacy. `data_extraction_rules.xml`
excluye el mismo directorio tanto de `cloud-backup` como de `device-transfer`. Se excluye el
directorio completo para cubrir `local_session.enc` y los artefactos transitorios de `AtomicFile`.
El resto de datos futuros no sensibles puede seguir usando `android:allowBackup="true"`.

### Expiración, restauración y refresh

Las duraciones `accessTokenExpiresIn` y `refreshTokenExpiresIn`, expresadas en segundos, se
convierten con un `Clock` inyectable en instantes absolutos al recibir cada respuesta. La decisión
de uso aplica un margen de 30 segundos y no decodifica ni confía en claims JWT.

Al arrancar se muestra `RestoringSession` y se lee/descifra fuera del hilo principal. Si no existe
sesión se muestra `SignedOut`. Si el access token es utilizable se valida con `/users/me`; si no,
pero el refresh token sigue siendo utilizable, se muestra `RefreshingSession`, se rota el par y se
persiste antes de consultar `/users/me`. Nunca se muestra `Authenticated` por la mera presencia del
archivo. Un refresh rechazado o expirado limpia la sesión. Un error temporal mantiene la copia
potencialmente válida y muestra `RecoverableSessionError` con reintento.

`SessionRefreshCoordinator` comparte una renovación en curso y la protege con `Mutex`. Cuando
varias peticiones fallan con el mismo access token, una realiza refresh y las demás esperan su
resultado; si al entrar ya existe un token nuevo, lo reutilizan. La rotación reemplaza siempre ambos
tokens y sus expiraciones. Si no puede persistirse el par rotado, se destruye la sesión local para
no mantener memoria y disco contradictorios.

`SessionAuthenticator` actúa únicamente ante `401` de una petición marcada `retry-on-401`. Revisa
`priorResponse`, realiza como máximo un refresh y construye como máximo una repetición con el token
nuevo. No interviene ante otros códigos ni en challenge, login, refresh o logout. Las llamadas
públicas sin marcador nunca reciben `Authorization`.

### Logout remoto y borrado local

**Cerrar sesión** envía el refresh token en JSON y el access token como Bearer, exactamente como
exige el backend. Logout está marcado `no-retry`, por lo que un `401` no inicia refresh. Un `204`
confirma la revocación remota y después se eliminan caché, archivo y clave. Un `401` también elimina
la sesión local porque ya no es utilizable.

Ante error de red no se afirma que el servidor haya cerrado la sesión y se mantiene la copia local
para poder elegir entre **Reintentar** o **Eliminar solo de este dispositivo**. Esta segunda acción
destruye la copia local explícitamente, pero informa de que la revocación remota no fue confirmada.
El backend no mantiene una denylist de access tokens: uno emitido antes del logout puede seguir
siendo válido hasta su expiración.

### Diagnóstico básico de sesión

- Si la sesión desaparece tras cambiar o restaurar el dispositivo, es el comportamiento esperado:
  el ciphertext no sirve sin la clave Keystore original y además está excluido de transferencias.
- Si aparece un error recuperable al arrancar, comprueba conectividad y usa **Reintentar**; un error
  temporal no borra automáticamente la sesión.
- Si el servidor rechaza refresh, revisa los códigos `INVALID_REFRESH_TOKEN`,
  `REFRESH_TOKEN_EXPIRED` o `REFRESH_TOKEN_REUSED` en observabilidad del backend sin registrar el
  token.
- Si falla el almacenamiento seguro, revisa espacio disponible y salud de Android Keystore; la app
  prioriza destruir el estado local antes que conservar un par rotado parcialmente.

## Catálogo de plantillas de ejercicio

El catálogo consume exclusivamente los endpoints protegidos de solo lectura:

```text
GET /api/v1/exercise-templates
    ?query=<texto>
    &primaryMuscleGroup=<MuscleGroup>
    &equipment=<Equipment>
    &exerciseType=<ExerciseType>
    &movementPattern=<MovementPattern>
    &page=<0..n>
    &size=<1..100>
    &sort=<campo,dirección>

GET /api/v1/exercise-templates/{exerciseTemplateId}
```

La aplicación solicita páginas de 20 elementos. La búsqueda aplica un debounce de 400 ms y
normaliza espacios; cada cambio de búsqueda, filtros u orden cancela la carga anterior y vuelve a
la página 0. Los filtros se editan en un borrador y solo se combinan con semántica AND al pulsar
**Aplicar**. El orden se limita a `name`, `primaryMuscleGroup`, `equipment` y `exerciseType`; ningún
texto libre se convierte en un nombre de propiedad del backend.

La paginación incremental conserva los resultados existentes durante la carga y si falla una
página posterior. No solicita una página ya en curso, deja de cargar al recibir `last=true`,
descarta respuestas de consultas antiguas y elimina duplicados por UUID sin cambiar el orden del
servidor. El detalle siempre se obtiene del endpoint individual y no carga instrucciones desde el
listado.

El selector reutiliza el mismo navegador del catálogo y admite selección única o múltiple, IDs
iniciales, búsqueda y filtros sin perder la selección. Confirmar devuelve solo un conjunto de UUIDs
al llamador; cancelar no devuelve selección. En este incremento el resultado es transitorio: no
crea rutinas, entrenamientos ni realiza escrituras en el backend. Catálogo, detalle y selector se
conectan mediante destinos locales mínimos en `MainActivity`, sin bottom navigation, deep links ni
una arquitectura de navegación general.

No se añadieron dependencias para este catálogo: `StateFlow`, coroutines, Retrofit y Compose ya
estaban disponibles en el módulo. Las peticiones usan el marcador protegido y el interceptor y
authenticator existentes, de modo que nunca usan el ID token de Google y pueden compartir el
refresh controlado tras un `401`.

### Prueba manual del catálogo

1. Arranca PostgreSQL y el backend Spring Boot en el puerto `8080`.
2. Inicia sesión y pulsa **Ver ejercicios**.
3. Confirma que aparecen las plantillas seed del backend.
4. Busca `press` y repite con mayúsculas y minúsculas.
5. Filtra por equipamiento **Barra** y por músculo principal **Pecho**.
6. Combina ambos filtros, aplícalos y después usa **Restablecer**.
7. Cambia entre **Nombre A–Z**, **Nombre Z–A** y los órdenes por músculo, equipamiento y tipo.
8. Abre una plantilla y comprueba descripción, atributos e instrucciones ordenadas.
9. Vuelve, abre **Seleccionar ejercicios** y selecciona varios elementos.
10. Busca otro texto y comprueba que el contador y la selección anterior se conservan.
11. Comprueba **Confirmar** y **Cancelar**; ninguna acción debe crear datos en el backend.
12. Detén el backend y comprueba el error recuperable y el reintento sin perder una página previa.
13. Con una sesión cuyo access token haya expirado, abre el catálogo y confirma en la
    observabilidad segura del backend que el refresh automático permite repetir una sola vez la
    petición protegida, sin registrar tokens.

## Medios de plantillas de ejercicio

El endpoint de detalle añade una lista `media`; el listado sigue sin incluir ni precargar medios.
Cada elemento contiene `type`, `role`, URL HTTPS, dimensiones opcionales y atribución opcional. La
aplicación selecciona una sola demostración: primero `ANIMATED_GIF`, después `IMAGE` como fallback,
y nunca intenta reproducir `VIDEO`. Una lista vacía o una URL no HTTPS se representa como
**Demostración no disponible** sin convertir el detalle completo en error.

La pantalla utiliza Coil 3.0.4 con `coil-compose`, `coil-gif` y `coil-network-okhttp`. Esta versión
estable es compatible con Kotlin 2.0.21, Java 11 y minSdk 26 del proyecto sin exigir una migración
general del toolchain. Existe un único `ImageLoader` reutilizable en `AppContainer`: usa
`AnimatedImageDecoder` desde API 28 y `GifDecoder` en API 26–27. Se mantienen las cachés de memoria
y disco predeterminadas de Coil; no existe descarga propia, precarga, proxy ni almacenamiento
offline.

## Workouts activos e historial

El cliente implementa el contrato actual de `/api/v1/workouts`: recupera el único workout
`ACTIVE`, permite iniciarlo vacío o desde una rutina, reemplazar su contenido completo, completarlo,
descartarlo y consultar el historial paginado y su detalle readonly. Las lecturas usan el cliente
autenticado con refresh existente. Si el access token ya no es utilizable se renueva antes de
enviar una mutación; las mutaciones se marcan `no-retry` y nunca se repiten automáticamente.

Cada documento conserva el `ETag` validado contra `version`. `PUT`, `complete` y `discard` envían
ese valor mediante `If-Match`; una respuesta correcta sustituye el borrador por el documento
canónico y su nuevo `ETag`. Ante `WORKOUT_VERSION_CONFLICT` el borrador local se conserva y la UI
advierte antes de recargar la versión del servidor.

Los DTO de red están separados del borrador de UI. Los `target*` son snapshots readonly y se
representan aparte de `reps`, `weight`, `durationSeconds`, `distanceMeters`, `rpe` y `completed`.
Un target nunca se copia a un resultado. Los ejercicios y series existentes envían sus UUID; los
nuevos omiten `id` y adquieren el UUID únicamente desde la respuesta del backend. Los ejercicios
nuevos envían solo `exerciseTemplateId` y campos editables, nunca snapshots manipulables.

El tiempo visible se deriva de `startedAt` mediante un `Clock` y se refresca localmente cada segundo.
No se persiste `elapsed`, no existe timer en el repository y el refresco visual no realiza llamadas
al backend. Volver a la pantalla vuelve a consultar `/workouts/active`, por lo que la duración se
reconstruye correctamente después de cerrar y reabrir la aplicación.

Las URLs de medio y atribución se validan antes de llegar a Compose y solo aceptan HTTPS. La
representación textual del tipo validado está redactada. El enlace de atribución se abre mediante
un intent `ACTION_VIEW` navegable únicamente si existe una Activity compatible; cualquier fallo se
muestra localmente y no cierra la aplicación. La atribución visible es exactamente la entregada por
el backend.

Los GIF se descargan desde el proveedor al abrir el detalle y no están incorporados como recursos
ni binarios dentro del APK. Si la carga externa falla, la descripción, músculos, equipamiento e
instrucciones permanecen disponibles y se ofrece un reintento visual que no repite el endpoint de
detalle.

### Prueba manual de medios

1. Arranca PostgreSQL, el backend y la aplicación Android.
2. Abre una plantilla cuyo detalle contenga un GIF y confirma que la demostración se anima.
3. Comprueba que la atribución coincide con el backend y que **Abrir fuente** abre su URL HTTPS.
4. Abre una plantilla con `media: []` y confirma **Demostración no disponible**.
5. Mantén el backend local activo, desconecta el acceso a Internet y vuelve a abrir un detalle con
   GIF.
6. Confirma el fallback visual y que descripción e instrucciones siguen visibles.
7. Reactiva Internet, vuelve a abrir el detalle y comprueba la carga de nuevo.
8. Repite en un emulador API 26 o 27 para validar `GifDecoder`, si está disponible.
9. Repite en API 28 o superior para validar `AnimatedImageDecoder`.

## Cliente de rutinas personales

La pantalla autenticada ofrece **Mis rutinas** y una navegación local temporal entre el listado,
el editor y el selector de ejercicios. No se ha añadido bottom navigation ni se serializan objetos
en rutas: el detalle recibe únicamente el UUID de la rutina.

El contrato se confirmó contra `gym-api` y sus pruebas HTTP:

```text
POST /api/v1/routines
GET  /api/v1/routines?archived=false&query=&page=0&size=20&sort=updatedAt,desc
GET  /api/v1/routines/{routineId}
PUT  /api/v1/routines/{routineId}                     If-Match: "<version>"
POST /api/v1/routines/{routineId}/archive             If-Match: "<version>"
POST /api/v1/routines/{routineId}/restore             If-Match: "<version>"
POST /api/v1/routines/{routineId}/duplicate           If-Match: "<version>"
```

Crear devuelve `201`, `Location`, `ETag` y el detalle. Detalle, reemplazo, archivado y restauración
devuelven `200`, `ETag` y el detalle; duplicar devuelve `201`, `Location`, `ETag` y el detalle de la
copia. `If-Match` admite en el backend una versión numérica cruda o entre comillas. El cliente
conserva y reenvía el valor de cabecera entre comillas, comprueba que coincide con `version` y
rechaza como respuesta incompatible un ETag ausente, débil, mal formado o discordante.

El listado pagina desde cero, usa 20 elementos por página (máximo backend 100), excluye archivadas
por defecto y permite ordenar solo por `name`, `createdAt` o `updatedAt`, en ascendente o
descendente. La búsqueda se normaliza y aplica un debounce de 400 ms. El DTO resumido contiene
`id`, nombre, descripción, conteo de ejercicios, estado, timestamps y versión; el detalle añade
ejercicios y series ordenados con el nombre, `ExerciseType` y equipo actuales del catálogo.

Los cuerpos de creación y `PUT` contienen nombre, descripción y ejercicios con posiciones desde
1. Cada ejercicio contiene `exerciseTemplateId`, posición, notas, `restSeconds` y series; cada
serie contiene posición, `SetType` y objetivos opcionales. El cliente nunca envía `ExerciseType`.
Los tipos de serie son `NORMAL`, `WARMUP`, `DROP` y `FAILURE`. Peso/asistencia/lastre se expresan en
kilogramos, distancia en metros, duración y descanso en segundos, y RPE entre 1.0 y 10.0.

El editor conserva números como texto hasta validar. Aplica los límites del backend: nombre 2–100,
descripción 2000, 30 ejercicios, una plantilla una vez, notas 1000, descanso 0–3600, 20 series por
ejercicio y 200 totales. Repeticiones admiten 1–1000; peso 0–10000 con 3 decimales; distancia
0.001–1000000 con 3; duración 1–86400; RPE 1.0–10.0 con 1. Solo muestra y acepta las métricas
compatibles con cada `ExerciseType`.

Los UUID de `RoutineExercise` y `RoutineSet` recibidos no se incorporan al borrador. Cada carga
genera IDs locales temporales que Compose usa para edición, foco, orden y asociación de errores.
Al guardar se recalculan todas las posiciones y se construye el cuerpo completo esperado por el
backend. El UUID raíz de `Routine` sí se conserva.

Un `409 ROUTINE_VERSION_CONFLICT` no se reintenta ni sobrescribe. El editor conserva el borrador,
muestra el conflicto y ofrece recargar; si hay cambios locales avisa de que se perderán. Las
operaciones mutantes tampoco se repiten automáticamente. Los errores siguen usando Problem Details
RFC 9457 y se conservan las rutas anidadas de `fieldErrors`.

### Prueba manual de rutinas

1. Inicia sesión, abre **Mis rutinas**, crea una rutina vacía y comprueba el estado guardado.
2. Crea otra rutina y añade tres ejercicios mediante **Añadir ejercicios**.
3. Añade series a ejercicios de tipos distintos y confirma que solo aparecen sus objetivos válidos.
4. Guarda, vuelve al listado, reabre la rutina y comprueba nombre, notas, descanso, orden y series.
5. Edita nombre y descripción, guarda y vuelve a abrir.
6. Reordena ejercicios y series con **Mover arriba/abajo**, guarda y reabre.
7. Confirma el diálogo de archivado, abre **Archivadas**, restaura y verifica la lista activa.
8. Duplica desde una card o el editor y comprueba que se abre una rutina independiente y activa.
9. Cierra completamente la aplicación, vuelve a abrirla y comprueba la persistencia remota.
10. Abre la misma rutina en dos clientes; guarda en uno y luego en el otro con su ETag antiguo.
    Comprueba que el segundo conserva sus cambios, muestra conflicto y solo recarga al solicitarlo.
11. Detén el backend y comprueba error y reintento tanto en listado como en detalle.
12. Usa un access token expirado y comprueba que el refresh compartido existente completa una sola
    repetición protegida, sin registrar credenciales ni añadir un refresh específico de rutinas.

Este incremento no implementa entrenamientos, inicio de rutina, contador, historial, programas,
superseries, caché local ni navegación inferior definitiva.
