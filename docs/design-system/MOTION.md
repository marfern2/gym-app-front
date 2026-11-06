# Motion language de GYmApp

## Propósito

El movimiento existe para explicar una relación espacial, confirmar una interacción o hacer legible un cambio de estado. No se usa como decoración ni para retrasar acciones frecuentes.

Cada propuesta de motion debe declarar antes de implementarse:

1. qué cambio ayuda a entender;
2. con qué frecuencia ocurre;
3. qué queda comprensible con las animaciones desactivadas;
4. cómo se verificará en un dispositivo real.

Si no hay una respuesta clara, el cambio debe ser inmediato.

## Jerarquía de decisión

1. Respetar la escala de animación y las preferencias de accesibilidad del sistema.
2. Conservar el comportamiento nativo de Material cuando ya comunica correctamente el cambio.
3. Usar MotionScheme de Material 3 cuando la versión fijada por el proyecto lo exponga y el cambio de dependencia se apruebe por separado.
4. Cuando haga falta una identidad propia, crear un token semántico de GYmApp y reutilizarlo; no dispersar literales por pantallas.
5. Elegir springs para movimiento espacial e interacción interrumpible. Usar duración y easing solo cuando el cambio tenga un recorrido temporal determinista.

El BOM actual debe comprobarse antes de usar APIs de MotionScheme. Una tarea visual no debe actualizar Compose o Material 3 de forma incidental.

## Navegación

- Entre destinos principales no se usa un desplazamiento direccional: no existe una relación padre-hijo que lo justifique.
- Una navegación padre-detalle puede expresar avance y vuelta si conserva la orientación espacial y predictive back.
- No se anima una ruta para ocultar esperas de red o carga.
- Un rediseño de navegación requiere propuesta y aprobación antes de cambiar transiciones.

## Inserción y eliminación en listas

- Animar solo el elemento afectado y el reajuste necesario de sus vecinos.
- Las listas necesitan claves estables para conservar identidad.
- Una carga inicial o una sincronización completa no desencadena una cascada de entradas.
- La eliminación debe dejar claro qué desapareció y dónde quedó el foco; no debe bloquear la siguiente acción.

## Drag y reorder

- El elemento sigue el dedo sin latencia perceptible y puede interrumpirse en cualquier momento.
- Elevación, estado tonal y posición comunican qué se mueve y cuál es el destino. No se añaden movimientos secundarios que compitan con el drag.
- Al soltar, el asentamiento parte de la posición y velocidad actuales; no salta al origen para volver a animar.
- Cancelación, back y pérdida de gesto restauran un estado válido.
- La reordenación debe seguir siendo comprensible con movimiento reducido y tener una alternativa accesible cuando corresponda.

## Bottom sheets

- AppBottomSheet y el comportamiento Material son la referencia.
- Se conserva el gesto directo, la relación con el borde inferior y el scrim.
- No se sustituyen las transiciones del componente por timings locales de una feature.
- El contenido del sheet no ejecuta una segunda animación protagonista al abrirse.

## Pressed feedback

- AppPressedIndication y appPressedState son la única respuesta pressed propia de GYmApp.
- Sus valores viven centralizados en el componente; no se copian a pantallas.
- El feedback debe comenzar con la pulsación, admitir cancelación y no impedir el click.
- No se añade scale, bounce o ripple alternativo a una feature sin revisar primero el componente compartido.

## Charts

- Una gráfica prioriza lectura y comparación. No usa rebote ni springs expresivos en barras, puntos o cifras.
- Los cambios provocados por un filtro explícito pueden interpolarse si mantienen una correspondencia inequívoca entre valores.
- El primer render, el scroll y las actualizaciones de fondo no necesitan una animación completa.
- Labels, ejes y valores finales permanecen legibles sin depender del movimiento.

## Shared transitions

- Se reservan para la misma entidad que pasa de resumen a detalle y solo cuando ambos extremos comparten una jerarquía Compose compatible.
- No cruzan Dialog, ModalBottomSheet, AndroidView ni ventanas distintas.
- Deben conservar back, deep links, restauración de estado y accesibilidad.
- Requieren propuesta visual aprobada; una transición compartida no se introduce como refactor incidental.

## Loading

- Una acción breve mantiene el feedback dentro del control que la inició.
- Para contenido estructurado, un skeleton solo aporta valor si replica la geometría real. Si la estructura es incierta, se usa un indicador indeterminado.
- Se evita mostrar y retirar un placeholder en un destello. El estado loading, su aparición y su sustitución se diseñan como un único flujo.
- Las cargas de fondo no desplazan contenido ni muestran celebraciones.

## Reduced motion

- La pantalla final debe comunicar por sí sola el cambio de estado.
- Las APIs Compose estándar deben heredar la escala del sistema. Loops propios, Canvas, media o relojes manuales deben consultar MotionDurationScale o proporcionar un estado estático equivalente.
- GIFs y contenido animado no deben ser la única forma de transmitir información; una futura política de reproducción debe contemplar pausa o frame estático.
- Desactivar movimiento no puede eliminar acciones, foco, feedback semántico ni orden de lectura.

## Restricciones de performance

- Los valores que cambian cada frame se leen en layout o draw cuando sea posible, por ejemplo en lambdas de offset o graphicsLayer.
- No se anima padding o size si solo se necesita trasladar visualmente un elemento. Si los vecinos deben reflow, el coste de layout es intencional y se documenta.
- Un evento tiene una sola motion protagonista.
- No se evalúan jank, suavidad o frame pacing con una build debug. Esas afirmaciones requieren dispositivo físico y build release-like con optimización.
- La revisión incluye recomposición, asignaciones por frame, overdraw, clipping y comportamiento al interrumpir.

## Validación

Toda modificación de motion debe comprobar:

- estado inicial, intermedio, final y cancelado;
- navegación forward/back y rotación cuando aplique;
- escala de animación desactivada;
- dark theme y font scale grande;
- captura y logs en dispositivo o emulador;
- dispositivo físico release-like cuando se hagan afirmaciones de fluidez.

Los cambios importantes siguen el workflow y las puertas de aprobación de docs/agent-tooling.md.
