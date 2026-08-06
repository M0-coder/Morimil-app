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
- the observed security boundary;
- the minimum non-sensitive reproduction description;
- whether credentials, identity, memory, continuity, Body, Guardian, Seed, Genesis, or signing material may be involved.

## Handling rules

Security reports are triaged by severity, confidence, reproducibility, affected surface, and evidence. A report is not considered closed until the fix is executed, verified on an exact commit, and its residual risk is recorded.

No report authorizes release, Body mutation, Guardian mutation, Seed import, Genesis execution, activation, operational birth, branch deletion, or merge. Those operations require separate explicit authorization.

## Disclosure

Coordinated disclosure timing is decided case by case after the affected boundary is contained and evidence is preserved. No fixed disclosure deadline is promised at the current pre-alpha stage.
