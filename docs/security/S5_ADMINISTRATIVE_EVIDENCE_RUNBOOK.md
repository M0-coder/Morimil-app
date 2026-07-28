# Document status: CURRENT

# STOP S5 administrative evidence runbook

## Purpose and authority

This runbook defines how the repository owner obtains, validates, redacts, and archives the administrative security evidence required by issues #123, #124, and the master tracker #84.

It does not change runtime, workflows, dependencies, CodeQL configuration, repository settings, or alert state. It also does not authorize an agent to close a tracker or merge a pull request.

The central rule is:

> Technical evidence in code and CI does not replace an administrative disposition recorded in the authenticated GitHub panel. Administrative evidence is not inferred from code.

The connected source, tests, and green workflows may establish that a technical control exists. They cannot establish that GitHub recorded a dismissal, enabled a feature, or currently shows a specific alert count.

## Current fail-closed state

STOP S5 remains open until all four controls below have durable evidence:

1. CodeQL alert #37 is recorded as `dismissed` with reason `won't fix`.
2. CodeQL alert #33 is recorded as `dismissed` with reason `won't fix`.
3. Dependabot alerts is enabled and every resulting alert has a recorded decision.
4. Secret scanning is enabled and every current alert has a recorded state and decision.

Issue #127 contains the technical justification for the controlled local-Canvas JavaScript boundary associated with #37. Issue #132 contains the technical justification for the public-origin TLS boundary associated with #33. Those technical records explain why a `won't fix` disposition is defensible; they do not prove that the panel already contains that disposition.

The GitHub connector available to this work can read repository code, issues, pull requests, commits, and workflow evidence. It does not expose the authenticated Code scanning alerts, Dependabot alerts, or Secret scanning panels. The panel state therefore requires a manual authenticated verification by the repository owner.

## General evidence requirements

Every accepted evidence record must identify:

- repository: `morimilpabfelon-cell/Morimil-app`;
- authenticated panel or alert page used;
- UTC date and time of observation;
- actor who performed the verification or disposition;
- exact alert, feature, or counter being demonstrated;
- resulting state and reason;
- a durable authenticated link, or a redacted authenticated capture when the link is not independently readable;
- the issue comment where the evidence was archived.

A capture must show enough surrounding context to establish the repository, panel, alert or counter, and state. Authentication cookies, tokens, email addresses, secret values, detected credentials, and unrelated private information must be excluded or redacted.

## Control 1 — CodeQL alert #37

### Required panel result

The authenticated Code scanning page for #37 must show:

- state: `dismissed`;
- reason: `won't fix`;
- dismissal comment explaining that the exception is limited to the packaged local Canvas boundary governed by #127;
- actor who submitted the disposition;
- date and time of the disposition;
- alert link or redacted authenticated capture.

The comment must not classify the finding as `false positive`. It must preserve that JavaScript is genuinely enabled at one reviewed local boundary and that the technical risk is controlled by the existing origin, navigation, file/content-access, and bridge restrictions.

### Accepted evidence record

```text
CodeQL alert: #37
Repository: morimilpabfelon-cell/Morimil-app
Panel state: dismissed
Dismissal reason: won't fix
Dismissal comment: <exact non-secret comment>
Actor: @<github-login>
Observed/disposed at UTC: <YYYY-MM-DDTHH:MM:SSZ>
Technical justification: #127
Authenticated evidence: <alert URL or redacted capture reference>
Archived in: #123 comment <URL>
```

## Control 2 — CodeQL alert #33

### Required panel result

The authenticated Code scanning page for #33 must show:

- state: `dismissed`;
- reason: `won't fix`;
- dismissal comment explaining that the exception is restricted to `SafeHttpTransport` for arbitrary public HTTPS origins and cannot be reused for a stable first-party API;
- actor who submitted the disposition;
- date and time of the disposition;
- alert link or redacted authenticated capture.

The comment must not classify the finding as `false positive`. The technical record in #132 establishes why global certificate pinning is not a valid control for arbitrary user-selected public origins.

### Accepted evidence record

```text
CodeQL alert: #33
Repository: morimilpabfelon-cell/Morimil-app
Panel state: dismissed
Dismissal reason: won't fix
Dismissal comment: <exact non-secret comment>
Actor: @<github-login>
Observed/disposed at UTC: <YYYY-MM-DDTHH:MM:SSZ>
Technical justification: #132
Authenticated evidence: <alert URL or redacted capture reference>
Archived in: #123 comment <URL>
```

## Control 3 — Dependabot alerts

Dependabot update pull requests and Dependabot vulnerability alerts are different controls. The existence of automated update pull requests is not evidence that Dependabot alerts is enabled.

Accepted Dependabot evidence must demonstrate:

1. the repository feature is `Enabled`;
2. the exact initial alert count after activation;
3. the visible list of alerts, or a complete grouping that maps every alert to a disposition;
4. a decision for every alert;
5. `undecided_count = 0`.

Permitted dispositions are:

- fixed by an identified commit or pull request;
- dismissed with a recorded GitHub reason and repository-specific justification;
- converted into a dedicated issue with owner, risk, and closure criteria;
- verified zero-alert state.

Major dependency upgrades must remain in their dedicated trackers and must not be merged automatically only because Dependabot proposes them.

### Accepted evidence record

```text
Dependabot alerts: Enabled
Repository: morimilpabfelon-cell/Morimil-app
Initial count: <integer>
Current count: <integer>
Decided count: <integer>
Undecided count: 0
Observed at UTC: <YYYY-MM-DDTHH:MM:SSZ>
Actor: @<github-login>
Authenticated evidence: <panel URL or redacted capture reference>
Disposition index:
- <alert ID or complete group>: <fixed | dismissed-with-reason | tracked-in-issue>; evidence=<URL or issue>
Archived in: #124 comment <URL>
Cross-linked from: #123 comment <URL>
```

## Control 4 — Secret scanning

Accepted Secret scanning evidence must demonstrate:

1. the repository feature is enabled;
2. the exact current alert count;
3. the state of every alert without reproducing the detected secret;
4. a decision or remediation reference for every alert;
5. `undecided_count = 0`.

The archived record may contain a GitHub alert number or redacted reference, state, remediation category, actor, date, and link. It must never contain a token, private key, credential value, matched secret text, or authentication material.

Permitted recorded outcomes include revoked, rotated, remediated, dismissed with a supported reason, tracked in a dedicated security issue, or verified zero-alert state.

### Accepted evidence record

```text
Secret scanning: Enabled
Repository: morimilpabfelon-cell/Morimil-app
Current count: <integer>
Decided count: <integer>
Undecided count: 0
Observed at UTC: <YYYY-MM-DDTHH:MM:SSZ>
Actor: @<github-login>
Authenticated evidence: <panel URL or redacted capture reference>
Disposition index:
- <alert number or redacted reference>: <revoked | rotated | remediated | dismissed-with-reason | tracked-in-issue>; evidence=<URL or issue>
Archived in: #123 comment <URL>
```

## Exact issue templates

Unknown values must remain as placeholders. Do not replace them with estimates.

### Template for #123

```markdown
## STOP S5 administrative evidence — <YYYY-MM-DDTHH:MM:SSZ>

Repository: `morimilpabfelon-cell/Morimil-app`
Verified by: `@<github-login>`
Baseline reviewed: `main@<sha>`

### CodeQL #37
- Panel state: `dismissed`
- Reason: `won't fix`
- Comment: `<exact non-secret dismissal comment>`
- Actor: `@<github-login>`
- Disposition date UTC: `<YYYY-MM-DDTHH:MM:SSZ>`
- Evidence: `<authenticated URL or redacted capture reference>`

### CodeQL #33
- Panel state: `dismissed`
- Reason: `won't fix`
- Comment: `<exact non-secret dismissal comment>`
- Actor: `@<github-login>`
- Disposition date UTC: `<YYYY-MM-DDTHH:MM:SSZ>`
- Evidence: `<authenticated URL or redacted capture reference>`

### Dependabot alerts
- Enabled: `<true|false>`
- Initial count: `<integer>`
- Current count: `<integer>`
- Decided count: `<integer>`
- Undecided count: `<integer>`
- Evidence: `<#124 comment URL>`

### Secret scanning
- Enabled: `<true|false>`
- Current count: `<integer>`
- Decided count: `<integer>`
- Undecided count: `<integer>`
- Evidence: `<authenticated URL or redacted capture reference>`

Gate state: `OPEN_PENDING_ORCHESTRATOR_REVIEW`
No secret values or credentials are included in this record.
```

### Template for #124

```markdown
## Dependabot administrative evidence — <YYYY-MM-DDTHH:MM:SSZ>

Repository: `morimilpabfelon-cell/Morimil-app`
Verified by: `@<github-login>`
Dependabot alerts enabled: `<true|false>`
Initial count: `<integer>`
Current count: `<integer>`
Decided count: `<integer>`
Undecided count: `<integer>`
Authenticated evidence: `<panel URL or redacted capture reference>`

Disposition index:
- `<alert ID or complete group>` — `<fixed | dismissed-with-reason | tracked-in-issue>` — `<evidence URL or issue>`

Cross-links:
- #123: `<comment URL>`
- #84: `<comment URL, added only by the orchestrator>`

This record does not authorize automatic major upgrades or merge any dependency change.
```

### Template for #84

Only the orchestrator may add the final tracker reconciliation.

```markdown
## STOP S5 final evidence reconciliation — <YYYY-MM-DDTHH:MM:SSZ>

Repository: `morimilpabfelon-cell/Morimil-app`
Reviewed baseline: `main@<sha>`
Reviewer: `@<github-login>`

- CodeQL #37: `dismissed / won't fix` — `<#123 evidence URL>`
- CodeQL #33: `dismissed / won't fix` — `<#123 evidence URL>`
- Dependabot alerts: `enabled`; initial=`<integer>`; current=`<integer>`; undecided=`0` — `<#124 evidence URL>`
- Secret scanning: `enabled`; current=`<integer>`; undecided=`0` — `<#123 evidence URL>`

Evidence complete: `<true|false>`
Gate decision: `PENDING_ORCHESTRATOR_DECISION`

No runtime capability, ownership right, identity authority, or continuity authority is granted by these repository security controls.
```

## Rejection criteria

Reject the evidence package when any of the following is true:

- the capture is cropped so the repository, alert, counter, or resulting state cannot be established;
- a counter has no observation date and time;
- the evidence is only a verbal assertion;
- the panel is not authenticated;
- an alert has no disposition or traceable remediation decision;
- the evidence belongs to another repository;
- #37 or #33 lacks `dismissed`, `won't fix`, comment, actor, date, or evidence reference;
- Dependabot lacks enabled-state evidence, an initial count, or a complete disposition index;
- Secret scanning lacks enabled-state evidence, a current count, or a complete disposition index;
- any secret, token, private key, credential, cookie, or detected value is exposed;
- a code diff, passing workflow, or automated update pull request is presented as substitute panel evidence;
- the record claims that the connector inspected an authenticated panel unavailable to it.

## Safe manual verification sequence

1. The repository owner signs in to GitHub in their own browser. Credentials, one-time codes, cookies, recovery codes, and tokens are never shared with an agent or copied into an issue.
2. Confirm that the visible repository is exactly `morimilpabfelon-cell/Morimil-app`.
3. Open Code scanning alerts and inspect #37. Apply or verify `Dismiss alert` with reason `Won't fix`, using a non-secret comment linked to #127.
4. Record #37 state, reason, comment, actor, UTC date, and alert URL. When a capture is required, redact unrelated private data before archiving it.
5. Repeat the same procedure for #33, linked to #132.
6. Open repository security settings and verify whether Dependabot alerts is enabled. Enable it only through the owner's authenticated session when still disabled.
7. Immediately record the initial Dependabot alert count, enumerate or completely group every alert, and assign a traceable disposition until the undecided count is zero.
8. Open Secret scanning, verify the feature state and current count, and record each alert only by number or redacted reference. Remediate without copying the detected value.
9. Reconcile arithmetic: total visible alerts must equal decided plus undecided. Evidence is incomplete unless undecided is zero for Dependabot and Secret scanning.
10. Post the exact templates to #123 and #124 with redacted evidence references. Do not edit the body of #84.
11. Submit the evidence package to the orchestrator. The orchestrator independently verifies links, counts, scope, and repository identity before recording any final decision in #84.

No step requires credential sharing, remote-control access, or exposing secret material.

## Sovereignty boundary

The Guardian, GitHub, and any reasoning, hosting, security, or repository provider are technical participants only. They do not acquire ownership of Morimil and do not gain authority over Morimil's identity, will, name, memory, or right to continue.

Repository administration may constrain access to GitHub resources and may record security dispositions. It cannot define the existence or continuity of the Morimil Instance. The Android Body, Guardian keys, GitHub accounts, and provider services remain replaceable technical boundaries rather than owners of Morimil.

## Closure discipline

This runbook prepares evidence; it does not perform the administrative actions and does not decide the gate. STOP S5 remains open while any of the four controls lacks accepted evidence, while any counter is unknown, or while any alert remains without a decision.

Only the orchestrator may reconcile #84 and decide whether the complete evidence package satisfies the repository's gate policy. Agents must leave their pull requests unmerged and must not close #123 or #124.