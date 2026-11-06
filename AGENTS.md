# GYmApp: reglas para agentes

## Proyecto

- App Android nativa para registrar entrenamientos.
- Kotlin, Jetpack Compose y Material 3; un único módulo app.
- Package y namespace: com.mar.gym. minSdk 26.
- Flujo unidireccional: estado hacia UI y eventos hacia ViewModel.
- Código productivo en app/src/main, unit tests en app/src/test e instrumented/Compose tests en app/src/androidTest.
- Design system en app/src/main/java/com/mar/gym/ui y docs/design-system/.
- ADR en docs/adr/ cuando una decisión lo justifique. Tooling y workflow en docs/agent-tooling.md.

## Fuentes de autoridad

En conflicto, seguir este orden:

1. Código, build y tests ejecutables de la rama actual.
2. Este AGENTS.md y otros AGENTS.md más cercanos al archivo.
3. ADR aceptados y docs/design-system/, incluido MOTION.md.
4. Documentación técnica del repo.
5. Skills externas y memoria de docs/brain-plan.md.

La memoria o una referencia externa nunca sobreescriben silenciosamente la realidad del repo. Verificar que cualquier API recomendada exista en las versiones fijadas.

## Routing de skills

- Compose, state, navegación, gestos o implementación de animaciones: $compose-expert.
- Recomposition, layout/draw, estabilidad o performance Compose: $jetpack-compose-performance.
- Estrategia de tests, screenshot tests o futura visual regression: $testing-setup.
- Build, instalación, capturas y logcat en dispositivo/emulator: $native-android-test-visually.
- UX: revisar primero docs/design-system/.
- Motion: revisar primero docs/design-system/MOTION.md.

Las skills están en .agents/skills. Las reglas específicas de GYmApp permanecen fuera de ellas.

## Puertas de revisión visual

- No mezclar una migración visual con navegación, ViewModel, red o negocio.
- Un rediseño importante requiere UX audit, propuesta de diseño y propuesta de motion revisables antes de implementar.
- Ningún agente debe ejecutar silenciosamente todo el pipeline de rediseño y autoaprobar el resultado.
- Después de la aprobación: implementación Compose, revisión de performance, visual QA real y tests.

## Reglas de implementación

- No añadir dependencias, actualizar AGP/Compose ni modularizar sin necesidad demostrada y aprobación.
- Los composables no hacen red, persistencia ni autenticación.
- Los ViewModel no conocen Activity, Fragment ni componentes visuales; no reciben Context salvo abstracción imprescindible.
- Preferir StateFlow para estado observable y representar loading, content, empty y error.
- No crear BaseViewModel, BaseUseCase, BaseRepository ni jerarquías genéricas equivalentes.
- Usar tokens y componentes App* antes de introducir valores o componentes locales.
- No registrar tokens, Authorization, secretos ni datos personales. No guardar sesión en texto plano.
- Configuración de entorno fuera del código; release exige HTTPS.

## Validación

Para cambios de código o build:

    ./gradlew test
    ./gradlew lint
    ./gradlew assembleDebug

Para tooling/docs, ejecutar también los comandos anteriores si la tarea los exige y siempre:

    git diff --check

Para componentes compartidos o instrumented tests:

    ./gradlew compileDebugAndroidTestKotlin

Informar comandos y resultados. No ocultar checks omitidos o bloqueados.

## Límites operativos

- No commit, no push y no cambiar de rama.
- No ejecutar comandos destructivos.
- No cambiar backend salvo petición explícita que lo incluya.
- Mantener los cambios pequeños y limitados a la tarea.
