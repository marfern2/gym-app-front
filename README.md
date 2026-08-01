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

GET /api/v1/users/me
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
4. La sesión local devuelta se conserva solo en memoria. Un interceptor elimina su marcador
   interno y añade el access token local como Bearer exclusivamente a `/api/v1/users/me`.
5. La UI considera el login completo únicamente cuando `/users/me` responde y entonces muestra
   el usuario local.

No hay refresh automático, reintento automático de `401`, persistencia, DataStore, logout remoto
ni navegación. **Cerrar sesión local** es una acción temporal que solo elimina los tokens de la
memoria del proceso. El ID token de Google no se usa como Bearer ni se conserva en estado de UI.

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

## Sesión y copias de seguridad

En este incremento `InMemorySessionStore` no usa archivos, `SharedPreferences`, DataStore ni
`SavedStateHandle`, por lo que backup y device transfer no contienen material de sesión. El
manifest mantiene `android:allowBackup="true"` para datos futuros no sensibles. Antes de añadir
almacenamiento seguro permanente será obligatorio cifrarlo mediante una abstracción basada en
Android Keystore y excluir expresamente las credenciales tanto de cloud backup como de device
transfer en `backup_rules.xml` y `data_extraction_rules.xml`.
