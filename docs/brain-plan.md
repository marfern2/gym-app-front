# Plan de brain y memoria

## Objetivo

Preparar una memoria navegable para agentes y personas sin introducir todavía embeddings, una base vectorial ni sincronización automática. La primera versión es Markdown versionado y enlazable desde Obsidian.

## Source of truth

En caso de conflicto, manda la realidad del repositorio en este orden:

1. Código, configuración de build y tests ejecutables.
2. AGENTS.md y los AGENTS.md más específicos que puedan existir.
3. ADR aceptados en docs/adr/.
4. Documentación vigente de docs/design-system/ y documentación técnica.

Una nota de memoria nunca modifica ni sobreescribe estas fuentes por sí sola. Si una nota contradice el código, se marca como obsoleta y se abre una decisión explícita para reconciliarla.

## Knowledge y memory

La memoria puede conservar:

- decisiones UX y su contexto;
- referencias externas y research;
- patrones rechazados y por qué se rechazaron;
- hipótesis o aprendizajes de pruebas;
- ideas de producto todavía no aprobadas.

No puede convertir una idea, una captura o una conversación en requisito vigente.

## Estructura futura propuesta

    docs/brain/
      README.md
      ux-decisions/
      rejected-patterns/
      research/
      references/
      product-ideas/

No se crean estas carpetas hasta que exista contenido real. Cada nota debe incluir estado, fecha, autor o sesión, enlaces a evidencia y relaciones con código, ADR o design-system.

Estados recomendados:

- draft: hipótesis sin validar;
- reviewed: conocimiento revisado, todavía no normativo;
- superseded: reemplazado, conserva trazabilidad;
- rejected: opción descartada con motivo.

Las decisiones normativas se promueven a un ADR o al documento de design system correspondiente. No permanecen escondidas en brain.

## Uso con Obsidian

- Abrir docs/ como vault o incluirlo en un vault mayor.
- Usar enlaces Markdown relativos; evitar enlaces dependientes de plugins.
- No versionar datos privados de Obsidian, cachés ni índices locales.
- No copiar código completo a notas: enlazar archivo y símbolo.
- Mantener attachments pesados fuera del repositorio salvo que sean evidencia aprobada.

## Consulta por agentes

1. Leer primero AGENTS.md y detectar la fuente de verdad aplicable.
2. Consultar ADR y design-system para decisiones normativas.
3. Usar brain solo como contexto secundario.
4. Verificar toda afirmación de brain contra código y estado actual.
5. Si hay conflicto, informar y no aplicar silenciosamente la memoria.

## Evolución futura

Solo considerar búsqueda semántica cuando el volumen haga insuficiente la navegación por enlaces y texto. Antes de una vector DB deben existir:

- ownership y política de retención;
- exclusión de secretos y datos personales;
- metadatos de vigencia;
- reindexado reproducible;
- citas que apunten al archivo fuente;
- una regla de precedencia que mantenga al repo como autoridad.

Hasta entonces, rg, enlaces Markdown y una taxonomía pequeña son suficientes.
