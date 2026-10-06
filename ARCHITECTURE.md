# Grove architecture

For the full current system and invariant inventory with source line references, see [SYSTEM-CATALOG.md](SYSTEM-CATALOG.md). This page is the shorter ownership overview.

## PR #87 presentation and failure-boundary changes

The review branch adds an activity-scoped `PresentationController`: Android owns the applied static/live wallpaper and renders it through the wallpaper window. Home stays transparent without global dimming. Text shadows protect clock/date and wallpaper labels; inset-sized views protect system-bar icons only. Grove does not paint a decoded copy of its remembered selection. Android owns wallpaper ID/color metadata; a private worker reads it, a Home-color listener invalidates it, and resume refresh is the fallback if registration fails. Lifecycle generations reject destroyed/superseded output. No additional wallpaper-reading or file permission is required. Missing color metadata uses the current theme's button colors.

Config schema v10 adds `themeMode` (system/light/dark/wallpaper) in both Kotlin and Rust. Legacy v1–v9 imports retain System and their existing wallpaper-button-color preference. Explicit Light/Dark uses AppCompat night mode; Wallpaper colors follows system night mode and applies Android Home-wallpaper color to launcher buttons. Home/Lock/Both still apply through Android before Grove's remembered Home preference commits; lock-only never replaces Home preference. External changes never rewrite that preference.

Config commits reconcile source switches and theme only after persistence succeeds. Generic failed-save dialogs do not retry a captured Config without the originating UI completion; users retry from that retained surface. Document streams/parsing run on a dedicated executor; activation returns to the main thread and checks the current Config against its start snapshot. Destroyed/superseded documents cannot activate. Settings switches roll back a failed commit.

Search preserves its committed frame through debounce; permission/preference reconciliation explicitly clears protected rows. Stable cache payloads preserve prepared snapshot identity, state-only notifications avoid restarting scans, and `SearchFrameGate` suppresses identical row rebuilds. Query/source generation and current Android access still gate publication and actions. Contact bounds propagate Partial through live results/cache status. Source verification and remaining Android checks are in BUILD-STATUS/TESTING.

Status: the integrated source refactor and [PR #73](https://github.com/davidcit646/Grove/pull/73) MainActivity lane split are on main. The broad device verification ticket #34 was closed unrun by scope decision; the checklist remains in TESTING.md. [GROVE-STATUS.md](GROVE-STATUS.md) is the target behavior; [FAILURE-POLICY.md](FAILURE-POLICY.md) defines the capability outcomes, and [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) separates source checks from Android device work.

`MainActivity` is the approximately 200-line Android host for lifecycle, ActivityResult launchers, root views, platform services and routing. Nine activity-scoped controllers own feature state and navigation decisions. `SearchSources` owns optional contact/file indexes and cancellation; `WidgetFlow` owns the widget setup sequence while the Activity retains result launchers. `FirstRunSetup` owns the full-screen shell and navigation; `FirstRunPages` renders page content with `FirstRunComponents` visual primitives and `FirstRunState` provisional answers. The earlier 1,145-line Activity was split by #73. The former #6 device gate was closed by user scope decision without a device pass; see TESTING.md for the unrun matrix. These lower-level components still support the controllers:

| Owner | Boundary and timing | Failure outcome / retry | Verification |
| --- | --- | --- | --- |
| `StartupCoordinator`, `AppCatalog`, `CatalogController`, `AppIconStore` | Cold/resume plan; app enumeration is separate from icon decode; catalog publishes Loading/Ready/Degraded/Failed | Typed config/service/catalog recovery shows Retry/Home settings; icon failure degrades imagery only; changed-package/size refresh invalidates the relevant cache; stale generations cannot publish | #20-#22, CoreRecoveryPolicyTest, AppCatalogTest, StartupCoordinatorTest; device gate in TESTING.md |
| `SearchSources`, `SearchSourceState`, `SearchController`, `ContactIndex`, `FileIndex` | Enabled source + current permission + current query generation; access changes cancel before reconciliation; file canonical path is rechecked at action | Disabled/permission/loading/ready/partial/failed remain distinct; stale, revoked, inactive or cache-superseded live results cannot publish; app source exposes its own catalog state | #5, #9, #23, SearchSourceStateTest, SearchPublicationGateTest, FileIndexTest; device gate in TESTING.md |
| `ContactActions`, `FileActions`, `SearchActions` | Current permission/package/path before external action | A failed contact/file/intent action reports local error; Home remains usable | #6, #32, #34 |
| `GestureSession`, `HomeTouchRouter`, `DrawerState`, `DrawerTiles`, `DrawerDragController`, `FolderActions`, `PinDragController`, `UninstallBatch` | Touch/selection state before animation or persisted drawer edit; OS uninstall result before next launch | Disabled/invalid/canceled means no action or commit; cancellation clears queue | #14, #24, #25 and respective unit tests, #34 |
| `WidgetFlow`, `WidgetRegistry`, `WidgetScreen` | Allocation and persisted ID transition; provider again at render | Missing provider/createView yields removable placeholder; interrupted setup offers retry/removal | #26, #34 |
| `WallpaperArt`, `WallpaperController`, `WallpaperPicker`, `WallpaperPresentationController`, `ThemeColors` | Stable source registry; packaged/generated/custom worker preview; Android apply before Grove Home preference commit | Invalid/failed preview is scoped and retryable; failed Android apply preserves prior selection; successful apply followed by Config failure exposes split state and retries persistence without reapplying; cosmetic color failure falls back safely | #29, #82, WallpaperControllerTest, WallpaperRegistryTest; device gate in TESTING.md |
| `ConfigDocuments`, `ConfigStore`, `FirstRunSetup`, `FirstRunPages`, `FirstRunComponents`, `FirstRunState`, `ConfigController.commitConfig` | Bounded read/schema check, committed activation; provisional onboarding answers; startup settings check after minimal UI | Invalid import leaves active config; damaged custom config preserved; write failure keeps prior in-memory layout; unreadable startup settings show Retry and Android Home settings; denied optional source disabled | #2, #27, #30 and unit tests, #34 |
| `CoreBridge`, Rust `search/mime/config/wallpaper/bridge` modules | Native load and per-call payload validation | Bounded diagnostic and equivalent Kotlin fallback where safe | #12, #15, #33, NativeResultsTest, Rust tests, #34 |
| `GroveErrors`, `CrashReporter` | Stable Grove/GWS code registry, severity routing, bounded private report drafting and user-directed handoff | Unrelated Home behavior stays available; chooser launch is not delivery; reports remain until explicit discard/confirmable handoff; no mail handler exposes a copy fallback | #28, #78, GroveErrorRegistryTest, GroveErrorRoutingTest, CrashReporterPrivacyTest, ReportPromptPolicyTest; device gate in TESTING.md |
| Gradle, Rust build script, release verifier/workflow | Compile/import/resource/package checks at build; signature/ABI/checksum/tag at release | Required failure stops job and publication; no runtime import sweep | #3, #10, #36, #34 |

On main before PR #87, the JSON schema reads versions 1–9. Version 8 added independent contact/file indexing choices; version 9 persists wallpaper selections by stable source ID instead of numeric slot. Legacy versions 1–8 remain readable, v1–v7 migration leaves durable indexing off, and a legacy numeric wallpaper slot is rewritten as its stable ID on the next successful save/export. A damaged custom configuration is kept separately from the safe fallback until explicit replacement. Optional permissions do not gate the core Home shell. See the current index ownership and flow addendum in SYSTEM-CATALOG.md.

Gradle `preBuild` calls `scripts/build-rust-android.sh` with NDK 27.3.13750724, producing arm64, ARMv7 and x86-64 `.so` files. PR CI runs Rust tests, Android debug build, JVM tests, lint and the missing-signing negative check. A main build requires signing and verifies APK/AAB certificate, native payloads/alignment and checksums. The manual release workflow rebuilds from a supplied tag and exact commit; nothing is published by the refactor itself. Positive signed install/update remains #36; the #34 device matrix was closed unrun by scope decision. The split passed source CI on its PR branch; Android widget, permission, and onboarding interactions have not been device-tested for this change. Current main release CI fails closed when the signing password is unavailable, while debug build, unit tests, lint and Rust tests pass.

The umbrella #17 was closed by an explicit scope decision after limited user testing; that closure does not mean every device/performance/release gate passed. The dated gates in [TESTING.md](TESTING.md) and [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) remain unrun; closure of #6 and #34 is a scope decision, not passing evidence.

## MainActivity lane split (#6)

`MainActivity` is the Android host: lifecycle, result registrations, root views,
platform services, and routing. Lane controllers are activity-scoped and own their
mutable feature state; they use the host for Android effects and explicit calls to
other controllers. This is a single HOME Activity, so switching between Home,
drawer, and search retains the existing task/lifecycle behavior.

| Owner | Lane/state | Failure boundary |
| --- | --- | --- |
| StartupController | Config loading, launcher callback, typed core recovery | Required config/service/catalog failure closes normal Home to a typed Retry/system-settings recovery state; entering recovery supersedes pending catalog/search work. |
| CatalogController | App snapshot, prepared app search, explicit catalog state, icon publication, generation | Enumeration failure is Failed/core recovery; icon failures produce Degraded while apps stay usable; stale generations cannot publish. |
| HomeController | Home rendering, scroll, animation, wallpaper backdrop | Android owns the unmodified wallpaper; local text shadows and system-bar regions supply contrast without a global overlay. |
| DrawerController | Grid, filtering, selection, folders/drag | Folder/pin mutation passes through successful config commit before selection is cleared. |
| SearchController | Search worker, query generation, source snapshots/permissions, publication gate | Source/access changes cancel before reconciliation; stale, revoked, inactive or cache-superseded output cannot publish; app/contact/file outcomes stay distinct. |
| ConfigController | Active config, store, import/export/editor/recovery | Parse before activation; persistence before publication; failed save keeps editor/recovery open. |
| SetupController | Setup instance, settings and permission explanations | Failed config save retains setup for retry; optional permissions do not block Home. |
| ActionController | External actions, menus, uninstall queue | File/contact adapters recheck access; canceled/failed uninstall stops the batch. |
| WallpaperPresentationController | Picker and applied preference | System apply must succeed before wallpaper preference commit. |

`ConfigTransaction` tests enforce persistence-before-publication and ensure a
publication invariant is propagated rather than mislabeled as a storage failure.
The broader Ready/Degraded/Unavailable/Canceled policy remains specified by
FAILURE-POLICY.md; this extraction preserves existing source/widget outcomes and
does not claim every Android boundary has completed failure-injection coverage.

Device gate: Home scroll/back, drawer filter/selection/folders/pins, search with
permissions denied/revoked, setup finish/skip/replay, widgets bind/cancel/restore,
config import/editor/recovery, wallpaper apply, and rotation/resume during pending
search/catalog/wallpaper work. #6 was closed with this device checklist unrun by user scope decision.

## Target design gaps

The implementation matrix in [GROVE-STATUS.md](GROVE-STATUS.md#implementation-status-on-main) is the immutable operating contract, even where its historical main-branch implementation notes lag behind this branch. On PR #83, #74/#75 implement independent controls, live lookups and private persistent caches; #82 incorporates #76 with packaged offline artwork, v9 stable source IDs, solid black and a bounded Android-selected custom image flow; #78 implements the stable Grove/GWS error registry, Degrade/Recover/Stop routing and privacy-bounded report handoff; #27 separates bounded configuration document/recovery work from Activity UI; and #81 moves replay into Launcher settings → Tutorials with a single persisted request. Production/test source at `13aa0c10ac635e216fe555484bc2807d3a98bb2a` passed Android CI run #567 (Rust tests, debug build, JVM tests, lint, and missing-signing-secret negative gate). Android device proof remains recorded separately in TESTING.md. PR #87 implements external Android wallpaper/theme reconciliation under #77 on its review branch.


## PR #90 onboarding implementation — October 5, 2026

Production/test source `026be7f0314c355f0449fe23796a557ee0417e22` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37399253894): seven Rust tests, debug APK assembly, JVM tests, lint, and the missing-signing negative gate. This is source/build verification; PR #90 remains unmerged and its Android device acceptance is pending. GROVE-STATUS.md is unchanged. Issue #89 explicitly requests fresh-install indexing defaults and Settings-only indexing controls; this overrides the older onboarding indexing-choice description without editing that contract.

FirstRunSetup retains one shell and a scoped DynamicColors context with a theme fallback. FirstRunComponents uses Material switches/checkboxes/input and bundled Material Symbols Rounded. FirstRunPages owns concise page content and direct permission requests; FirstRunState owns provisional choices. A Bundle snapshot restores the current page, choices, pins and practice flags; it does not activate settings. Finish still requires a successful Config commit.

FirstRunMotion owns cosmetic alpha/translation only: 300 ms initial foreground fade, 220 ms forward/back slides with RTL inversion, immediate final state when Android animators are disabled. Navigation and touch are gated during motion; stop/permission return/destruction settle it, remove outgoing views and restore alpha/translation. Permission redraws and restored instances do not replay the fade. Fresh setup uses an optional worker-rendered Fern backdrop with a cheap Fern-palette fallback; the stationary setup-only image never changes Android wallpaper. Replay uses Android's existing wallpaper. Destroyed/superseded artwork is recycled.

SetupDefaults selects indexing-on preferences only when no config/initialized/setup-complete marker exists. Config() and legacy parser defaults remain indexing-off for recovery/migration; saved choices remain intact. IndexAccessPolicy requires source enabled AND indexing enabled AND current Android access at scheduling, cache loading/publication, observer and worker/cache-commit boundaries. Source disable cancels work and clears the cache without resetting the indexing preference. Missing permission disables the unavailable setup source at Finish without erasing the indexing preference. Settings retains separate index switches and permitted live search remains available when indexing is off.
