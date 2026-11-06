# Tooling para desarrollo asistido por agentes

## Alcance

Esta capa es repo-local. No cambia la aplicación de producción, no añade dependencias Android y no configura servicios externos. Las skills viven en .agents/skills y las reglas específicas de GYmApp permanecen en AGENTS.md y docs/.

La estructura sigue la carga progresiva de Codex: AGENTS.md contiene reglas y routing; cada skill conserva su SKILL.md y referencias.

## Auditoría de repositorios

Auditoría realizada el 2026-11-06.

| Repositorio | Licencia | Compatibilidad | Decisión |
|---|---|---|---|
| android/skills | Apache-2.0, Google LLC | Agent Skills y plugin oficial de Codex | Instalar jetpack-compose-performance y testing-setup; no copiar el catálogo completo |
| aldefy/compose-skill | MIT; receipts AOSP bajo Apache-2.0 | SKILL.md compatible con Codex | Instalar compose-expert como guía de implementación |
| rock3r/android-ux-skills | Apache-2.0 según metadata y caché pública | El formato observado es compatible | No instalar: el repositorio actual no estaba accesible y no se reconstruyen skills parciales |
| santimattius/performance-compose-skills | Apache-2.0 | Declara Codex y usa Agent Skills | No instalar: compose-audit depende de ocho skills de remediación y duplica las autoridades elegidas |
| lukas-hinterleitner/visual-app-skills | Apache-2.0 y NOTICE | Agent Skills; scripts bash, Gradle y adb | Instalar solo native-android-test-visually, resolviendo sus symlinks compartidos |

Los avisos de las skills instaladas están en .agents/THIRD_PARTY.md y .agents/licenses/.

## Skills instaladas y routing

| Tarea | Skill | Límite |
|---|---|---|
| Implementación Compose, state, navegación, gestos y APIs de animación | $compose-expert | No decide producto ni sustituye el design system |
| Recomposition, layout/draw, jank y performance Compose | $jetpack-compose-performance | Autoridad técnica oficial; medir solo en build adecuada |
| Estrategia futura de unit, UI, screenshot y E2E tests | $testing-setup | No añadir frameworks o dependencias sin aprobación |
| Build, instalación, screenshot y logcat reales | $native-android-test-visually | Validación interactiva; no es todavía visual regression automatizada |
| UX y motion de GYmApp | docs/design-system/ y MOTION.md | Autoridad local; no hay skill externa de UX instalada |

Si dos skills discrepan, prevalecen por orden: código y versión fijada, AGENTS.md, ADR/design-system, skill oficial Android, skill especializada de terceros.

## Detectabilidad en Codex

Codex descubre las skills de proyecto en .agents/skills/<nombre>/SKILL.md. Una sesión nueva debe mostrar estos nombres:

- compose-expert
- jetpack-compose-performance
- native-android-test-visually
- testing-setup

Comprobación estructural:

    find .agents/skills -mindepth 2 -maxdepth 2 -name SKILL.md -print

Cada SKILL.md debe tener frontmatter con name y description. La skill visual debe contener referencias y scripts como archivos reales, no symlinks rotos.

## Visual QA real de GYmApp

### Entorno conocido

- App Android nativa, Kotlin y Jetpack Compose.
- Gradle Wrapper y módulo app.
- applicationId: com.mar.gym.
- launcher: com.mar.gym.MainActivity.
- minSdk 26.
- Dispositivo físico o emulator aceptado para validación visual funcional.
- local.properties debe conservar la configuración descrita en README.md.

### Preflight

1. Ejecutar .agents/skills/native-android-test-visually/scripts/check-devices.sh.
2. Si hay varios dispositivos, fijar el serial con -s en todos los comandos.
3. Confirmar ./gradlew --version y la configuración debug.
4. Registrar modelo, API, resolución, tema, font scale y variante.
5. No arrancar automáticamente un emulator ni borrar datos de la app sin petición explícita.

### Build, launch y evidencia

    ./gradlew :app:installDebug
    adb -s <serial> shell am start -n com.mar.gym/.MainActivity

Esperar a BUILD SUCCESSFUL y después a la actividad visible; no capturar el splash. Si no hay un marcador propio de la pantalla, comprobar ActivityTaskManager y mResumedActivity.

    .agents/skills/native-android-test-visually/scripts/snap.sh -s <serial> /tmp/gymapp-visual-qa/<tarea>/screen.png
    .agents/skills/native-android-test-visually/scripts/capture-logs.sh -s <serial> --app com.mar.gym /tmp/gymapp-visual-qa/<tarea>/logcat.txt

Los artefactos de trabajo viven en /tmp. Una captura aprobada puede copiarse a captures/visual-qa/<tarea>/, que está ignorado por Git, si el mantenedor la necesita. Nunca guardar tokens, credenciales, datos personales o screenshots de cuentas reales.

El informe debe incluir:

- build y commit de trabajo observado, sin crear commit;
- dispositivo y configuración;
- ruta y estado exactos de la pantalla;
- screenshots y rango temporal de logs;
- esperado frente a observado;
- verdict claro: pass, pass con observaciones o fail;
- limitaciones, datos mock/fake y pasos no cubiertos.

Un error de red solo es defecto visual si afecta al estado que se está validando. No se modifica ni se arranca backend como parte de este workflow.

### Matriz mínima

- dark theme, que es la referencia principal;
- light theme funcional;
- ancho estrecho;
- font scale 1.5;
- loading, content, empty y error cuando la pantalla los soporte;
- interacción relevante y back;
- reduced motion para cualquier cambio animado.

La fluidez o el frame pacing no se juzgan en debug. Para motion/performance se usa un dispositivo físico y una build release-like/profileable acordada, sin credenciales de producción.

## Visual regression futura

native-android-test-visually proporciona evidencia real pero no golden tests deterministas. La siguiente fase debe usar $testing-setup para evaluar screenshot testing compatible con la versión AGP fijada, decidir device-based frente a host-based, definir golden ownership y CI, y añadirlo en una tarea separada. No se ha añadido ahora ningún plugin, dependencia ni baseline.

## Workflow de una tarea visual

1. Consultar AGENTS.md, código, ADR y design-system; brain es contexto secundario.
2. Hacer UX audit del estado actual con evidencia.
3. Preparar una propuesta de diseño acotada.
4. Preparar la propuesta de motion usando MOTION.md.
5. Obtener aprobación explícita si cambia dirección visual, navegación, componentes compartidos o motion relevante.
6. Implementar en Compose con $compose-expert.
7. Revisar performance con $jetpack-compose-performance.
8. Ejecutar visual QA real con $native-android-test-visually.
9. Ejecutar tests automatizados existentes y los específicos aprobados.
10. Entregar un estado PR ready con diff, evidencia, riesgos y comandos; no crear commit ni push.

En un rediseño importante, una misma ejecución no puede atravesar silenciosamente todo el pipeline. Hay una puerta humana obligatoria después de los pasos 2 a 4 y antes de implementar. La validación visual también se entrega como evidencia revisable, no como autoaprobación del diseño.

## Mantenimiento

- No editar una skill vendorizada para introducir reglas de GYmApp.
- Antes de actualizar: revisar diff upstream, licencia, frontmatter, scripts y solapamientos.
- Actualizar una skill por vez y validar su detección.
- Registrar fuente, licencia y fecha en .agents/THIRD_PARTY.md.
- No instalar repos completos ni skills que no tengan un trigger concreto.
