# Document status: CURRENT

# WebView security boundary

## Purpose

This contract freezes the current WebView attack surface while the remaining
CodeQL findings are resolved without breaking Morimil's Body.

Web content is temporary computation and presentation only. It has no authority
over Morimil's instance identity, signed continuity, canonical memory, name,
lifecycle, or Body succession.

## Current boundaries

| Boundary | Content | JavaScript decision | Network decision | Tracking |
| --- | --- | --- | --- | --- |
| Native Web Bridge | Brave results and selected public sources | No WebView is created; search and capture use pure Kotlin parsing | Only the DNS/SSRF-filtered, byte-limited transport performs requests initiated by a visible user search | #125 |
| Native browser screen | Hardened HTML already fetched through the safe document loader | Disabled; capture uses bounded deterministic text extraction | WebView network loads and navigation are blocked | #126 |
| Native browser runtime | Hardened HTML already fetched through the safe document loader | No WebView is created; extraction is pure Kotlin | Only the filtered transport performs the bounded fetch | #126 |
| Morimil Canvas | App-owned assets served through `WebViewAssetLoader` | Accepted only for the controlled local Canvas origin | External HTTP(S) requests are rejected | #127 |

## Invariants

- `allowFileAccess`, `allowContentAccess`,
  `allowFileAccessFromFileURLs`, and
  `allowUniversalAccessFromFileURLs` remain disabled through explicit
  `WebSettings` setters that CodeQL can model.
- Every WebView constructor is assigned to a local variable before its
  `WebSettings` are obtained and hardened. Security configuration must not use
  an implicit Kotlin `apply` receiver.
- WebView references are not stored in delegated Compose state. A
  non-observable local slot avoids manufacturing unconfigured WebView sources in
  static analysis, while a separate Boolean state carries readiness.
- No production code may call `addJavascriptInterface` without a separate,
  explicit security review.
- Every production `javaScriptEnabled = true` site must be listed in
  `WebViewSecurityContractTest` and carry its reviewed boundary marker.
- New JavaScript-enabled WebViews fail tests by default.
- The local Canvas exception does not authorize remote JavaScript.
- The remote-research bridge must not create a WebView or call
  `evaluateJavascript`; search-result parsing and evidence capture are
  deterministic and bounded.
- The isolated readers must not call `evaluateJavascript`; their deterministic
  extractor returns only bounded static text and treats empty static evidence
  as insufficient.

## Alert reconciliation

The 27 alerts observed on 2026-07-26 are assigned to #125 through #128.
Historical filenames must not be dismissed merely because they were renamed.
Alert closure requires a fresh CodeQL result on the exact protected `main`
commit plus source evidence for the corresponding boundary.

CodeQL #149 on `main@296d6d9` reduced the inventory from 27 to 12 without
manual dismissals. CodeQL #151 on `main@fc8185a` then closed three old
fingerprints but opened #34–#36, leaving the total at 12. That result proves
that explicit setters inside a Kotlin `WebView(...).apply` block are not enough
for this query's data-flow model. The remaining inventory is:

| Alerts | Boundary | Disposition |
| --- | --- | --- |
| #2, #3, #6–#10, #31, #35, #36 | Canvas and isolated browser content access | Replace implicit Kotlin receivers and delegated WebView state with a traceable constructor → settings → fail-closed setter flow, then require a fresh CodeQL run |
| #34 | Local Canvas JavaScript | Reviewed local-origin exception tracked by #127; remote requests and navigation remain denied |
| #33 | Public-origin TLS pinning | Platform-PKI exception tracked by #132; arbitrary user-selected hosts cannot share a truthful static pin set |

## Public-origin TLS

`SafeHttpTransport` is a bounded reader for arbitrary public HTTPS origins. It
is not the client of a stable Morimil-owned API. Static certificate pinning in
that transport would either break legitimate user-selected destinations during
normal certificate rotation or become security theatre.

The accepted boundary in #132 therefore requires:

- HTTPS only;
- the unmodified OkHttp/platform trust manager and hostname verifier;
- public-only DNS answers supplied directly to OkHttp;
- automatic redirects disabled and every hop revalidated;
- no credentials or cookies, bounded headers and bodies, and strict timeouts.

Any future stable Morimil-owned service must use a separate transport with
real pins and an explicit rotation policy. It cannot inherit this exception.
