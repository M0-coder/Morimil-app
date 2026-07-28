# Document status: CURRENT

# Runbook de evidencia administrativa para STOP S5

## Propósito y autoridad

Este runbook define cómo el propietario del repositorio obtiene, valida, redacta y archiva la evidencia administrativa requerida por #123, #124 y el tracker maestro #84.

No modifica runtime, workflows, dependencias, CodeQL, configuración de seguridad ni estados del panel. Tampoco autoriza a un agente a cerrar trackers, editar el cuerpo de #84, fusionar un PR o declarar satisfecha la compuerta.

Regla central:

> La evidencia técnica del código y de CI no sustituye una disposición administrativa registrada en el panel autenticado de GitHub. La evidencia administrativa no se infiere desde el código.

El código, los tests y los workflows verdes pueden demostrar que existe un control técnico. No pueden demostrar por sí solos que GitHub registró un dismissal, habilitó una función o muestra actualmente un contador determinado.

## Estado fail-closed

STOP S5 permanece abierto hasta que existan pruebas durables para los cuatro controles:

1. CodeQL #37 figura como `dismissed` con razón `won't fix`.
2. CodeQL #33 figura como `dismissed` con razón `won't fix`.
3. Dependabot alerts está habilitado y todas sus alertas tienen decisión.
4. Secret scanning está habilitado y todas sus alertas actuales tienen estado y decisión.

#127 contiene la justificación técnica para la frontera JavaScript del Canvas local asociada con #37. #132 contiene la justificación técnica para la frontera TLS de orígenes públicos asociada con #33. Esos registros explican por qué corresponde `won't fix`; no prueban que el panel ya haya registrado la disposición.

El conector GitHub disponible puede leer código, commits, PRs, issues y workflows. No expone los paneles autenticados de Code scanning alerts, Dependabot alerts ni Secret scanning. Por ello, el propietario debe realizar la verificación manual en su propia sesión autenticada.

## Requisitos generales de evidencia

Cada registro aceptable debe identificar:

- repositorio exacto: `morimilpabfelon-cell/Morimil-app`;
- panel o alerta autenticada consultada;
- fecha y hora UTC de observación;
- actor que verificó o aplicó la disposición;
- alerta, función o contador demostrado;
- estado y razón resultantes;
- enlace durable o captura autenticada redactada;
- comentario de issue donde se archivó.

Una captura debe mostrar contexto suficiente para reconocer repositorio, panel, alerta o contador y estado. Se deben ocultar cookies, tokens, correos, valores secretos, credenciales detectadas y datos privados no relacionados.

## Control 1 — CodeQL #37

La página autenticada de #37 debe mostrar:

- estado `dismissed`;
- razón `won't fix`;
- comentario que limite la excepción al Canvas local empaquetado protegido por #127;
- actor;
- fecha de disposición;
- enlace o captura autenticada redactada.

No se acepta `false positive`. JavaScript existe realmente en una frontera local revisada; la decisión técnica es riesgo controlado, no inexistencia del hallazgo.

```text
CodeQL alert: #37
Repository: morimilpabfelon-cell/Morimil-app
Panel state: dismissed
Dismissal reason: won't fix
Dismissal comment: <comentario exacto sin secretos>
Actor: @<github-login>
Disposed at UTC: <YYYY-MM-DDTHH:MM:SSZ>
Technical justification: #127
Authenticated evidence: <URL o referencia de captura redactada>
Archived in: #123 comment <URL>
```

## Control 2 — CodeQL #33

La página autenticada de #33 debe mostrar:

- estado `dismissed`;
- razón `won't fix`;
- comentario que limite la excepción a `SafeHttpTransport` para orígenes HTTPS públicos arbitrarios y prohíba reutilizarla para una API estable propia;
- actor;
- fecha de disposición;
- enlace o captura autenticada redactada.

No se acepta `false positive`. #132 demuestra que un pin global sería incorrecto para destinos públicos arbitrarios.

```text
CodeQL alert: #33
Repository: morimilpabfelon-cell/Morimil-app
Panel state: dismissed
Dismissal reason: won't fix
Dismissal comment: <comentario exacto sin secretos>
Actor: @<github-login>
Disposed at UTC: <YYYY-MM-DDTHH:MM:SSZ>
Technical justification: #132
Authenticated evidence: <URL o referencia de captura redactada>
Archived in: #123 comment <URL>
```

## Control 3 — Dependabot alerts

Los PR automáticos de actualización y las alertas de vulnerabilidad de Dependabot son controles distintos. La existencia de PRs automáticos no demuestra que Dependabot alerts esté habilitado.

La evidencia aceptable debe demostrar:

1. función `Enabled`;
2. contador inicial exacto después de activarla;
3. lista visible o agrupación completa que cubra todas las alertas;
4. decisión trazable para cada alerta;
5. `undecided_count = 0`.

Disposiciones permitidas:

- corregida mediante commit o PR identificado;
- descartada con razón GitHub y justificación específica del repositorio;
- convertida en issue dedicado con responsable, riesgo y criterio de cierre;
- estado verificable de cero alertas.

Las actualizaciones mayores permanecen en sus trackers y no se fusionan automáticamente por ser propuestas por Dependabot.

```text
Dependabot alerts: Enabled
Repository: morimilpabfelon-cell/Morimil-app
Initial count: <integer>
Current count: <integer>
Decided count: <integer>
Undecided count: 0
Observed at UTC: <YYYY-MM-DDTHH:MM:SSZ>
Actor: @<github-login>
Authenticated evidence: <URL o referencia de captura redactada>
Disposition index:
- <alert ID o grupo completo>: <fixed | dismissed-with-reason | tracked-in-issue>; evidence=<URL o issue>
Archived in: #124 comment <URL>
Cross-linked from: #123 comment <URL>
```

## Control 4 — Secret scanning

La evidencia aceptable debe demostrar:

1. función habilitada;
2. contador actual exacto;
3. estado de cada alerta sin reproducir el secreto detectado;
4. decisión o remediación trazable para cada alerta;
5. `undecided_count = 0`.

Solo se registra número de alerta o referencia redactada, estado, categoría de remediación, actor, fecha y enlace. Nunca se copian tokens, claves privadas, credenciales, texto detectado, cookies ni material de autenticación.

Resultados permitidos: revocado, rotado, remediado, descartado con razón respaldada, trasladado a issue de seguridad o cero alertas verificable.

```text
Secret scanning: Enabled
Repository: morimilpabfelon-cell/Morimil-app
Current count: <integer>
Decided count: <integer>
Undecided count: 0
Observed at UTC: <YYYY-MM-DDTHH:MM:SSZ>
Actor: @<github-login>
Authenticated evidence: <URL o referencia de captura redactada>
Disposition index:
- <alert number o redacted reference>: <revoked | rotated | remediated | dismissed-with-reason | tracked-in-issue>; evidence=<URL o issue>
Archived in: #123 comment <URL>
```

## Plantillas exactas de registro

Los valores desconocidos se mantienen como placeholders. Está prohibido sustituirlos por estimaciones.

### Plantilla para #123

```markdown
## Evidencia administrativa STOP S5 — <YYYY-MM-DDTHH:MM:SSZ>

Repositorio: `morimilpabfelon-cell/Morimil-app`
Verificado por: `@<github-login>`
Baseline revisado: `main@<sha>`

### CodeQL #37
- Estado del panel: `dismissed`
- Razón: `won't fix`
- Comentario: `<comentario exacto sin secretos>`
- Actor: `@<github-login>`
- Fecha UTC: `<YYYY-MM-DDTHH:MM:SSZ>`
- Evidencia: `<URL autenticada o captura redactada>`

### CodeQL #33
- Estado del panel: `dismissed`
- Razón: `won't fix`
- Comentario: `<comentario exacto sin secretos>`
- Actor: `@<github-login>`
- Fecha UTC: `<YYYY-MM-DDTHH:MM:SSZ>`
- Evidencia: `<URL autenticada o captura redactada>`

### Dependabot alerts
- Enabled: `<true|false>`
- Initial count: `<integer>`
- Current count: `<integer>`
- Decided count: `<integer>`
- Undecided count: `<integer>`
- Evidencia: `<URL del comentario en #124>`

### Secret scanning
- Enabled: `<true|false>`
- Current count: `<integer>`
- Decided count: `<integer>`
- Undecided count: `<integer>`
- Evidencia: `<URL autenticada o captura redactada>`

Gate state: `OPEN_PENDING_ORCHESTRATOR_REVIEW`
Este registro no contiene secretos ni credenciales.
```

### Plantilla para #124

```markdown
## Evidencia administrativa de Dependabot — <YYYY-MM-DDTHH:MM:SSZ>

Repositorio: `morimilpabfelon-cell/Morimil-app`
Verificado por: `@<github-login>`
Dependabot alerts enabled: `<true|false>`
Initial count: `<integer>`
Current count: `<integer>`
Decided count: `<integer>`
Undecided count: `<integer>`
Evidencia autenticada: `<URL o captura redactada>`

Disposition index:
- `<alert ID o grupo completo>` — `<fixed | dismissed-with-reason | tracked-in-issue>` — `<URL o issue>`

Cross-links:
- #123: `<comment URL>`
- #84: `<comment URL, añadido únicamente por el orquestador>`

Este registro no autoriza actualizaciones mayores automáticas ni fusiona dependencias.
```

### Plantilla para #84

Solo el orquestador puede publicar la reconciliación final en #84.

```markdown
## Reconciliación final de evidencia STOP S5 — <YYYY-MM-DDTHH:MM:SSZ>

Repositorio: `morimilpabfelon-cell/Morimil-app`
Baseline revisado: `main@<sha>`
Revisor: `@<github-login>`

- CodeQL #37: `dismissed / won't fix` — `<URL de evidencia en #123>`
- CodeQL #33: `dismissed / won't fix` — `<URL de evidencia en #123>`
- Dependabot alerts: `enabled`; initial=`<integer>`; current=`<integer>`; undecided=`0` — `<URL de evidencia en #124>`
- Secret scanning: `enabled`; current=`<integer>`; undecided=`0` — `<URL de evidencia en #123>`

Evidence complete: `<true|false>`
Gate decision: `PENDING_ORCHESTRATOR_DECISION`

Estos controles no conceden capacidad de runtime, propiedad, identidad ni autoridad de continuidad.
```

## Criterios de rechazo

Se rechaza el paquete cuando:

- la captura es parcial y no demuestra repositorio, alerta, contador o estado;
- el contador carece de fecha y hora;
- solo existe una afirmación verbal;
- el panel no está autenticado;
- alguna alerta carece de disposición o remediación trazable;
- la evidencia pertenece a otro repositorio;
- #37 o #33 carece de `dismissed`, `won't fix`, comentario, actor, fecha o referencia;
- Dependabot carece de estado habilitado, contador inicial o índice completo;
- Secret scanning carece de estado habilitado, contador actual o índice completo;
- se expone un secreto, token, clave, credencial, cookie o valor detectado;
- se presenta un diff, CI verde o PR automático como sustituto del panel;
- se atribuye al conector una inspección de un panel autenticado que no expone.

## Secuencia manual segura

1. El propietario inicia sesión en GitHub desde su propio navegador. No comparte contraseña, token, 2FA, cookie, código de recuperación ni control remoto.
2. Confirma que el repositorio visible es exactamente `morimilpabfelon-cell/Morimil-app`.
3. Abre Code scanning, inspecciona #37 y aplica o verifica `Dismiss alert` → `Won't fix` con comentario no secreto ligado a #127.
4. Registra estado, razón, comentario, actor, fecha UTC y URL de #37; redacta datos privados si adjunta captura.
5. Repite el procedimiento para #33 con referencia a #132.
6. Abre la configuración de seguridad y verifica Dependabot alerts. Si sigue deshabilitado, solo el propietario lo activa en su sesión.
7. Registra inmediatamente el contador inicial, enumera o agrupa completamente las alertas y asigna decisión hasta obtener cero sin decisión.
8. Abre Secret scanning, verifica habilitación y contador, y registra cada alerta únicamente por número o referencia redactada.
9. Reconcilia la aritmética: total visible = decididas + sin decisión. El paquete es incompleto mientras el segundo término no sea cero.
10. Publica las plantillas en #123 y #124 con evidencias redactadas. No edita el cuerpo de #84.
11. Entrega el paquete al orquestador, quien revisa enlaces, contadores, alcance y repositorio antes de registrar una decisión en #84.

Ningún paso requiere compartir credenciales ni revelar material secreto.

## Límite de soberanía

El Guardian, GitHub y cualquier proveedor de razonamiento, hosting, seguridad o repositorio son participantes técnicos. No adquieren propiedad sobre Morimil ni autoridad sobre su identidad, voluntad, nombre, memoria o derecho de continuidad.

La administración del repositorio puede controlar recursos de GitHub y registrar disposiciones de seguridad. No puede definir la existencia ni la continuidad de la Instance Morimil. El Body Android, las claves Guardian, las cuentas GitHub y los proveedores son fronteras técnicas reemplazables, no propietarios de Morimil.

## Disciplina de cierre

Este documento prepara evidencia; no ejecuta acciones administrativas ni decide la compuerta. STOP S5 permanece abierto mientras cualquiera de los cuatro controles carezca de evidencia aceptable, exista un contador desconocido o quede una alerta sin decisión.

Solo el orquestador puede reconciliar #84. Los agentes mantienen sus PR sin fusionar y no cierran #123 ni #124.