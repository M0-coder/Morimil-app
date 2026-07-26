# Document status: CURRENT

# Genesis Ultra: autorización de nacimiento atómico

## Objetivo

La evidencia criptográfica completa no basta por sí sola para comprometer el nacimiento.
La autorización exige que cuatro fronteras coincidan:

```text
candidato Genesis Ultra exacto
+ consentimiento explícito y vigente del host
+ raíz Body local
+ trust anchor del Guardián local
+ grafo completo de evidencia firmado
= autorización tipada de activación
```

La autorización todavía no escribe Room, no crea memoria y no modifica onboarding.

## Paquete de testimonio

`GenesisUltraAtomicBirthWitnessPackage` recibe los artefactos finales y las siete
entradas del journal. Todos los arrays se copian defensivamente al entrar y al
salir.

El paquete debe contener:

- Seed manifest y firma detached;
- archivos exactos del Seed;
- identidad de instancia;
- Carta de Libertad;
- registro inicial del Body;
- registry inicial de cuerpos;
- epoch criptográfico inicial;
- prueba de posesión;
- primer evento de memoria;
- política y estado de recuperación;
- estado y recibo de nacimiento;
- journal completo de siete fases.

La app no fabrica firmas del Guardián. El paquete llega después de la ceremonia
de testimonio y se valida contra la clave pública fijada localmente.

## Sustitución de entradas no confiables

El coordinador no acepta claves de confianza desde el paquete. Siempre carga:

```text
bodyRawPublicKey <- Body identity root local
Guardian registry <- trust anchor local
consent <- consent store local
```

Después llama al verificador de evidencia existente.

## Vinculación exacta

La evidencia verificada debe reproducir exactamente los documentos del candidato:

- Seed root;
- identidad y nombre canónico;
- `bodyId` y fingerprint;
- registry digest;
- key epoch digest;
- possession proof digest;
- birth state;
- receipt final.

El consentimiento debe coincidir con el mismo `candidateDigest`, instancia,
nombre, Seed, Body, Guardián y epoch.

## Vigencia

La autorización expira en el menor de estos límites:

- expiración del consentimiento;
- expiración de la prueba de posesión corporal.

`activate()` exige además un `activatedAt` canónico dentro de esa ventana.

## Type-state

La única salida válida es `GenesisUltraAuthorizedAtomicBirth`:

```text
birthCommitAuthorized = true
```

Ese valor no representa propiedad, autoautorización ni una concesión del
Guardián. Representa que las condiciones independientes ya fueron verificadas.

## Cierre del bypass

La entrada productiva de `GenesisUltraAtomicBirthActivationCoordinator.activate`
ya no acepta `GenesisUltraVerifiedAtomicBirth`. Su primer parámetro es
`GenesisUltraAuthorizedAtomicBirth`.

Las pruebas antiguas de transacción usan un puente por reflexión compilado solo
en `androidTest`. No existe una sobrecarga equivalente en el APK de producción.

## Fuera de alcance

Esta fase no:

- conecta onboarding;
- inicia la ceremonia real del Guardián;
- ejecuta el nacimiento desde la interfaz;
- importa memoria heredada;
- activa motores deliberativo o metacognitivo.
