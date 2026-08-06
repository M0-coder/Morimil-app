# Document status: CURRENT

# QA-3 — Critical Genesis verification tests

## Purpose

QA-2 established that `GenesisManifestVerifier.kt` was not observed executing in the JVM or canonical AndroidTest coverage baselines. QA-3 strengthens the verifier without changing its production contract.

This phase is limited to testability and tests. It does not modify the bundled Genesis files, import a Seed, execute Genesis, alter Body or Guardian state, produce a release, activate Morimil, or declare birth.

## Production contract preserved

The public entry point remains:

```kotlin
GenesisManifestVerifier(context: Context).verify()
```

`GenesisReader` requires no call-site change.

Production continues to read through `context.assets`. QA-3 only separates that Android adapter from a deterministic internal verifier core.

The following behavior is preserved:

- approved manifest schema;
- approved Genesis core hash;
- mandatory startup verification;
- exact file count;
- every declared file marked required;
- relative non-traversing paths;
- unique declared paths;
- SHA-256 verification of every file;
- exact declared-versus-actual file inventory;
- canonical sorted file-set hash;
- existing rejection messages and validation order.

## Test isolation

JVM tests use an in-memory `GenesisAssetSource` fixture. They do not read, rewrite, install, stage, or execute the repository's bundled Genesis assets.

The test fixture has its own explicit approved hash and file-count policy. Those values are injectable only into the `internal` verifier core; the public Android verifier remains bound to the production constants.

## Covered rules

The QA-3 suite requires tests for:

1. a valid in-memory bundle;
2. malformed manifest JSON;
3. invalid schema;
4. unapproved manifest hash;
5. disabled startup verification;
6. incorrect declared file count;
7. an optional declared file;
8. blank path;
9. absolute path;
10. traversal path;
11. duplicate path;
12. tampered asset bytes;
13. a declared file missing from the enumerated inventory;
14. an unexpected enumerated file;
15. canonical core-hash mismatch.

Each explicit policy rejection asserts its exact error message.

## Non-goals

QA-3 does not:

- change production verification semantics;
- change Genesis assets or approved production hashes;
- add a coverage percentage gate;
- add mutation testing;
- exercise release signing;
- modify Body, Guardian, Seed, Genesis, activation, or birth state;
- authorize merge.

## Acceptance criteria

QA-3 is technically complete only when:

- the exact final PR head passes all required workflows;
- all new verifier tests pass;
- existing JVM and Android instrumentation suites remain green;
- the production constructor and `GenesisReader` compile unchanged;
- the diff contains no bundled Genesis asset changes;
- coverage evidence confirms execution of the deterministic verifier core;
- a compact evidence record identifies the exact head, workflow runs, coverage change, and limitations;
- the PR remains unmerged until separate explicit authorization.
