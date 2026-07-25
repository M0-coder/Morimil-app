# Autorización durable del nacimiento Genesis Ultra

## Propósito

El nacimiento Genesis Ultra no queda completo únicamente porque sus documentos y firmas sean válidos. También debe conservarse qué candidato exacto fue aprobado por el host y qué autorización permitió entrar a la transacción de nacimiento.

La evidencia durable se almacena en `genesis_ultra_birth_authorization` y contiene:

- `candidateDigest`;
- `consentDigest`;
- `birthStateDigest`;
- `receiptDigest`;
- Body inicial;
- Guardián y época de clave;
- ventana temporal de autorización;
- `authorizationDigest`;
- bytes JSON canónicos y su SHA-256.

El registro es evidencia pública de auditoría. No contiene claves privadas, contraseñas, secretos del Guardián ni material de Android Keystore.

## Atomicidad

La activación escribe dentro de una única transacción Room:

1. artefactos y journal verificados del nacimiento;
2. marcador de nacimiento;
3. recibo de autorización ligado al consentimiento;
4. recuperación y auditoría del nacimiento recién persistido;
5. primer evento canónico posterior al nacimiento, con secuencia 1.

Si falla la firma Body, la recuperación, el recibo o la memoria, SQLite revierte todas las filas. No puede quedar un nacimiento comprometido sin autorización durable o sin memoria canónica de secuencia 1.

## Estado después de reiniciar

`GenesisUltraAuthorizedBirthStateAudit` exige una correspondencia uno a uno:

```text
nacimiento ABSENT + autorización 0 = ABSENT
nacimiento COMMITTED + autorización válida 1 = COMMITTED
cualquier otra combinación = INCONSISTENT
```

El coordinador de preparación utiliza este estado reforzado. Un commit antiguo o insertado por una ruta interna sin recibo de consentimiento no se presenta como nacimiento completo.

## Migración 12 → 13

La migración crea la tabla y sus índices únicos, pero no reconstruye autorizaciones para nacimientos anteriores. Fabricar `candidateDigest`, `consentDigest` o consentimiento histórico sería una declaración falsa.

Una instalación que ya contenga un nacimiento experimental sin autorización durable será clasificada como `INCONSISTENT` y requerirá una estrategia de recuperación explícita en una fase posterior.

## Fronteras

Esta fase no:

- conecta Genesis Ultra con onboarding;
- ejecuta una ceremonia real del Guardián;
- importa memoria heredada;
- convierte al host o al Guardián en propietario;
- activa los motores deliberativo o metacognitivo;
- elimina todavía las entradas internas de persistencia usadas por pruebas y recuperación.

La ruta operativa de activación continúa exigiendo `GenesisUltraAuthorizedAtomicBirth`. Las entradas de persistencia de bajo nivel no producen por sí mismas un estado de nacimiento autorizado y completo.
