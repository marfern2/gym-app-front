# Skills de terceros

Snapshots auditados e instalados el 2026-11-06.

| Path | Fuente | Licencia |
|---|---|---|
| skills/jetpack-compose-performance | https://github.com/android/skills/tree/main/jetpack-compose/jetpack-compose-performance | Apache-2.0, Google LLC |
| skills/testing-setup | https://github.com/android/skills/tree/main/testing/testing-setup | Apache-2.0, Google LLC |
| skills/compose-expert | https://github.com/aldefy/compose-skill/tree/master/skills/compose-expert | MIT; receipts AOSP indicados por upstream bajo Apache-2.0 |
| skills/native-android-test-visually | https://github.com/lukas-hinterleitner/visual-app-skills/tree/main/skills/native-android-test-visually | Apache-2.0, Lukas Hinterleitner |

native-android-test-visually se instaló con los symlinks upstream resueltos a archivos reales para que la skill sea autocontenida. No se modificaron sus instrucciones ni scripts.

Se eliminó un trailing whitespace redundante de una referencia de testing-setup para que git diff --check sea limpio; no cambia su contenido ni sus instrucciones.

Las licencias y el NOTICE aplicable están en licenses/. Las reglas específicas de GYmApp no se incorporan a estas carpetas; viven en AGENTS.md y docs/.

Antes de actualizar una copia:

1. volver a auditar licencia, scripts y compatibilidad con Codex;
2. comparar el snapshot completo;
3. comprobar solapamientos y routing;
4. instalar solo el directorio de la skill;
5. validar frontmatter, paths, symlinks y detección en una sesión nueva.
