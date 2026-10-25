# FCM en Android

## Configuración real

1. Crear o elegir un proyecto en [Firebase Console](https://console.firebase.google.com/) y registrar una app Android con package `com.mar.gym`. El `applicationId` debe coincidir exactamente. Activar Firebase Cloud Messaging para ese proyecto. No se necesita Analytics.
2. Descargar el `google-services.json` de esa app y colocarlo en `app/google-services.json`. El archivo está ignorado por Git. El plugin Google Services solo se aplica cuando el archivo existe; así se pueden compilar pruebas locales sin un proyecto Firebase, pero no se recibirá FCM hasta configurarlo.
3. Usar un emulador con Google Play services o un dispositivo real. Comprobar que el login muestra la preferencia de push en Ajustes y que el backend recibe `POST /api/v1/users/me/push-devices` con `platform=ANDROID`.
4. En el backend ya existente, habilitar `PUSH_ENABLED=true`, configurar `PUSH_PROJECT_ID` con el ID de *este mismo* proyecto Firebase y proporcionar credenciales de servicio fuera del repositorio mediante `PUSH_CREDENTIALS_PATH` o Application Default Credentials. La cuenta de servicio debe poder enviar mensajes FCM. No copiar credenciales de servicio al proyecto Android.

La app utiliza `firebase-messaging` con Firebase BoM y el plugin Google Services. No incluye Firebase Authentication, Analytics ni Crashlytics. El token FCM se obtiene del SDK y se mantiene solo en memoria de esta capa; el SDK conserva su propio token y se vuelve a consultar tras restaurar sesión. El `deviceId` devuelto por el backend queda asociado a la sesión en memoria y se vuelve a obtener al registrar tras reiniciar el proceso.

## Comportamiento y límites del contrato actual

- El backend envía mensajes FCM con `notification` + `data`. En foreground, `FirebaseMessagingService` crea una notificación local con el channel `social_notifications`. Si el listado está visible se omite esa notificación y se refresca el listado. En background, Android/FCM muestra automáticamente la parte `notification`; los campos `data` llegan al intent al tocarla. Para que la app controle también el renderizado y la deduplicación en background, el backend tendría que enviar mensajes **solo `data`**. Esto queda fuera del trabajo Android.
- La app comprueba `notificationId`, actor y disponibilidad contra el listado persistido del usuario antes de abrir un destino. FOLLOW necesita el `username` de ese registro, pues el payload solo incluye `actorUserId`. Si no se encuentra en la primera página de 100 elementos, falla la red, falta metadata o el destino ya no está disponible, se abre el listado de notificaciones.
- El payload no contiene el ID del receptor. Si un token antiguo aún está asociado a la cuenta A por un cierre offline, Android puede mostrar un push de A antes de que el login de C transfiera el token. El tap no abre el recurso de A en C porque se verifica contra el listado de C; para evitar incluso la visualización transitoria haría falta identificar al receptor en el payload y enviar solo `data`.
- Al cerrar sesión se intenta borrar el `deviceId` antes de revocar la sesión. Si no hay red, se elimina la sesión local; el servidor puede conservar el token hasta que otro login en este dispositivo lo registre y transfiera. El backend hace ese registro idempotente por token.
- Al quedar sin sesión, la app descarta el tap pendiente y retira del panel las notificaciones del channel social que siguen publicadas por esta instalación.
- Android 13+ solicita permiso solo al activar push en Ajustes. Si se deniega, la preferencia no se activa desde ese flujo y el listado persistido sigue disponible. Si la preferencia ya estaba activa en el backend pero se revoca el permiso en Android, Ajustes muestra una advertencia.

## Prueba manual con dos cuentas

Usar A como actor y B como receptor; confirmar primero que ambos apuntan al mismo proyecto Firebase.

1. B inicia sesión, activa push en Ajustes si estaba desactivado, concede permiso Android 13+ y confirma el registro del dispositivo en backend.
2. Poner B en background. A sigue a B. Verificar push en B; tocarlo debe abrir el perfil público de A.
3. A da me gusta a un entrenamiento público de B. Verificar push; tocarlo debe abrir el detalle social del entrenamiento.
4. A comenta un entrenamiento público de B. Verificar push; tocarlo debe abrir el detalle social del entrenamiento.
5. Dejar B en foreground y generar otro evento. Verificar notificación del sistema y refresco del contador. Repetir con el listado de notificaciones abierto: el listado se refresca y no se duplica la notificación local.
6. B desactiva push en Ajustes. Generar un evento: no debe llegar push y sí debe aparecer en el listado persistido.
7. B cierra sesión y C inicia sesión en el mismo dispositivo. Confirmar registro del token para C. Generar un evento dirigido a C y comprobar que el tap solo abre recursos de C. Repetir una vez con la app cerrada por completo.
8. Desactivar la red antes de cerrar sesión con B. La sesión local debe cerrarse sin bloquearse; al iniciar C con red, el registro del token debe transferirlo a C.
