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
  `allowUniversalAccessFromFileURLs` remain disabled.
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
