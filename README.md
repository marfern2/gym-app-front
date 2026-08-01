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

El Web/server OAuth client ID no es un secreto, pero se mantiene fuera del código para centralizar la configuración por entorno. `GOOGLE_SERVER_CLIENT_ID` debe contener el Web/server client ID que se pasará en el futuro a `setServerClientId`; no debe contener el Android OAuth client ID. No se necesita ni se debe incluir ningún client secret.

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
