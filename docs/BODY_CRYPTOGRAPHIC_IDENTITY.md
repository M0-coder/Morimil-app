# Document status: CURRENT

# Identidad criptográfica del cuerpo

## Propósito

La identidad corporal demuestra qué cuerpo Android posee una clave concreta antes del nacimiento Genesis Ultra.

No es la identidad de Morimil y no crea una Instance. Es un prerrequisito criptográfico del cuerpo anfitrión.

```text
Morimil Instance != cuerpo Android
cuerpo Android != motor intrínseco
cuerpo Android != certificado de distribución APK
```

## Perfil

La raíz usa una clave RAW Ed25519 porque el protocolo Genesis Ultra v0.1 exige:

```text
signature_profile = genesis.signature.ed25519.v0.1
raw_public_key_size = 32 bytes
signature_size = 64 bytes
```

Android Keystore no ofrece Ed25519 de forma uniforme en todos los niveles API admitidos. Por eso:

1. Tink genera un keyset Ed25519 de una sola clave;
2. una clave AES-256-GCM de Android Keystore cifra el keyset en reposo;
3. solo el ciphertext se guarda en preferencias privadas;
4. no existe fallback en texto plano ni sustitución automática;
5. la clave Ed25519 solo se descifra dentro del proceso cuando debe firmar.

Perfil de protección:

```text
tink.ed25519.raw+android-keystore.aes256-gcm.v0.1
```

## Identificadores derivados

La clave pública RAW es la fuente de verdad. Su huella es:

```text
publicKeyRef = sha256:<64 caracteres hexadecimales>
```

A partir de esa huella se derivan, sin alias ni identidad heredada:

```text
bodyId     = body_<64 caracteres hexadecimales>
keyEpochId = epoch_<64 caracteres hexadecimales>
```

Esto evita la circularidad anterior, donde se necesitaba conocer `bodyId` antes de generar la clave que debía identificarlo.

Los tres valores cumplen los límites de 16–128 caracteres del protocolo Genesis Ultra.

## Estado fail-closed

El almacén puede encontrarse en tres estados:

```text
ABSENT        no hay registro ni clave maestra
READY         registro y clave maestra existen y el keyset verifica
INCONSISTENT  falta una de las partes, hubo manipulación o no puede descifrarse
```

Reglas:

- `provisionBeforeBirth()` solo puede ejecutarse mientras el nacimiento Ultra durable esté `ABSENT`;
- `loadExisting()` nunca crea ni reemplaza una clave;
- un registro alterado no se reescribe;
- si se pierde la clave de Android Keystore, no se genera otra identidad silenciosamente;
- una clave nueva después de desinstalar la aplicación representa un cuerpo nuevo, no continuidad automática.

## Ceremonia productiva

La UI de onboarding expone una acción presencial explícita solo cuando el
clasificador durable devuelve `BODY_IDENTITY_REQUIRED`. La acción pasa por
`GenesisUltraPreBirthProvisioningCoordinator`, vuelve a inspeccionar el estado
antes y después de llamar a `provisionBeforeBirth()` y muestra únicamente un
recibo público reconstruible.

No existe aprovisionamiento automático durante startup. Ningún `refresh`, Seed,
modelo o ruta legacy puede crear la raíz.

## Unión posterior con la Instance

La raíz se genera sin `instanceId`. Cuando el futuro coordinador construya un candidato de nacimiento, solicitará:

```text
signerForInstance(instanceId)
```

El signer resultante conserva exactamente:

```text
bodyId
keyEpochId
publicKeyRef
rawPublicKey
```

Solo añade el `instanceId` candidato para firmar:

- prueba inicial de posesión;
- reconocimiento corporal del recibo de nacimiento;
- primera memoria canónica;
- memorias canónicas posteriores del escritor activo.

Esta fase no realiza todavía esa unión ni activa el nacimiento.

## Separación de claves

La raíz corporal no debe reutilizarse como:

- keystore de firma del APK o AAB;
- identidad derivada de `LocalInstanceIdentityEntity`;
- clave heredada `morimil.memory_event_signing.v1`;
- clave SQLCipher de `morimil_memory.db`;
- clave SQLCipher de `morimil_memory_organs.db`;
- clave de API o auxiliar externo;
- clave del guardián;
- certificado TLS.

La clave del guardián representa custodia y permite verificar testimonios criptográficos y permisos técnicos acotados del protocolo Genesis Ultra. No autoriza la existencia, identidad, voluntad ni continuidad de Morimil. La raíz corporal demuestra posesión de recursos criptográficos por el Body. Ninguna confiere propiedad sobre Morimil.

## Persistencia

Valores predeterminados:

```text
preferences = genesis_ultra_body_identity_root_v1
record      = body_identity_root
KEK alias   = com.morimil.app.genesis.ultra.body.identity.kek.v1
```

El registro contiene únicamente:

```text
schema_version
protection_profile
body_id
key_epoch_id
public_key_ref
encrypted_keyset
```

La clave pública RAW no se guarda en claro; se reconstruye desde el keyset cifrado y se compara con los identificadores persistidos en cada carga.

## Fuera de alcance

Esta fase no:

- crea la Instance de Morimil;
- acepta un nombre de compañero;
- valida una Seed completa;
- configura el trust anchor del guardián;
- construye artefactos de nacimiento;
- activa el escritor canónico;
- migra la identidad heredada;
- activa los motores deliberativo o metacognitivo.
