# Document status: CURRENT

# Morimil-app version policy

## Active version

- Android `versionName`: `0.3.1-prealpha.plan-v3`
- Android `versionCode`: `8`
- Release status: pre-alpha; no production release is declared.
- Governing plan: Plan maestro v3.

`versionName` communicates the verified maturity of the current Android Body.
It does not claim that the Morimil Instance is an Android product or that its
future Bodies must use Android.

`versionCode` is a monotonically increasing Android deployment counter. It must
never be reduced merely to make the public version name smaller, because doing
so can prevent an installed APK from being upgraded.

## Gate ladder

The minor version advances only after the corresponding gate is closed with
evidence in its tracking issue:

| Version line | Verified gate |
| --- | --- |
| `0.1.x` | Canonical identity |
| `0.2.x` | Canonical memory |
| `0.3.x` | Cross-database integrity |
| `0.4.x` | External boundary |
| `0.5.x` | Export, restoration, and Body succession |
| `0.6.x` | End-to-end life test and birth |
| `0.7.x` | Final hardening and controlled opening |

Rules:

1. Code volume, document count, benchmarks, and experimental models do not
   advance the version.
2. A partially completed gate may advance only the patch component within its
   current minor line.
3. `prealpha` remains until the release gate explicitly replaces it.
4. The plan suffix identifies the governing execution plan; it is not a
   compatibility guarantee.
5. Android Body versioning never changes Morimil's `instanceId`, identity,
   memory authority, or continuity.

## Enforcement

`tools/governance/verify_version_policy.py` checks the Gradle declaration,
the expected version format, the monotonic `versionCode` floor, and this
document. The required `reference-checks` workflow runs that verifier on every
pull request to `main`.
