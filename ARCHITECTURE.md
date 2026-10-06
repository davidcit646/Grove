# Grove architecture

## Juniper settings architecture — PR #92

Production/test source `994df71d741b67dd65ed690840bd67b48cef1cdb` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37404896738): eight Rust tests, 163 JVM tests, debug APK assembly, Android lint and the missing-signing negative gate. Signed release steps were skipped. PR #92 is stacked on unmerged PR #90; Juniper device acceptance remains pending. GROVE-STATUS.md is unchanged.

| Owner | Incoming source → outgoing consumer | Authority, commit and failure boundary |
| --- | --- | --- |
| `SettingsRepository` in `GroveApp` | ConfigStore load + typed changes → revisioned snapshot → both hosts | Sole active Config authority. Validate complete candidate; persist before publishing. Invalid, Conflict and Unavailable leave prior snapshot/revision intact. No views or feature work. |
| `SettingsCommands` | UI intent → latest repository transform → existing feature owners | Boolean/theme/grid mutations never replace stale UI snapshots. Index reconciliation runs after persistence; scheduling failure reports saved preference with unavailable refresh. Reports and replay retain separate stores. |
| `SettingsActivity`, `SettingsPages`, `SettingsGridPage`, `SettingsDocumentPages` | Committed snapshot + current platform access → categorized views; UI intent → commands | Host owns lifecycle, result launchers, navigation and Material presentation. Views do not write preferences. Retained drafts are provisional. The Settings Activity is packaged in the same APK/process, with an opaque theme; Home wallpaper transparency remains in MainActivity. |
| `SettingsSession`, `ConfigDocuments` | Editor/document → bounded worker read/parse → reviewed candidate → explicit Apply | No retained Activity. Generation invalidation cancels queued/publication effects. Candidate captures config and revision; concurrent changes require re-review. Failed writes preserve draft and active configuration. Rotation retains jobs/drafts; process death requires explicit re-review/reimport. |
| `ConfigController`, `MainActivity` | Shared committed snapshot → Home/search/theme reconciliation | ConfigController is a host adapter, not an independent Config store. Main refreshes on return/resume and observes committed changes while active. Saved preference and unavailable feature refresh remain distinguishable. |
| `SetupController`, `SetupMergePolicy` | Original base + provisional setup lanes + current snapshot → checked Finish | Merge only modified setup lanes; retain current grids/theme/folders/wallpaper. Same-lane conflict restarts review; failed save retains setup. Marker writes are checked. |
| `GridPolicy`, `HomeController`, `DrawerController`, `DrawerTiles` | Optional validated home/drawer grid + actual items → pages and accessible tiles | Schema v11 reads v1–v10 as automatic grid. Columns and rows independently accept integers 1–10. Page capacity = columns × rows; shrink/filter clamps pages without dropping items. Dense grids scroll with minimum touch/font sizing. Existing folder grids and automatic behavior remain unchanged. |
| Existing feature owners | Settings routes → Main feature entry/platform adapter | WidgetFlow/Registry retain ID ownership; FolderActions retains selected-app mutations; wallpaper owners retain Android apply-before-preference; SearchSources/IndexWork retain permission, generation, cache and scheduling gates. Reports, email and tutorial markers remain outside portable Config. |

The categorized workflow replaces the Grove settings modal and configuration dialogs. Home/drawer/search remain in MainActivity. Widget, wallpaper and folder entry routes delegate to their existing owners; their contextual dialogs are retained. There is no second APK, duplicated preference store or exported backend service. The APPLICATION_PREFERENCES route opens SettingsActivity directly.

Portable Config schema v11 includes nullable `homeGrid` and `drawerGrid`. Kotlin and native preflight agree on integral dimensions 1–10; legacy automatic grids and unrelated preferences are preserved. Local widget IDs, report settings and replay markers keep their existing separate ownership.

For the full current system and invariant inventory with source line references, see [SYSTEM-CATALOG.md](SYSTEM-CATALOG.md). This page is the shorter ownership overview.

## PR #87 presentation and failure-boundary changes

The review branch adds an activity-scoped `PresentationController`: Android owns the applied static/live wallpaper and renders it through the wallpaper window. Home stays transparent without global dimming. Text shadows protect clock/date and wallpaper labels; inset-sized views protect system-bar icons only. Grove does not paint a decoded copy of its remembered selection. Android owns wallpaper ID/color metadata; a private worker reads it, a Home-color listener invalidates it, and resume refresh is the fallback if registration fails. Lifecycle generations reject destroyed/superseded output. No additional wallpaper-reading or file permission is required. Missing color metadata uses the current theme's button colors.

Config schema v10 adds `themeMode` (system/light/dark/wallpaper) in both Kotlin and Rust. Legacy v1–v9 imports retain System and their existing wallpaper-button-color preference. Explicit Light/Dark uses AppCompat night mode; Wallpaper colors follows system night mode and applies Android Home-wallpaper color to launcher buttons. Home/Lock/Both still apply through Android before Grove's remembered Home preference commits; lock-only never replaces Home preference. External changes never rewrite that preference.

Config commits reconcile source switches and theme only after persistence succeeds. Generic failed-save dialogs do not retry a captured Config without the originating UI completion; users retry from that retained surface. Document streams/parsing run on a dedicated executor; activation returns to the main thread and checks the current Config against its start snapshot. Destroyed/superseded documents cannot activate. Settings switches roll back a failed commit.

Search preserves its committed frame through debounce; permission/preference reconciliation explicitly clears protected rows. Stable cache payloads preserve prepared snapshot identity, state-only notifications avoid restarting scans, and `SearchFrameGate` suppresses identical row rebuilds. Query/source generation and current Android access still gate publication and actions. Contact bounds propagate Partial through live results/cache status. Source verification and remaining Android checks are in BUILD-STATUS/TESTING.

Status: the integrated source refactor and [PR #73](https://github.com/davidcit646/Grove/pull/73) MainActivity lane split are on main. The broad device verification ticket #34 was closed unrun by scope decision; the checklist remains in TESTING.md. [GROVE-STATUS.md](GROVE-STATUS.md) is the target behavior; [FAILURE-POLICY.md](FAILURE-POLICY.md) defines the capability outcomes, and [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) separates source checks from Android device work.

`MainActivity` is the approximately 200-line Android host for lifecycle, ActivityResult launchers, root views, platform services and routing. Nine activity-scoped controllers own feature state and navigation decisions. `SearchSources` owns activity cache snapshots; process-owned `ContactChanges` owns contact observation, `IndexWork` owns durable scheduling and `IndexCache` owns publication; `WidgetFlow` owns the widget setup sequence while the Activity retains result launchers. `FirstRunSetup` owns the full-screen shell and navigation; `FirstRunPages` renders page content with `FirstRunComponents` visual primitives and `FirstRunState` provisional answers. The earlier 1,145-line Activity was split by #73. The former #6 device gate was closed by user scope decision without a device pass; see TESTING.md for the unrun matrix. These lower-level components still support the controllers:

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
| ConfigController | MainActivity adapter to shared SettingsRepository; settings workflow routing | Persistence before publication; reconcile committed state on resume; SettingsSession owns documents/drafts. |
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

## Juniper cleanup after initial device check

Cleanup source `a8f1ecdc5be3cd51b685425f6f4c896bd91d67bc` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37407249555): eight Rust tests, JVM tests, debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. The user confirmed the original Juniper build loads and core workflows work, and supplied a recording showing repeated contact-index status transitions. The cleanup still requires device revalidation; no merge occurred.

Settings categories and destinations now use matching Material Symbols Rounded icons. Search groups contact/file controls into equivalent Material cards. Cache availability and WorkManager activity are separate labels (Idle/Queued/Running/Waiting to retry/Failed); cache presence does not claim complete coverage. IndexWork serializes scheduling, coalesces queued notifications, retains at most one deferred follow-up for running work, and rate-limits sequential contact-provider events to a 30-second window. Manual refresh remains immediate when no work is active. A terminal failed scan remains failed rather than appending a dependent job that could be canceled. Permission, settings and generation gates remain authoritative.

## Contact-path fixes #93–#102 — October 6, 2026

Production/test source `6cafc09247cbc3f96eb1a21a1ebc4bfb025bd130` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37409473807): eight Rust tests, the JVM suite (173 test methods), debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. Source trees locally and on the pushed branch match. PR #92 remains draft, stacked on unmerged PR #90; the new device acceptance is pending. GROVE-STATUS.md is unchanged.

The current owner/caller/consumer and failure inventory is [CONTACT-REVIEW.md](CONTACT-REVIEW.md). Package changes no longer request contact indexing; ContactChanges owns process observation, IndexWork owns scheduling/recovery/repair, IndexCache owns confirmed cache publication and shared metadata, and the two UIs render those outcomes. The previous cleanup pass did not prove idle stability; this source fixes the subsequent full-audit findings.

## Grouped settings cards — October 6, 2026

Source `42984548fd4c6211bde60df7871acd20a421c9fb` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37456480396): Rust tests, the JVM suite, debug APK assembly, lint and the missing-signing gate. Signed release steps were skipped. This is source/build verification; visual device acceptance is pending. GROVE-STATUS.md is unchanged.

`SettingsGroups` owns only Material card surfaces, spacing and accessibility headings. `SettingsPages` and `SettingsDocumentPages` place their existing controls inside these groups; SettingsCommands, the shared repository and platform/feature owners retain all actions and settings authority. Search uses the same helper. Home groups are Buttons, Clock and date, Pinned apps, Gestures, Widgets and Default launcher; Drawer, Appearance, Configuration, Help, About and artwork credits also use labeled cards. No setting keys, navigation routes, platform actions or command calls were removed.


### Settings scroll ownership

`SettingsActivity` owns transient per-route scroll positions through `SettingsScrollState`, outside configuration and `SettingsRepository`. Before a render it remembers the laid-out displayed route; after the replacement ScrollView lays out it restores that route's position. The layout guard prevents rapid repository updates from saving a newly created view's zero position before restoration. Saved-instance state preserves allowlisted route positions across recreation. The ScrollView receives focus to prevent newly constructed controls from pulling the viewport to the first row. Existing page motion finishes before capture; Android clamps positions when content shrinks.


### Settings discovery (#103)

`SettingsCatalogue` owns stable discovery IDs, aliases, icons and typed Grove route/anchor or public Android action destinations. `SettingsLabels` binds titles and breadcrumbs to Android resources; typed toggles and anchored action rows reuse the labels. `SettingsMatcher` prepares the small catalogue once and performs bounded, deterministic local matching without permissions, provider queries, disk indexing or settings mutations. Adding a typed SettingKey requires a catalogue destination; SettingsDiscoveryTest checks coverage and ID uniqueness.

`SearchController` owns debounced generation-checked publication of apps, Grove settings, Android settings, contacts, files and web/store results. Grove rows precede Android rows structurally. New rows and capability failure state participate in SearchScreen's frame fingerprint. Android capability snapshots refresh off-thread on search entry/resume and relevant package catalogue events; matching uses a prepared snapshot rather than PackageManager calls per keystroke. Contact/file grants, user switches, indexes and live scans retain their existing owners and gates.

`AndroidSettingsRouter` owns platform resolution and launch. `SettingsCapabilities` distinguishes Ready, unsupported/unavailable and failed discovery, validates enabled/exported/permitted system handlers from packages that handle the public Android Settings root, and rechecks the destination at action time. Failed discovery exposes a scoped retry and bounded diagnostic; unsupported pages are hidden. Launch failure leaves local search available. Targeted manifest intent queries cover the curated actions; no broad package visibility or settings-write permission is introduced. Android remains responsible for changing system settings.

`SettingsSearchPresentation` converts metadata into navigation rows. SettingsActivity validates catalogue anchors and scrolls to the matching view after layout, consuming the anchor once; Back and later preference refreshes preserve normal per-route scroll state. No search result invokes a command merely by being displayed. Add widget navigates to the existing Home widget control, which delegates to WidgetFlow only when tapped. Defaults, report deletion and configuration imports retain their existing explicit review/action workflows.

Android has no universal documented widget-settings page. Widget queries offer Grove Add widget first, plus supported related Default launcher and a labeled Search Android settings (or Android settings root) fallback. ACTION_APP_SEARCH_SETTINGS receives no undocumented query extras. ACTION_SEARCH_SETTINGS, which configures global search, is not used.


### Calculator search

`SearchCalculator` owns deterministic local arithmetic for expressions ending in `=`: decimal literals, unary signs, parentheses and precedence for addition/subtraction/multiplication/division, including ×/÷/− keyboard symbols. It returns NotCalculation, Answer (with an explicit approximation flag), or Invalid (syntax, division by zero, or limits). It parses no functions/scripts/exponents and never accesses Android, the network or persistent settings. Input is bounded to 256 characters, literals to 64 characters, nesting to 16, operations to 128, and intermediate precision/scale and rendered answers to bounded sizes. BigDecimal preserves exact arithmetic; nonterminating division uses DECIMAL128 and marks the answer approximate.

SearchController evaluates on the existing query worker and publishes under the same generation/target guard as other results. SearchScreen includes the calculation in its frame fingerprint and renders the answer before app results; malformed arithmetic has a non-actionable correction message. Ordinary queries retain their normal search behavior. CalculatorActions owns tap-time handoff through Intent.makeMainSelectorActivity(ACTION_MAIN, CATEGORY_APP_CALCULATOR), without an OEM package name or expression extras. A missing/failed external calculator produces a scoped message and keeps the local answer available. Android owns calculator app selection and launch.
