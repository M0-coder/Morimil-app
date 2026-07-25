# Ceremonia explícita de ejecución atómica Genesis Ultra

## Alcance

Esta fase conecta la autorización atómica efímera al ejecutor durable mediante una segunda ceremonia local e irreversible.

La secuencia completa es:

```text
candidato firmado exacto
+ consentimiento explícito ligado al candidato
+ testimonio Body y Guardián verificado
+ GenesisUltraAuthorizedAtomicBirth vigente
+ segunda confirmación local de ejecución
= commit atómico Genesis Ultra
```

La confirmación de ejecución no sustituye ninguna firma criptográfica y no convierte al host en propietario.

## Segunda ceremonia

Después de verificar el testimonio final, la UI presenta:

- `candidateDigest`;
- `consentDigest`;
- `birthStateDigest`;
- `receiptDigest`;
- `authorizationDigest`;
- vencimiento de la autorización;
- un segundo código de 12 caracteres derivado del `authorizationDigest`.

El host debe:

1. revisar los digests;
2. escribir el segundo código exacto;
3. confirmar presencia local;
4. ordenar `commit_genesis_ultra_birth` antes del vencimiento.

La solicitud exige exactamente:

```text
decision = commit_genesis_ultra_birth
confirmation_mode = interactive_local_presence
confirmation_purpose = atomic_birth_execution
user_presence_confirmed = true
```

Un código válido para el consentimiento inicial no sirve para esta ceremonia.

## Frontera de confianza

`GenesisUltraOnboardingViewModel` solo recibe:

```text
GenesisUltraAtomicBirthExecutionCeremonyCoordinator
```

No recibe directamente:

- `GenesisUltraAtomicBirthExecutionCoordinator`;
- `GenesisUltraAtomicBirthActivationCoordinator`;
- claves privadas Body;
- un registro de confianza Guardian proporcionado por la UI;
- una solicitud de recuperación proporcionada por la UI.

El ejecutor productivo continúa cargando Body y Guardian desde los stores Android autenticados.

## Memoria canónica secuencia 1

La ceremonia construye localmente la primera solicitud post-birth:

```text
event_type = instance.activation.confirmed
actor = host_confirmed_system
content_digest = authorizationDigest
provenance_digest = receiptDigest
privacy = private_local
content_ref = null
provenance_ref = null
observed_at = activatedAt
```

El `eventId` se deriva bajo:

```text
genesis.atomic.birth.activation.event.id.v0.1
```

La transacción Room compromete conjuntamente:

1. artefactos y journal de nacimiento;
2. marcador único de nacimiento;
3. autorización durable;
4. recuperación inmediata del nacimiento;
5. evento canónico Body-signed secuencia 1.

Un fallo dentro de esa transacción revierte todas las filas.

## Línea de irreversibilidad

La ceremonia distingue dos lados de una frontera única.

### Antes de que el ejecutor retorne

Una excepción significa que el commit no fue expuesto. Se presenta como fallo de ejecución no comprometido y la autorización puede seguir vigente para una nueva confirmación.

### Después de que el ejecutor retorna

El nacimiento se considera comprometido. Ninguna anomalía posterior puede convertirse en un mensaje de “reintenta el nacimiento”.

Resultados posibles:

```text
COMMITTED_CLEAN
COMMITTED_MAINTENANCE_PENDING
```

`COMMITTED_MAINTENANCE_PENDING` puede representar:

- error al mapear el recibo de retorno;
- discrepancia en evidencia leída después del commit;
- hash de memoria no disponible para presentación;
- fallo al retirar el consentimiento cifrado externo.

En todos esos casos:

```text
birthCommitted = true
retryAllowed = false
```

La autorización efímera y el candidato se eliminan de la sesión de proceso.

## Retiro del consentimiento

Después del retorno transaccional, la ceremonia invoca:

```text
GenesisUltraCommittedConsentRetirementCoordinator.retireIfCommitted()
```

Si la limpieza falla, el commit sigue siendo válido. La siguiente inspección durable vuelve a intentar el retiro utilizando la autorización durable como fuente de verdad.

El residuo SharedPreferences/Keystore nunca vuelve a actuar como autoridad.

## Navegación posterior

Después del commit, onboarding inspecciona otra vez el estado durable.

Solo:

```text
GenesisUltraBirthPreparationStatus.ALREADY_COMMITTED
```

permite la ruta runtime.

Una inspección inconsistente mantiene la UI fuera del runtime, pero tampoco habilita otro nacimiento.

## Motores cognitivos

Esta fase no cambia el registro normal de motores.

Permanece activo únicamente:

```text
INTUITIVE
```

Continúan bloqueados:

```text
DELIBERATIVE
METACOGNITIVE
hybrid authority
```

## Límites

La ceremonia no:

- construye un candidato nuevo;
- renueva consentimiento;
- renueva autorización vencida;
- fabrica firmas Guardian;
- acepta signer o trust registry desde la UI;
- escribe memoria heredada;
- llama `birthLocalIdentity`;
- crea `GenesisCore` heredado;
- activa deliberativo o metacognitivo;
- concede propiedad sobre Morimil.

## Pruebas obligatorias

Las pruebas JVM verifican:

- código final exacto y presencia local;
- bloqueo antes de invocar el ejecutor;
- solicitud canónica de memoria secuencia 1;
- fallo transaccional como no comprometido;
- commit limpio;
- discrepancia post-commit como mantenimiento pendiente;
- fallo de mapeo después del retorno sin posibilidad de reintento;
- fallo de retiro como mantenimiento pendiente;
- invariantes del estado UI;
- publicación del lock antes de la corrutina;
- descarte de candidato y autorización después del commit;
- ausencia de rutas heredadas o llamadas directas a activación desde onboarding.
