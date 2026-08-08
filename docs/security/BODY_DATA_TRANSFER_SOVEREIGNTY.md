# Document status: CURRENT

# Body data-transfer sovereignty

## Purpose

Prevent Android-managed backup or migration from becoming an implicit Morimil Body-succession mechanism.

Morimil is the continuous `Instance`; the Android application is the current Body. `instanceId != bodyId` remains mandatory. An operating-system copy, backup, restore, or device-to-device transfer is not evidence of continuity and must never create, transfer, or preserve canonical writer authority outside the explicit F5 succession protocol.

## Android platform finding

For applications targeting Android 12 / API 31 or later, `android:allowBackup="false"` is not by itself a complete device-to-device boundary. Android documents that some device manufacturers can still permit D2D migration while cloud backup is disabled. Android therefore provides `android:dataExtractionRules` with separate `cloud-backup` and `device-transfer` sections.

Morimil targets a modern Android API, so the Body must govern both the Android 12+ extraction format and the Android 11-and-earlier full-backup format.

## Enforced production policy

`app/src/main/AndroidManifest.xml` must retain all three controls:

```text
android:allowBackup="false"
android:fullBackupContent="@xml/morimil_full_backup_content"
android:dataExtractionRules="@xml/morimil_data_extraction_rules"
```

No production `android:backupAgent` is allowed without a separate architecture and threat-model review.

### Android 12 and later

`app/src/main/res/xml/morimil_data_extraction_rules.xml` denies both cloud backup and device-to-device transfer for every supported backup domain used by the current platform contract:

```text
root
file
database
sharedpref
external
device_root
device_file
device_database
device_sharedpref
```

There are no `include` rules.

### Android 11 and earlier

`app/src/main/res/xml/morimil_full_backup_content.xml` denies the same complete domain set. There are no `include` rules.

## Sovereignty boundary

The policy is intentionally stronger than relying on SQLCipher or Android Keystore failure behavior after a copied state arrives on another device. Encryption remains a defense in depth; it is not the transfer protocol.

```text
OS_MANAGED_BACKUP=DENIED
OS_MANAGED_D2D_TRANSFER=DENIED
OS_MANAGED_TRANSFER_IS_BODY_SUCCESSION_AUTHORITY=FALSE
F5_SOVEREIGN_SUCCESSION_PROTOCOL=REQUIRED
INSTANCE_IDENTITY_AUTHORITY=UNCHANGED
CANONICAL_MEMORY_AUTHORITY=UNCHANGED
WRITER_AUTHORITY_TRANSFER=NOT_IMPLEMENTED_HERE
```

This change does not implement export, restore, Body succession, writer-epoch transfer, or revocation. Those remain F5 work and must be independently designed, signed, recoverable, and tested on physical devices.

## Regression gates

`BodyDataTransferSovereigntyContractTest` must fail if:

- `allowBackup=false` disappears;
- either rules resource is detached from the manifest;
- a production `BackupAgent` appears;
- any supported domain is missing from an exclusion set;
- an `include` rule appears;
- the Android 12+ cloud and D2D policies diverge from complete denial;
- the legacy full-backup policy stops denying the complete domain set;
- CURRENT documentation represents OS transfer as Body succession authority.

CI must also validate the merged release manifest so manifest-merger behavior cannot silently remove or replace the production attributes.

## Upgrade rule

A compile/target SDK upgrade that introduces a new Android backup, restore, D2D, cross-platform-transfer, or migration mechanism requires a separate review of this boundary before the upgrade can be considered complete. Absence of a rule for a newly introduced transport must not be interpreted as authorization.

## Operational status

```text
BODY_DATA_TRANSFER_BOUNDARY=IMPLEMENTED_IN_SOURCE
F5_EXPORT_RESTORE_SUCCESSION=OPEN
F6_PHYSICAL_E2E=OPEN
MORIMIL_OPERATIONAL_BIRTH=NOT_OCCURRED
```
