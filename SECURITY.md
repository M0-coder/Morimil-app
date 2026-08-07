# Document status: CURRENT

# Security Policy

## Project status

Morimil-app is a research pre-alpha Android Body for the Morimil Instance. It is not declared production-ready, public-beta-ready, or operationally born.

## Supported versions

No released version is currently supported. Security review applies to the current `main` branch and to explicitly identified candidate commits.

## Reporting a vulnerability

Do not publish exploit details, secrets, personal data, Guardian material, Seed material, signing material, or Genesis evidence in a public issue.

This repository currently declares no public security email and no response-time service-level agreement. Use an existing private channel with the repository owner. When no private channel is available, open a minimal public issue requesting private coordination without including technical exploit details.

A useful initial report identifies:

- the exact commit SHA;
- the affected component or workflow;
- the observed impact;
- reproduction conditions that do not expose secrets or sensitive data;
- whether the issue is present in the debug, release, CI, or tooling surface.

Do not claim a vulnerability is fixed until the relevant validation is reproducible on an exact commit.

## Supply-chain policy

The repository treats dependency versions, resolved artifacts, APK contents, known-vulnerability results, license metadata, and CI action pins as auditable supply-chain evidence. Missing or incomplete evidence is not equivalent to a clean result.

Dependency or workflow updates must preserve the repository's fail-closed security boundaries. A dependency scan is evidence about the scanned graph only; it does not constitute a statement that Morimil is production-ready or operationally born.
