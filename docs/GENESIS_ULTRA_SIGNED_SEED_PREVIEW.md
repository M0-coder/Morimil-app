# Genesis Ultra signed Seed preview

## Purpose

Android can now import one user-selected ZIP, verify the detached Guardian
signature against the locally pinned Guardian epoch and construct an ephemeral
Genesis Ultra birth candidate summary.

This is a preparation boundary only. It does not authorize or commit birth.

## Archive envelope

The ZIP transport contains exactly two reserved metadata entries:

```text
genesis.seed.manifest.json
genesis.seed.signature.json
```

Every other non-directory entry is treated as a Seed payload file. The manifest
remains authoritative for the exact payload path set and digests.

The reader rejects:

- absolute, parent-traversal, empty-segment, drive-prefixed or backslash paths;
- duplicate entries;
- missing manifest, detached signature or payload;
- malformed UTF-8 metadata;
- more than 512 entries;
- any expanded entry larger than 4 MiB;
- more than 32 MiB of total expanded bytes.

No entry path is resolved on the Android filesystem. Payload bytes remain in
memory and are passed to the existing strict release verifier.

## Trust and candidate construction

The import path is available only when preparation state is
`READY_FOR_SIGNED_CANDIDATE`. Verification uses only the public Guardian epoch
already pinned out of band in the Android trust-anchor store.

After release verification, candidate construction binds:

- the exact verified Seed root;
- the canonical companion name;
- the local Body identity root;
- the pinned Guardian and key epoch;
- a fresh Body-possession proof;
- a newly generated Instance identifier.

The UI receives only non-secret identifiers and digests. It does not retain the
verified release or the candidate object after the preview result is produced.
Changing the companion name or rechecking preparation clears the preview.

## Still blocked

A verified preview is not:

- host consent;
- Guardian birth testimony;
- atomic-birth authorization;
- a persisted candidate;
- a Genesis Ultra birth.

The birth button remains disabled. The preview coordinator has no dependency on
the consent store, authorization coordinator, execution coordinator or Room
transaction APIs. Deliberative and metacognitive motors remain blocked.
