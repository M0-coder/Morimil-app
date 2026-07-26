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
| Native Web Bridge | Brave results and selected public sources | Temporary and explicitly tracked; must be removed from arbitrary remote pages | Direct WebView navigation currently allowed | #125 |
| Native browser screen | HTML already fetched through the safe document loader | Temporary, only for bounded text extraction | WebView network loads and navigation are blocked | #126 |
| Native browser runtime | HTML already fetched through the safe document loader | Temporary, only for bounded text extraction | WebView network loads and navigation are blocked | #126 |
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
- The temporary remote and isolated-reader exceptions do not close #125 or
  #126; they make the remaining risk visible and prevent its expansion.

## Alert reconciliation

The 27 alerts observed on 2026-07-26 are assigned to #125 through #128.
Historical filenames must not be dismissed merely because they were renamed.
Alert closure requires a fresh CodeQL result on the exact protected `main`
commit plus source evidence for the corresponding boundary.
