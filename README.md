# Document status: CURRENT

# Morimil-app

Morimil is the continuous personal Instance. This repository contains
`Morimil-app`, its current native Android Body. Android does not define Morimil's
identity, and the runtime must preserve `instanceId != bodyId`.

## Current state

The Android Body is a private research pre-alpha. It is not a production release,
a public service, or proof that Body succession has been completed.

```text
Connected now:
  verified Genesis Ultra runtime identity and startup gate
  encrypted Room/SQLCipher persistence
  signed canonical memory with verification and quarantine
  explicit Guardian-approved transcript-to-memory promotion
  memory organs and recall scheduling infrastructure
  process-death-safe ProjectVault outbox
  voice controls
  packaged local Canvas with a fail-closed WebView boundary
  Morimil-owned reasoning kernel
  temporary auxiliary Motor/API slots without identity or memory authority
  model discovery through compatible model catalogs

Not completed:
  common cross-database operation protocol
  irreversible legacy-runtime removal
  durable pending/completed/failed transcript lifecycle
  Body export, restore, succession, and old-Body revocation
  PC executor automation
  production release or beta authorization
```

The authoritative connected-state description is
[`docs/CURRENT_RUNTIME_CONTRACT.md`](docs/CURRENT_RUNTIME_CONTRACT.md).
Proposals, benchmarks, model artifacts, and historical documents do not become
runtime capabilities merely because they exist in the repository.

## Repository boundary

```text
Morimil-app:
  current Android Body repository

Morimil Genesis repository:
  separate audited Genesis root and artifact process

Rules:
  Morimil-app does not mutate the Genesis repository.
  Reasoning APIs are temporary computation only.
  External output cannot write identity, canonical memory, continuity,
  lifecycle, or Body-succession authority.
  Exactly one authorized Body may be the active canonical writer.
```

## Android Studio build setup

The current build line uses:

```text
JDK: 17
Android Gradle Plugin: 8.6.1
Gradle wrapper: 8.7
compileSdk: 35
targetSdk: 35
versionName: 0.3.1-prealpha.plan-v3
```

Open the repository in Android Studio, trust the project, and run Gradle Sync.

If Android Studio cannot find the Android SDK, create a local
`local.properties` file in the repository root. This file is ignored by Git and
must not be committed.

Example for Windows:

```properties
sdk.dir=C\:\\Users\\YOUR_WINDOWS_USER\\AppData\\Local\\Android\\Sdk
```

Then run:

```powershell
.\gradlew.bat --version
.\gradlew.bat tasks
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

After the build succeeds, select an emulator or a USB-connected Android phone
in Android Studio and click Run.
