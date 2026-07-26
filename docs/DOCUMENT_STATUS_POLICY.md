# Document status: CURRENT

# Morimil-app document status policy

## Purpose

Every mutable Markdown document must declare its authority on the first
non-empty line:

```text
# Document status: CURRENT
# Document status: HISTORICAL
# Document status: PROPOSAL
# Document status: RESEARCH_ONLY
# Document status: SUPERSEDED
```

The marker classifies the document; it does not certify that every statement
inside it is correct. Production code, tests, cryptographic evidence, and the
current runtime contract remain the stronger sources of runtime truth.

## Meanings

| Status | Permitted use |
| --- | --- |
| `CURRENT` | Active operational contract or accurate description of a connected boundary. |
| `HISTORICAL` | Immutable record of a completed or retired phase; not implementation guidance. |
| `PROPOSAL` | Future design or workflow without current runtime authority. |
| `RESEARCH_ONLY` | Experiment, model artifact, benchmark, or evidence that cannot activate production behavior. |
| `SUPERSEDED` | Known-stale document retained for traceability; must not guide implementation. |

## Directory rules

- `docs/archive/**/*.md` is `HISTORICAL`.
- `docs/research/**/*.md` is `RESEARCH_ONLY`.
- `docs/model-artifacts/**/*.md` is `RESEARCH_ONLY`.
- PC executor material remains `PROPOSAL` until its phase is authorized.
- Architecture and roadmap snapshots that contradict the connected runtime are
  `SUPERSEDED`, not silently rewritten into false currency.

## Sealed Genesis exception

Three Markdown files are payloads inside the bundled Genesis seed:

| Path | Manifest SHA-256 |
| --- | --- |
| `app/src/main/assets/genesis/docs/GENESIS_MEMORY_CORE.md` | `cc426e7473a7f1d1b38fe98a217b61b1c48323566176d3b0c1b16622dee22c50` |
| `app/src/main/assets/genesis/doctrine/doctrine.md` | `0020228d16e0d94f8e22df0966de9f67622534115e53e872e2a77feac0392ffb` |
| `app/src/main/assets/genesis/doctrine/evolution_rules.md` | `1f81f9e4c8f4fc9af065b91810d5434253ee71945a5f17de834c79f758437572` |

They are deliberately exempt from the status-header mutation because their
bytes are committed by `genesis_manifest.json`. Adding a header would alter the
seed. Changing them requires the separate audited Genesis artifact process, not
an application-documentation cleanup.

## Enforcement

`DocumentStatusContractTest` scans every repository Markdown file, requires a
valid status for each mutable document, enforces the directory rules, and
recomputes the hashes of the sealed Genesis files. A new Markdown file without a
classification fails the required Android unit-test workflows.
