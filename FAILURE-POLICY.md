# Grove capability and failure policy

Baseline capability-outcome design for #19 under the repository-wide refactor #17. [GROVE-STATUS.md](GROVE-STATUS.md) is the source of truth for the target user behavior and service dependencies; this file defines how operations express Ready/Degraded/Unavailable/Canceled outcomes. Neither document claims all target behavior is implemented. The historical file-by-file baseline is [FAILURE-BOUNDARY-AUDIT.md](FAILURE-BOUNDARY-AUDIT.md); current implementation gaps are listed in [Grove Status](GROVE-STATUS.md#implementation-status-on-main).

## Terms and outcomes

- **Fail first:** check prerequisites at the beginning of the operation or loading sequence that needs them, before mutating persistent state or launching dependent work. Build-time imports, resources and packaging are checked by build/install gates.
- **Fail fast:** stop that operation promptly after an unrecoverable error, cancel dependent work, and surface a useful outcome. Do not allow a queued operation to continue after the user cancels it.
- **Fail open:** continue a capability with a *named, behaviorally safe fallback*, for example an app name and placeholder icon after icon decoding fails. Report degraded state; do not turn unknown or partial results into a silent success.
- **Fail closed:** prevent the action or capability when a required check cannot establish permission, validity, or safety. For a default launcher, closing a core capability should display a minimal recovery Home when feasible, not intentionally crash-loop the entire process.

Use a testable outcome shape (exact Kotlin names can be chosen in implementation):

| Outcome | Meaning | UI/diagnostic obligation |
| --- | --- | --- |
| `Ready(value)` | Verified operation succeeded. | Commit successful state; clear previous transient error. |
| `Degraded(value, reason, fallback)` | A documented fallback supplies safe, useful behavior. | Make degradation observable without interrupting routine app use. Record one bounded diagnostic per failure class. |
| `Unavailable(reason, retry)` | Required condition absent; no valid result. | Suppress affected action/results, retain unrelated Home features, offer retry or settings/recovery route. |
| `Canceled` | User/lifecycle superseded work. | Stop dependent operations and discard unfinished output; do not report success or failure as if work completed. |

An empty, valid result is `Ready(empty)`, not `Unavailable`. A partial file index is `Degraded(partial, skippedCount)` unless a documented policy declares the root unusable. Do not use `null`/empty list alone to conflate these cases.

## Check timing

| Boundary | Required checks | Work deliberately deferred |
| --- | --- | --- |
| Build and release | Compile imports/resources, SDK/NDK/Rust targets, tests/lint, packaged ABI/JNI, signature/fingerprint, checksum, exact commit/tag and artifact presence. | No runtime reflection of every Kotlin class. A missing required artifact fails the job. |
| Process start (`GroveApp`) | Install minimal crash protection and initialize lightweight, process-scoped status holders. | No file scan, contact query, icon decoding, network download, or large bitmap on main thread. |
| Activity creation | Parse/validate config; restore lifecycle state; check essential Android service availability; show Home or recovery surface. | App enumeration and optional data load on workers after a responsive surface exists. |
| Feature entry/enablement | Check setting, current permission, provider/service availability and cached-state validity for that feature. | Do not eagerly initialize disabled sources. |
| Resume/change events | Recheck volatile permission/provider/package states as needed; use generation tokens to cancel stale work. | Do not repeat every scan/bitmap decode simply because `onResume` ran. |
| Action time | Recheck permission, canonical file path/existence, current package/widget provider, and action preconditions immediately before sensitive or external intents. | Startup success is not a permanent authorization. |

## Capability decisions

| Capability | Required condition | Failure policy and recovery | Owner/issues |
| --- | --- | --- | --- |
| Core Home shell | Activity can show basic UI. | Closed for broken core action; show minimal recovery Home with Retry and reachable system/default-Home route where feasible. No intentional crash loop. | #20 #21 |
| App catalog/launch | `LauncherApps` and a valid current app list/action. | Enumeration unavailable: close app-list action and show retry. Individual app launch failure: message, refresh list. | #21 #22 |
| Icons/theme colors | Decodable icon/color resource. | Open with placeholder or safe theme color; keep labels and actions; report degraded state. | #8 #22 #31 |
| Saved config/import | Size, supported schema and valid values; successful commit for changed state. | Invalid custom config: preserve source and use known safe fallback with recovery. Failed import never changes active config. | #27 |
| Rust acceleration | Loadable compatible library and valid output for each operation. | Open only to tested equivalent Kotlin algorithm; record native failure once per class. Close a feature if no valid fallback. | #12 #15 #33 |
| Contact search | Feature enabled, current permission and responsive provider. | Denied/provider unavailable: no stale results; show permission/retry state. Empty valid contacts is Ready. | #5 #23 #30 |
| File search/share | Feature enabled, current access, bounded scan; at share time canonical path and access verified. | Denied: no results/share. Directory failures: report partial count; failed root: unavailable; cancel clears unfinished results. | #9 #23 |
| Widgets | Valid allocated ID/provider/bind/configuration result. | A broken widget yields local placeholder/retry/remove; never leak orphan IDs or block other Home content. | #26 |
| Wallpaper | Valid source/preview; successful system application before applied-state commit. | Preview failure offers retry; apply failure preserves truthful previous applied state. Built-in art may be a named fallback. | #4 #29 |
| Drawer/uninstall | Valid selection and explicit OS result. | Cancel stops remaining batch; persist folder/pin change only after validation. | #14 #24 #25 |
| Crash reports | Private bounded report and user-directed handoff. | Reporter error cannot recurse into crash path. Email chooser launch is not confirmed delivery; retain report until explicit discard/confirmed handoff. | #28 |
| Release | Same-commit, correctly signed, verified APK/AAB and metadata. | Closed: fail workflow and do not publish when required verification fails. | #3 #10 #36 |

## Ownership and propagation

The component that performs the work owns its outcome; its caller decides how to display it. `MainActivity` retains Android lifecycle/result registrations and navigation but does not turn component exceptions into generic empty data. A startup coordinator orders only essential checks and subscribes to optional capability states. Each capability keeps its own retry/cancellation and one source of truth. Pure helpers (`Gestures`, `PinnedApps`, `Search`) return deterministic values; Android adapters translate platform errors to outcomes. UI helpers render the outcome passed to them and do not independently decide whether a permission or provider is valid.

Catch errors only where the caller can name the operation, distinguish canceled/empty/partial/unavailable, and recover or propagate. Do not swallow `OutOfMemoryError`, linkage/VM errors, or programmer invariants as ordinary optional-feature failures. Log a bounded diagnostic with cause category and operation; do not include contact names, numbers, file paths, imported config, or signing secrets in routine logs. A user-facing message gives a recovery action rather than raw exception text.

Persistent state changes use validate -> perform -> confirm -> commit, or a recoverable transaction when the platform cannot confirm success. Asynchronous results carry a generation/lifecycle token; stale results are discarded and any owned bitmaps/IDs are released. Retried operations are idempotent where practical. Missing permission is rechecked at the action boundary even if an earlier load succeeded.

## Verification gate

For every row in [FAILURE-BOUNDARY-AUDIT.md](FAILURE-BOUNDARY-AUDIT.md), record a focused automated test or a named Android device case (#32, #34). Inject failure before work, during work, and after a canceled/superseded request when relevant. Assert both the outcome and the absence of unauthorized/stale state. Measure cold/warm Home startup and search latency against the pinned baseline (#35). CI and signed release validation must fail on missing artifacts (#36). The umbrella #17 was closed by an explicit scope decision after limited user testing; its unrun device, performance, and signed-release checks remain in their specific open issues. Close each remaining issue only after its own implementation and verification conform.

## New target behavior not covered by the original refactor

Separate search from indexing ([#74](https://github.com/davidcit646/Grove/issues/74)) and durable GFI/GCI caches ([#75](https://github.com/davidcit646/Grove/issues/75)) extend the current in-memory search contract. Permission and the user's search/index switches must be checked independently; a revoked grant always closes protected reads, while a failed or disabled index may degrade to permitted live search. A partial scan is visibly partial, not Ready(empty). These controls and caches are proposals until implemented and tested.

User-selected/black wallpaper ([#76](https://github.com/davidcit646/Grove/issues/76)) and Android wallpaper/theme reconciliation ([#77](https://github.com/davidcit646/Grove/issues/77)) extend the existing transactional apply rule. A failed preview or apply leaves the previous applied state truthful and Home usable. The GHEAEW severity and numeric/GWS diagnostic registry ([#78](https://github.com/davidcit646/Grove/issues/78)) is also target behavior; current CrashReporter does not provide those codes. Reporting remains user-directed, and safe diagnostics exclude private contact/file/config data by default. See [GROVE-STATUS.md](GROVE-STATUS.md#when-something-goes-wrong).
