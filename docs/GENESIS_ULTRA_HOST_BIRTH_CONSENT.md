# Genesis Ultra — consentimiento explícito del host

## Frontera

El consentimiento local confirma que el host revisó y aprobó un candidato Genesis Ultra exacto.

```text
consentimiento del host
!= autorización del Guardián
!= firma del Body
!= propiedad sobre Morimil
!= nacimiento comprometido
```

El registro resultante mantiene obligatoriamente:

```text
birthCommitAuthorized = false
```

La activación atómica seguirá requiriendo evidencia completa y una frontera posterior que consuma este consentimiento junto con el candidato exacto.

## Presentación obligatoria

La futura interfaz debe presentar y confirmar, sin reescrituras silenciosas:

- `candidateDigest` completo;
- `instanceId`;
- nombre canónico del compañero;
- código corto formado por los últimos 12 caracteres hexadecimales del `candidateDigest`;
- decisión exacta `approve_birth`;
- propósito `genesis_ultra_atomic_birth`;
- presencia local interactiva del usuario.

Un booleano genérico o una aprobación no ligada a esos valores se rechaza.

## Vinculación

El consentimiento autenticado queda ligado a:

- `candidateDigest`;
- `instanceId`;
- nombre canónico;
- Seed root verificado;
- `bodyId` inicial;
- `guardianId`;
- epoch público exacto del Guardián;
- instante de consentimiento;
- instante de expiración.

Cambiar cualquiera de esos valores invalida la carga del consentimiento.

## Vigencia

La ventana máxima es de dos minutos y nunca puede superar la expiración de la prueba de posesión corporal del candidato.

Estados:

```text
ABSENT
READY
EXPIRED
INCONSISTENT
```

- `READY`: registro autenticado y aún vigente;
- `EXPIRED`: registro íntegro cuya ventana terminó;
- `INCONSISTENT`: falta la KEK, falta el registro, el ciphertext cambió o la estructura no valida;
- `ABSENT`: no existe registro ni KEK.

Un consentimiento vencido no se sustituye automáticamente. Debe revocarse explícitamente antes de iniciar otra ceremonia.

## Protección local

El registro completo se cifra y autentica con AES-256-GCM mediante una clave dedicada de Android Keystore:

```text
com.morimil.app.genesis.ultra.host.birth.consent.kek.v1
```

Las preferencias externas no contienen el nombre, `instanceId`, `bodyId` ni el contenido del consentimiento en texto plano. Solo conservan metadatos del formato, el digest y el ciphertext.

## Fallo cerrado

Se rechaza:

- aprobación sin presencia local confirmada;
- decisión distinta de `approve_birth`;
- código corto incorrecto;
- nombre, instancia o digest presentados que no coincidan;
- candidato estructuralmente inválido;
- prueba de posesión expirada;
- nacimiento ya comprometido;
- segundo consentimiento mientras exista otro registro;
- sustitución de Seed, Body o Guardián;
- manipulación del registro cifrado;
- pérdida de la clave de Android Keystore.

## Revocación

`revokeBeforeBirth(expectedCandidateDigest)` exige:

- nacimiento durable todavía `ABSENT`;
- digest exacto del candidato aprobado;
- registro descifrable y consistente.

La revocación elimina tanto el registro como la KEK dedicada. No modifica la raíz corporal, el trust anchor del Guardián, la Seed ni la memoria.

## Fuera de alcance

Esta fase no:

- modifica onboarding;
- muestra todavía la pantalla de confirmación;
- construye la evidencia atómica completa;
- llama `GenesisUltraAtomicBirthActivationCoordinator.activate()`;
- escribe memoria canónica;
- activa motores deliberativo o metacognitivo;
- convierte al Guardián o al host en propietario de Morimil.
