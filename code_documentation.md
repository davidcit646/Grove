# Grove Launcher — current code documentation

This is the engineering contract for the source on this branch: cleanup candidate **1.0.1 / code 33**, configuration schema **12**, Android **31–36**, package `tech.granet.grove`. Debug adds `.test`. The previous released baseline is `0e9eb607` (1.0.0 / 32). This document describes current ownership and behavior; git history and old `verification/0.1.*` directories preserve historical evidence.

## Architecture and authority

Android owns launcher enumeration, permissions, contacts, filesystem access, widgets, intents, wallpaper application, resources and UI. The process owns one `SettingsRepository`; Activities and retained settings sessions share its immutable configuration snapshots. Rust owns portable computation through the versioned native bridge. Kotlin owns Android adapters, persistence confirmation, callback ordering, snapshots and bounded recovery implementations.

| System | Authority and owners | Result / failure boundary |
|---|---|---|
| Process | `GroveApp`, `SettingsRepository`, `ConfigStore`, `ContactChanges` | One active Config authority; provider observation is process-owned. |
| Startup | `StartupController`, `StartupCoordinator`, `CoreRecoveryPolicy`, `CoreRecoveryView` | Home precedes optional source reconciliation; core recovery supersedes pending output. |
| App catalogue | `CatalogController`, `AppCatalog`, `CatalogPipeline`, `AppIconStore` | Enumerate before icons; a failed icon preserves a launchable row and uses a fallback. |
| Home / drawer | `HomeController`, `DrawerController`, `DrawerTiles`, drag owners, `DrawerState` | Views and transient selection remain Activity-owned; edits return a candidate Config. |
| Search | `SearchController`, `SearchLiveQueries`, `SearchSources`, `SearchAccessController` | Source execution, cached snapshots and permission UI have separate owners; current authority is rechecked at publication. |
| Settings | `SettingsActivity`, `SettingsSession`, `SettingsCommands`, `TutorialCommands`, `DiagnosticsCommands`, `IndexStatusController` | Retained drafts/jobs own no Activity; domain commands do not own unrelated effects. |
| Config | `ConfigModels`, `ConfigCodec`, `ConfigInput`, `ConfigDocuments`, `ConfigDocumentGate` | Strict bounded input; schema/migration authority in Rust; explicit recovery decoder; durable save before publication. |
| Indexes | `IndexWork`, `IndexWorker`, `IndexCache`, `IndexRevocationGate` | Durable worker token plus current grant/switches; cancellation and cache commit share a lock. |
| Contacts | `ContactIndex`, `ContactScanBudget`, `ContactActions` | Android aggregate provider; names for search, details on demand; current data/access checked before actions. |
| Files | `FileIndex`, `FileActions`, `SharedFileProvider` | Bounded shared-storage metadata walk; canonical containment and link/grant checks before sharing. |
| Wallpaper | `WallpaperCatalog`, `WallpaperRenderer`, `WallpaperImageStore`, `WallpaperImages`, `WallpaperController`, presentation/picker owners | Registry, pixel generation, file slots, decoding and application are separate responsibilities. |
| Widgets | `WidgetRegistry`, `WidgetFlow`, `WidgetScreen` | Device-local IDs and pending bind/config state; Android host effects remain platform-owned. |
| Setup / tutorials | `FirstRunState`, setup/pages/motion owners; search tutorial state/session/view owners | Provisional answers; confirmed completion writes; replay keeps current wallpaper. |
| Errors / reports | `GroveErrorRegistry`, `GroveErrorPresenter`, `CrashReporter`, report store/formatter/handoff | Scoped recovery; bounded private reports; explicit external handoff never implies delivery. |
| Native | `CoreBridge`, `PortablePolicy`, `NativeResults`, Rust modules | Result bounds and shape checks before app state; native absence/failure enters recovery. |

### Execution lifetimes

`CatalogController`, `SearchController`, `SearchSources`, `SearchLiveQueries`, `ContactActions` and `WallpaperController` own their executors. `MainActivity.onDestroy` delegates shutdown to those owners; generations/cancellation signals invalidate late results. `SettingsSession.onCleared` cancels document generations, shuts down its executor and removes callbacks. Durable WorkManager work survives Activity destruction. `IndexWork` scheduling and the contact deadline scheduler live for the process lifetime. `ContactChanges` registers only while source/grant eligibility holds; Activity observers are lifecycle-scoped.

Executors do not authorize publication. Every protected result must still match its current generation, source switch, Android grant and source/cache state. Shutdown interruption is best effort; provider/file operations also receive explicit cancellation checks.

## Configuration contract

`SettingsRepository.update` takes the latest snapshot, optionally checks an expected revision, validates the candidate, confirms `ConfigStore.save`/`activate`, then publishes a new revision. A failed write leaves the active snapshot and revision unchanged. A publication callback failure is an invariant failure after persistence and must not be presented as a failed save.

`ConfigCodec` sends bounded text to Rust `config::canonical`: validate schema, migrate versions 1–12, resolve stable wallpaper IDs, apply historical defaults, deduplicate favorites/folder members and reject duplicate folder names or multiple folder ownership. Native invalid input is an error result, not native unavailability. Missing/malformed native output uses the explicitly named recovery decoder, with the same type/range constraints. `ConfigInput` prevents lenient Android `JSONObject` syntax/coercion differences before either path. It limits UTF-8 input to 64 KiB, nesting to 64 and rejects trailing text, invalid numbers/escapes and unpaired surrogates.

Limits: 100 favorites, 100 folders, 500 apps per folder, app IDs 3–512 UTF-16 units containing `/`, folder names 1–40 units after trim, grids 1–10 columns/rows. Theme IDs are `system`, `light`, `dark`, `wallpaper`. New exports use schema 12 and stable wallpaper IDs. Retired keys are not emitted.

Fresh installs have optional contact/file search **off**, independent indexing preferences **on**. Scheduling still requires source enabled + indexing enabled + Android grant. Legacy versions before 7 retain their prior search defaults; legacy imports and recovery defaults do not silently enable durable indexing.

Damaged saved configuration is retained as `broken_config`; recovery uses a separate fallback document and marker. Ordinary fallback changes do not overwrite the broken original. Explicit activation replaces the active document and clears recovery keys only after a successful commit.

### Import, export and editing

Android document pickers supply URIs. The settings ViewModel reads at most 64 KiB with strict UTF-8 decoding, validates off-thread and produces a review candidate tied to its starting `SettingsSnapshot`. Only explicit Apply activates it. A changed revision rejects Apply even if values later return to their previous state. Navigation/cancellation invalidates stale document callbacks. Export writes the current portable Config to the selected destination and checks output completion; exports exclude widget IDs, Android grants, report data and tutorial markers. Import does not grant permissions or call Android wallpaper APIs.

`SettingsActivity` is non-exported. Exported `SettingsEntryActivity` supports Android's application-preferences entry point but forwards no external extras. Internal routes/drafts/document URIs are passed only within the app. `MainActivity` is the exported Home/launcher entry; its fixed navigation actions expose user-controlled UI, not silent configuration replacement.

## Search, settings and actions

Labels are bounded and prepared once per catalogue/cache/scan batch. Native normalization uses NFD, removes marks, lowercases and trims with the JVM-compatible contract. `Search.prepare` limits query text to 256 UTF-16 units and splits Java ASCII whitespace. App/contact/file ranking requires every term; exact, prefix, then substring/fuzzy matches use stable original-index ties. Fuzzy distance counts UTF-16 units, permits one edit (two for terms of at least six units), and rejects words over 64 units. Rust uses diagonal-band Levenshtein with an equal-length Hamming shortcut, early row termination and compact stack buffers; top-K avoids sorting all rejected rows. Kotlin recovery preserves ranking semantics.

Search providers are independent: apps, optional contacts/files, calculator, Grove settings, Android settings, and explicit external web/Play actions. Empty successful source results are Ready(0); disabled, permission-required, loading, partial and failed states are distinct. Cached source readiness includes age and invalidation; stale/unavailable caches use bounded live search when allowed. A late live result cannot replace a newly ready authoritative cache. Identical UI frames do not rebuild rows or disturb focus/scroll.

Settings discovery uses an immutable catalogue with typed destinations. Matching scores all aliases, aggregates by entry, then returns stable ordering. Android Settings routes resolve only enabled/exported/system/permitted activities from the currently resolved Settings packages, and resolve again at launch. Missing routes and launch failures are distinct; generic Android search is a labeled fallback. Web/Play/AI queries leave the device only after the user chooses the external action; links are encoded and HTTPS-checked. Grove itself declares no INTERNET permission.

Calculator syntax is bounded arithmetic ending in `=`; no scripting engine or functions. Exact decimal addition/subtraction/multiplication and terminating division preserve values. Nonterminating division uses 34-digit half-even precision and marks approximation. Bounds: 256 input units, 64-unit literals, depth 16, 128 operations, coefficient/scale/output bounds. Invalid syntax, division by zero and limits are separate results. Rust uses arbitrary-precision coefficients; JVM BigDecimal remains the explicit recovery oracle.

Contact details and messaging channels are queried on demand, bounded to 80 useful rows / 1,000 raw rows / a 2.5-second cancellation deadline. Contact action shutdown cancels active provider signals. Phone deduplication preserves JDK 17 BMP digit semantics. Channels are collapsed by app/business variant; WhatsApp targets require both synced contact data and current installation. Dialing opens a dialer confirmation. Before a contact action, details and source/grant authority are rechecked. Shared files receive read-only temporary URI permission through `SharedFileProvider`; current source/grant is checked again at provider open, metadata reads and MIME lookup. Rust opens a regular file component-by-component relative to a root descriptor using O_NOFOLLOW, including intermediate directories, and transfers the owned descriptor to Android. Writes/deletion are refused. Native absence fails this optional file handoff closed while Home/search recovery remains available; no automatic file contents are indexed or uploaded. Uninstall uses Android's confirmation and advances only one package at a time; cancel stops the remaining batch.

## Indexes and cancellation

Persistent contact/file snapshots are separate versioned AtomicFiles under app-private storage. Limits: 12 MiB read/write, 50,000 contacts, 15,000 files. Contact freshness is 15 minutes; file freshness is one day. Contacts may visit 100,000 raw rows; file scans visit at most 100,000 entries / 10,000 directories / 10,000 queued directories. Live queries have a 2.5-second budget. File walking is lazy, skips links and protected Android/data/obb subtrees, reports skipped/overflow/deadline work as partial, and checks canonical root containment. Ordinary folders named `data`/`obb` are searchable.

WorkManager jobs have per-source durable UUID tokens and constraints requiring non-low battery/storage. `begin` persists started/last-started state; provider refreshes coalesce into one follow-up, rather than replacing an active scan. Automatic repair is bounded and manual retry resets its marker. Recovery clears reservations only for the matching missing/finished job, with confirmed writes.

Cancellation blocks the source in `IndexRevocationGate` before acquiring the commit lock. Under the same lock used by `IndexCache.write`, it confirms removal of durable token/work metadata, requests WorkManager cancellation and deletes the cache. A failed preference commit stays blocked in-process and reports failure; cleanup is skipped. After confirmed revocation every old worker is ineligible even if WorkManager takes time to stop it. Deletion checks base/backup/new files before declaring the cache absent. Failure reports a retryable status rather than claiming success. Repeated cancellation with no reservation/cache avoids unnecessary writes/work notifications. A newly confirmed reservation may renew eligibility.

Only bounded I/O and final metadata confirmation hold the cache lock. Large JSON decoding and label preparation run outside it; a generation check rejects a snapshot changed while decoding. Cache metadata/count/row bounds are checked before exposure. Android grant and source switches are always rechecked by the source owner and action owner; stored preferences/native code cannot grant access.

## Home, grids, gestures, setup and widgets

Home starts before optional indexes. Core startup failures show retry and Android Home escape routes without erasing saved configuration. App catalogue success publishes rows before icon batches; icon failures degrade visuals. Home pin/grid/folder transforms return complete Config candidates, validated at the repository boundary. Custom grid capacity creates pages rather than truncating data; page arithmetic saturates/clamps before slicing. Pin shifts avoid integer overflow and preserve identity/ties.

`GestureSession` passes one complete event snapshot to Rust for capture/move/release/long-press/cancel transitions. Android routing owns child cancellation, widget/interactive hit testing, scroll authority, MotionEvent dispatch and visual settling. Gesture thresholds/time/float semantics match the recovery reducer. Home and drawer gestures never authorize arbitrary platform actions.

Setup answers remain provisional until Finish; denied source grants disable that source without resetting its indexing preference. Portable page visibility, paging, pin bounds/order and merge decisions run in Rust. Setup merges only lanes changed by the draft into the latest configuration; an overlapping changed lane yields conflict. Search tutorial state/completion is separate; failed completion writes do not claim success and session suppression prevents repeated disruption. Replay markers are app-private and independent from Config.

Widget IDs belong to the Android host and are excluded from export. A pending allocation is persisted before exposure; provider absence/cancel releases it. Completed/removal state is published only after confirmed preference writes. Saved-state restores preserve in-progress bind/config flows; cold startup reconciles abandoned pending IDs. Android binder/provider effects cannot be rolled back transactionally with preferences; device validation must cover interrupted flows.

## Wallpapers and presentation

Built-ins are packaged offline; `WallpaperCatalog` owns source IDs/license/resource metadata. Rust generates pixel art. `WallpaperRenderer` supplies a Canvas recovery path. `WallpaperImageStore` owns committed/candidate/backup paths; `WallpaperImages` performs Android bounds-decode/bitmap creation using portable sampling/crop geometry. Previews request 360×800 rather than decoding/rendering full-resolution art first.

Custom import requires image MIME, at most 20 MiB and decoded bounds 1–8192 per dimension. `BoundedInput` handles zero-length reads and real byte limits. A validated candidate stays separate from committed art. Android apply is performed on the wallpaper executor; the candidate is promoted only when Home application succeeds. Platform failure and post-apply local/config persistence failure are different outcomes. Interrupted promotion restores a backup when needed. Bitmap owners recycle discarded/destroyed outputs; Home reads committed custom content.

Android remains wallpaper authority: Home uses FLAG_SHOW_WALLPAPER, not a copied system wallpaper. Presentation observes wallpaper identity/color metadata and theme preference. System/Light/Dark/Wallpaper colors are local; external wallpaper changes refresh optional colors, not Config history. Wallpaper failure uses scoped Recover/Degrade actions; it does not close a usable launcher.

## Errors, reports and security pass

Error code ownership: 100–199 configuration, 200–299 catalogue, 300–399 UI/wallpaper, 400–499 system, 500–599 files, 600–699 contacts. `GroveErrorRegistry.all` is the code/severity catalogue; `GroveErrorPresenter` renders scoped actions. Stop remains reserved for an uncaught runtime crash. Native helper failure is Degrade with bounded diagnostics.

Automatic local crash capture defaults on and can be disabled. Explicit user reports remain available. Report formatting omits exception messages and sensitive runtime values; only type/filtered stack (24 frames), error identity, version and device details are included. Store writes are serialized, use unique filenames and retain ten reports. Handoff reads bounded text, creates an email draft or copy fallback, and never deletes reports on chooser success. Explicit discard checks deletion. There is no telemetry/account/network reporting SDK. Backup/device-transfer rules exclude private app state.

| Finding | Enforcement / remedy | Verification and residual boundary |
|---|---|---|
| Exported Settings accepted internal URI/draft extras | Non-exported host plus extras-free public entry Activity | Manifest/source inspection; adversarial Intent test on hardware remains required. |
| Cancel ignored preference failure; old worker could publish | Revocation gate + durable checked commit under cache lock | Failed-write and concurrent-write tests; Android WorkManager/AtomicFile integration on hardware. |
| Cache/read/image bounds relied on file stat or stream progress | Exact byte reader, strict UTF-8, row/count bounds, decode bounds | Zero-read/overflow tests; malformed provider and storage-failure device checks. |
| Indexed file authority could change | Canonical containment, link/ancestor checks, protected-subtree and live grant checks | Rust leaf/ancestor-link, traversal/protected-path/non-file and concurrent ancestor-swap tests; custom provider opens descriptor-relative and read-only, rechecks grants, and refuses unsafe opens. Android URI-grant/viewer and ARMv7/ARM64/x86-64 behavior still need device validation. |
| JNI inputs/results and panics | Input/label/total-byte/pixel/envelope/output bounds, per-export panic containment | Native-loaded JVM suite plus malformed result tests; ABI/device checks still required. |
| Optional wallpaper failure closed launcher | Recover severity and scoped recovery UI | Error-routing tests; actual apply failure on hardware. |
| Diagnostics collisions/unbounded reads | UUID names, serialized retention, bounded handoff | Privacy/prompt tests and source review; inspect actual email draft on device. |
| Android settings could resolve untrusted handler | Current system package facts, allowlist, re-resolution | Capability tests; OEM availability varies. |
| Dependency/supply-chain drift | Exact Rust versions + Cargo.lock + `--locked`; resolved Maven/Rust advisory jobs | Advisory-service failures fail verification. A clean advisory scan is scoped evidence, not proof of absence of vulnerabilities. |

The security pass covers source trust boundaries, grants, exported components, private storage, diagnostics, JNI, bounded inputs and dependency advisories. It is not penetration testing of Android/OEM components. Findings requiring hardware evidence stay tracked rather than being called fixed by static review.

## Rust migration boundary

| Rust owner | Portable responsibilities |
|---|---|
| `config.rs` | Schema validation, canonical migration/defaults, duplicate ownership rules. |
| `calculator.rs` | Bounded exact/approximate decimal parser and arithmetic. |
| `search.rs` | UTF-16 scoring, banded fuzzy comparison, stable top-K. |
| `policy.rs` | Versioned operation dispatch, normalization, grid/gesture primitives, pin ordering, settings aggregation, refresh/access/publication decisions. |
| `gesture_session.rs` | Complete portable touch-event transition. |
| `decisions.rs` | Source/cache/recovery/trust/report/setup/tutorial rules and page/pin decisions. |
| `config_edits.rs` | Folder mutations, pin union and conflict-aware setup merge. |
| `contacts.rs`, `actions_policy.rs`, `reports.rs` | Stable phone digit normalization, channel collapse/target facts, uninstall queue transitions and safe-field report formatting. |
| `mime.rs`, `wallpaper.rs` | Grove extension overrides and generated pixels. |
| `shared_file.rs`, `bridge.rs` | Descriptor-relative regular-file opening; Linux/Android FFI, owned FD transfer, JNI conversion, bounds, panic containment and response envelopes. |

Coarse calls operate on a full query, source batch, configuration/edit, image geometry or touch event. Label batches are capped at 1,024 to bound JNI allocation/reference pressure. JNI label arrays cap at 50,000 / 4,096 units per label / 16 MiB total; generated output caps at 16 million pixels. Policy JSON caps input/output at 192 KiB and uses version 1 with exactly one of value/error. Native errors cannot supply Android authority. Results are checked before constructing state/bitmaps.

Linux/Android filesystem opening is also Rust-owned; kernel flags explicitly account for ARMv7 differences and all intermediate links are rejected. File handles transfer exactly once to ParcelFileDescriptor. The cross-platform Home recovery path does not fall back to unsafe path opening.

Remaining Kotlin is intentional for Android API calls, View/resource/binder/provider lifetimes, permission querying, file/preference confirmation, locks, callback sequencing, runtime identity/focus/frame snapshots, DTO projection and bounded bridge guards/recovery. Generic collection slicing or applying a returned DTO remains adapter work. Android Bitmap operations cannot be replaced by portable Rust pixel processing without transferring ownership through the platform. Recovery code intentionally duplicates portable behavior to keep Home usable when a native library is missing or rejected, and is exercised separately from the native path. Portable helper migration continues to be audited under #120; do not treat every remaining Kotlin line as an Android effect merely because it lives in a controller.

## Performance pass

Changes remove per-row JNI normalization, use bounded batches, short-circuit exact search before UTF-16 allocation, replace full fuzzy matrices with diagonal bands/early exit/stack arrays, prepare smaller wallpaper previews, avoid idle cancellation writes, and move large cache decoding outside the cancellation lock. Existing lazy filesystem walking, bounded live queries, prepared labels, top-K selection and icon batches remain enforced.

`verification/performance/search-baseline.rs` is the unchanged baseline kernel from `0e9eb607`, used only for comparison and never linked into the app. `search-benchmark.rs` runs 35 samples for 15,000/50,000 synthetic rows, exact/typo/no-match queries, checks result parity and records p50/p95 CPU microseconds. Both paths use the same top-K to isolate scoring changes. CI uploads `search-benchmark.csv`. On `e2657c32`, the 50k-row baseline/candidate p50 microseconds were 1348/467 (exact), 6654/1013 (typo), and 22324/12765 (no match); p95 were 1383/508, 6741/1051 and 23341/12887. These compare the current prepared-query path against the original per-label term preparation, not just matrix arithmetic. This host kernel benchmark does not measure JNI, Android UI, realistic provider/storage costs, device memory or frame time. Do not extrapolate it into device latency claims.

Device performance evidence must record model/OS/ABI, corpus size, warm/cold state, build SHA, median/p95 and memory before/after. Cover cold Home launch, icon publication, 15k files/50k contacts, rapid typing, large directory/provider behavior, canceled work, wallpaper previews and Activity teardown. Use the checklist below; no Android emulator/VM is assumed.

## Build status

CI workflow `.github/workflows/android.yml` is authoritative for each tested SHA; current evidence is linked from [PR #130](https://github.com/davidcit646/Grove/pull/130). Source `1ae8a522` passed the complete [workflow](https://github.com/davidcit646/Grove/actions/runs/37792872827): 22 Rust tests, Android debug assembly, fallback and native-loaded JVM suites, lint, resolved Maven advisory checks and the locked Rust advisory audit. Later changes add a JNI descriptor ownership test, case-insensitive protected directory rejection and strict formatting verification; consult the PR checks for their exact tested head. An earlier native-loaded run caught an opaque-ID folder adapter error, and the first benchmark exposed a typo regression; both were corrected and rerun before this proof.

A passing debug/source run is not a signed release or device acceptance. CI artifacts include resolved dependencies/advisory reports, Cargo.lock, test XML, lint XML, native source and benchmark CSV. Native tests assert the host cdylib actually loaded. Fallback/native suites run separately. The exhaustive fuzzy test compares against a full matrix over every pair of binary words through length six and budgets 0–2. Historical `verification/0.1.*` files are not evidence for this candidate.

### Local build and checks

Requires JDK 17, Android SDK 36, NDK 27.3.13750724 and Rust with arm64/ARMv7/x86-64 Android targets.

```sh
cargo test --locked --manifest-path rust/grove-core/Cargo.toml
./gradlew assembleDebug testDebugUnitTest lintDebug
cargo build --locked --manifest-path rust/grove-core/Cargo.toml
./gradlew testDebugUnitTest -PgroveNativeTests=true --rerun-tasks
rustc -O verification/performance/search-benchmark.rs -o /tmp/grove-search-benchmark
/tmp/grove-search-benchmark
```

### Signing readme

The signing password comes from `GROVE_SIGNING_PASSWORD`; no password is stored in documentation/source. Release signing uses the existing PKCS#12 alias/certificate. `scripts/verify-release.sh` fails before artifact checks when the secret is absent; it verifies APK/AAB certificate, all three Rust ABIs, zip alignment, 16 KiB ELF LOAD alignment and hashes, then emits commit/certificate/checksum provenance. Do not publish a debug APK as a stable release.

```sh
./gradlew assembleRelease bundleRelease
bash scripts/verify-release.sh
```

Main-branch CI builds/verifies signed release artifacts. Stable publication additionally requires a main push with `[release]`; version is read from Gradle and must be a numeric semantic version. Existing tags fail rather than silently replacing assets. The workflow no longer pins publication to 1.0.0. Code 33 / 1.0.1 is the candidate version, not a claim that it has been released.

## Testing

Hardware acceptance is separate and pending until recorded against the final SHA:

- Install the debug candidate alongside stable Grove; keep another Home app available. Test cold launch, role selection, app list failure/retry, core recovery escape and rotation/recreation.
- Verify Home scroll/widgets/interactive targets, drawer capture/release, multi-pointer cancel, context menus, grids/pages, pin/folder edits and rapid navigation. Compare with baseline behavior and use TalkBack/font scaling.
- Fresh install: contact/file search off, indexing preference on but no protected job/read. Grant, deny and revoke permissions with live query/cache/work active; late output must not publish. Retry failed writes/deletion; inspect status and private cache files.
- Persist workers across process death; trigger provider refresh during scan; cancel just before cache commit; restore missing/finished WorkManager reservation; verify bounded repair and manual retry.
- Import supported old/current documents, invalid types/syntax/UTF-8/depth/size; cancel slow reads, mutate configuration while reviewing, rotate editor/grid/email drafts and confirm explicit Apply only.
- Attempt public settings Intents with route/draft/importUri/exportUri extras from another app; extras must not reach internal Settings. Exercise fixed Home navigation extras and confirm no silent settings mutation.
- Test files with ordinary data/obb directory names, symbolic links, unreadable/large folders, canonical escape and concurrent renames. Open stale/deleted files and check URI permission scope and fallback MIME.
- Contact provider deadline/failure/large unusable-row corpus, deletion/number change before action, work/business messaging targets and chooser cancellation.
- Wallpaper valid/oversized/corrupt/zero-read image, preview cancellation, Android apply failure, post-apply preference/local promotion failure, cold backup recovery and external/live wallpaper changes. Home remains usable; inspect bitmap/memory behavior.
- Widget bind/config cancellation, provider removal, interrupted preference writes, restore pending IDs and delete failures; verify no leaked IDs or fabricated success.
- Disable automatic capture, explicitly report, inspect draft privacy/recipient, cancel chooser, copy fallback and discard failure. No delivery or deletion is inferred from a launched composer.
- Record cold/warm performance and memory with the same corpus/device before/after; test supported ABIs and at least one 16 KiB page-size device when available. Record actual signed-release verification before distribution.

## Play readiness

Source cleanup does not resolve store eligibility. `MANAGE_EXTERNAL_STORAGE` remains an optional file-search capability with a Play policy gate (#111); submission materials/approval remain #112. Backup is disabled, there is no INTERNET permission, and PRIVACY.md describes source/index defaults, retention, recovery and explicit handoffs. Review actual merged manifest/data-safety/store declarations before submission. Do not close hardware, Play or release tasks based only on CI.

## Source catalogue

Paths below are relative to the repository. Entry lists are declaration indexes, not claims that every method is public. Closely related UI builders are intentionally separate from portable policy and persistence owners.

| Source | Main declarations / entry points |
|---|---|
| [ActionController.kt](app/src/main/java/tech/granet/grove/ActionController.kt) | `ActionController`, `shutdown`, `sharedFileUri`, `contactMenu`, `openFile`, `openPlayStore`, `appMenu`, `searchItemMenu`, `webResultMenu`, `playStoreMenu`, `showActionMenu`, `openWeb`, `uninstallSelected`, `launchNextUninstall` |
| [AndroidSettingsRouter.kt](app/src/main/java/tech/granet/grove/AndroidSettingsRouter.kt) | `AndroidSettingsRouter`, `settingsPackages`, `resolve`, `handler`, `snapshot`, `logFailure`, `open` |
| [AppCatalog.kt](app/src/main/java/tech/granet/grove/AppCatalog.kt) | `App`, `CatalogState`, `Loading`, `Ready`, `Degraded`, `Failed`, `CatalogPipeline`, `AppCatalog`, `run`, `load` |
| [AppIconStore.kt](app/src/main/java/tech/granet/grove/AppIconStore.kt) | `SizedCache`, `AppIconStore`, `useSize`, `get`, `snapshot`, `replace`, `putAll`, `clear`, `snapshotExcluding` |
| [BoundedInput.kt](app/src/main/java/tech/granet/grove/BoundedInput.kt) | `BoundedInput`, `read` |
| [CalculatorActions.kt](app/src/main/java/tech/granet/grove/CalculatorActions.kt) | `CalculatorActions`, `open` |
| [CatalogController.kt](app/src/main/java/tech/granet/grove/CatalogController.kt) | `CatalogController`, `shutdown`, `loadApps`, `publishIcons`, `update` |
| [CommandFeedback.kt](app/src/main/java/tech/granet/grove/CommandFeedback.kt) | `checked` |
| [ConfigCodec.kt](app/src/main/java/tech/granet/grove/ConfigCodec.kt) | `ConfigCodec`, `encode`, `parse`, `resolveNative`, `recovery`, `decode`, `section`, `flag`, `grid`, `dimension` |
| [ConfigController.kt](app/src/main/java/tech/granet/grove/ConfigController.kt) | `ConfigController`, `load`, `resume`, `commitConfig`, `activateConfig`, `commit`, `editConfig`, `showConfigRecoveryDialog`, `exportDocument`, `importDocument` |
| [ConfigDocumentGate.kt](app/src/main/java/tech/granet/grove/ConfigDocumentGate.kt) | `ConfigDocumentGate`, `canActivate` |
| [ConfigDocuments.kt](app/src/main/java/tech/granet/grove/ConfigDocuments.kt) | `ConfigDocuments`, `read`, `write` |
| [ConfigInput.kt](app/src/main/java/tech/granet/grove/ConfigInput.kt) | `ConfigInput`, `Parser`, `validate`, `fail`, `whitespace`, `take`, `document`, `value`, `literal`, `number`, `digits`, `string` |
| [ConfigModels.kt](app/src/main/java/tech/granet/grove/ConfigModels.kt) | `GestureSettings`, `HomeScreenSettings`, `AppFolder`, `SearchSettings`, `Config`, `json`, `parse` |
| [ConfigStore.kt](app/src/main/java/tech/granet/grove/ConfigStore.kt) | `ConfigStore`, `parse`, `load`, `save`, `activate` |
| [ContactActions.kt](app/src/main/java/tech/granet/grove/ContactActions.kt) | `ContactActions`, `shutdown`, `details`, `menu`, `launch`, `show`, `launchCurrent`, `action`, `callRow`, `textRow` |
| [ContactChanges.kt](app/src/main/java/tech/granet/grove/ContactChanges.kt) | `ContactChanges`, `settings`, `eligible`, `onChange`, `reconcile` |
| [ContactIndex.kt](app/src/main/java/tech/granet/grove/ContactIndex.kt) | `ContactIndex`, `Contact`, `Number`, `Channel`, `Details`, `WhatsAppTarget`, `ScanResult`, `Raw`, `ContactCoverage`, `normalizeNumber`, `channelArgs`, `collapseChannels`, `whatsAppTargets`, `load`, `details`, `isPartial` |
| [ContactScanBudget.kt](app/src/main/java/tech/granet/grove/ContactScanBudget.kt) | `ContactScanBudget`, `expired`, `exhausted`, `visited` |
| [CoreBridge.kt](app/src/main/java/tech/granet/grove/CoreBridge.kt) | `CoreBridge`, `malformed`, `native`, `normalizeNative`, `searchNative`, `classifyNative`, `renderWallpaperNative`, `openSharedNative`, `closeSharedNative`, `policyNative`, `normalizeAll`, `openShared`, `closeShared`, `searchOrder`, `fallbackOrder`, `classifyTable`, `extensionOverride`, `renderWallpaper`, `portable` |
| [CoreRecoveryPolicy.kt](app/src/main/java/tech/granet/grove/CoreRecoveryPolicy.kt) | `CoreRecoveryReason`, `CoreRecoveryState`, `CoreRecoveryPolicy`, `forReason` |
| [CoreRecoveryView.kt](app/src/main/java/tech/granet/grove/CoreRecoveryView.kt) | `CoreRecoveryView`, `render` |
| [CrashReporter.kt](app/src/main/java/tech/granet/grove/CrashReporter.kt) | `ReportPromptPolicy`, `ReportHandoffPolicy`, `CrashReporter`, `shouldPrompt`, `hasMailHandler`, `install`, `isEnabled`, `setEnabled`, `developerEmail`, `setDeveloperEmail`, `reportUserRequested`, `promptIfPending`, `reviewPending`, `prompt`, `pendingCount`, `deleteAll`, `buildBody`, `safeDiagnostic`, `pendingReports`, `writeReport`, `reportsDir`, `prefs` |
| [DiagnosticReportFormatter.kt](app/src/main/java/tech/granet/grove/DiagnosticReportFormatter.kt) | `DiagnosticReportFormatter`, `legacyPackageInfo`, `buildBody`, `safeDiagnostic` |
| [DiagnosticReportHandoff.kt](app/src/main/java/tech/granet/grove/DiagnosticReportHandoff.kt) | `DiagnosticReportHandoff`, `sendReports`, `showCopyFallback` |
| [DiagnosticReportStore.kt](app/src/main/java/tech/granet/grove/DiagnosticReportStore.kt) | `DiagnosticReportStore`, `reportsDir`, `pendingReports`, `writeReport` |
| [DiagnosticsCommands.kt](app/src/main/java/tech/granet/grove/DiagnosticsCommands.kt) | `DiagnosticsCommands`, `capture`, `email`, `deleteReports` |
| [DrawerController.kt](app/src/main/java/tech/granet/grove/DrawerController.kt) | `DrawerController`, `tileHeight`, `clearAppSelection`, `showDrawer`, `beforeTextChanged`, `onTextChanged`, `afterTextChanged`, `renderApps`, `launchDrawerApp`, `createTile`, `bindTile`, `refreshDrawer`, `bindDrawerApp`, `bindDrawerFolder`, `drawerOptions` |
| [DrawerDragController.kt](app/src/main/java/tech/granet/grove/DrawerDragController.kt) | `DrawerDragController`, `Drag`, `attach`, `release` |
| [DrawerState.kt](app/src/main/java/tech/granet/grove/DrawerState.kt) | `DrawerState`, `isSelected`, `clear`, `clearKeys`, `toggleMode`, `toggle`, `select`, `createFolder`, `renameFolder`, `deleteFolder`, `moveToFolder`, `removeFromFolders`, `pin`, `movePin` |
| [DrawerTiles.kt](app/src/main/java/tech/granet/grove/DrawerTiles.kt) | `DrawerTiles`, `Item`, `Application`, `Folder`, `Tile`, `Adapter`, `minimumCellHeight`, `create`, `bind`, `submit`, `getCount`, `getItem`, `getItemId`, `getView` |
| [FileActions.kt](app/src/main/java/tech/granet/grove/FileActions.kt) | `FileActions`, `shareUri`, `open`, `openAs` |
| [FileIndex.kt](app/src/main/java/tech/granet/grove/FileIndex.kt) | `IndexedFile`, `FileIndex`, `ScanResult`, `Raw`, `scan`, `expired`, `categoryOf` |
| [FirstRunComponents.kt](app/src/main/java/tech/granet/grove/FirstRunComponents.kt) | `FirstRunComponents`, `color`, `text`, `icon`, `card`, `heading`, `choice`, `action` |
| [FirstRunMotion.kt](app/src/main/java/tech/granet/grove/FirstRunMotion.kt) | `FirstRunMotion`, `FirstRunMotionPolicy`, `finish`, `fade`, `slide`, `run`, `onAnimationEnd`, `offset` |
| [FirstRunPages.kt](app/src/main/java/tech/granet/grove/FirstRunPages.kt) | `FirstRunPages`, `pausePractice`, `resumePractice`, `destroyPractice`, `render`, `update`, `updateCounter`, `updateList`, `beforeTextChanged`, `onTextChanged`, `afterTextChanged`, `permissionPage` |
| [FirstRunSetup.kt](app/src/main/java/tech/granet/grove/FirstRunSetup.kt) | `FirstRunSetup`, `saveState`, `show`, `onInterceptTouchEvent`, `refreshPermissions`, `settleMotion`, `back`, `refreshPage`, `displayPage`, `updateNavigation`, `close`, `destroy` |
| [FirstRunState.kt](app/src/main/java/tech/granet/grove/FirstRunState.kt) | `FirstRunState`, `pages`, `nativeStep`, `back`, `next`, `preparedPins`, `snapshot`, `restore`, `togglePin` |
| [FolderActions.kt](app/src/main/java/tech/granet/grove/FolderActions.kt) | `FolderActions`, `options`, `open`, `getCount`, `getItem`, `getItemId`, `getView`, `move`, `promptCreate`, `promptRename`, `choose` |
| [FolderPolicy.kt](app/src/main/java/tech/granet/grove/FolderPolicy.kt) | `FolderPolicy`, `NativeConfig`, `createFolder`, `renameFolder`, `deleteFolder`, `moveToFolder`, `removeFromFolders`, `pin`, `movePin`, `native`, `identifiers` |
| [GestureSession.kt](app/src/main/java/tech/granet/grove/GestureSession.kt) | `GestureSession`, `Move`, `Release`, `native`, `begin`, `verticalDelta`, `longPress`, `cancel`, `move`, `release` |
| [Gestures.kt](app/src/main/java/tech/granet/grove/Gestures.kt) | `HomeGesture`, `Gestures`, `resolve`, `drawerClose` |
| [GridPolicy.kt](app/src/main/java/tech/granet/grove/GridPolicy.kt) | `IconGrid`, `GridPolicy`, `columns`, `pageCount`, `page`, `items`, `native` |
| [GroveApp.kt](app/src/main/java/tech/granet/grove/GroveApp.kt) | `GroveApp`, `onCreate` |
| [GroveErrorPresenter.kt](app/src/main/java/tech/granet/grove/GroveErrorPresenter.kt) | `GroveErrorPresenter`, `show` |
| [GroveErrors.kt](app/src/main/java/tech/granet/grove/GroveErrors.kt) | `GroveErrorOwner`, `ErrorSeverity`, `GroveError`, `GroveErrorRegistry`, `GroveErrorRoute`, `GroveErrorRouting`, `codeLine`, `byCode`, `route` |
| [HomeController.kt](app/src/main/java/tech/granet/grove/HomeController.kt) | `HomeController`, `bodyInitialized`, `button`, `base`, `openSearch`, `openAppDrawer`, `openClock`, `openCalendar`, `enterContent`, `showHome`, `rememberHomeScroll`, `renderPinnedApps`, `addGrid`, `move`, `resetSwipeFeedback`, `settleSwipeFeedback`, `animateHomeGesture`, `animateDrawerClosed` |
| [HomeScreen.kt](app/src/main/java/tech/granet/grove/HomeScreen.kt) | `HomeScreen`, `render` |
| [HomeTouchRouter.kt](app/src/main/java/tech/granet/grove/HomeTouchRouter.kt) | `HomeTouchRouter`, `cancel`, `dispatch`, `cancelChildTouch`, `settle`, `pointInside`, `scrollAt`, `insideWidget`, `insideInteractive` |
| [IndexAccessPolicy.kt](app/src/main/java/tech/granet/grove/IndexAccessPolicy.kt) | `IndexAccessPolicy`, `contacts`, `files`, `eligible` |
| [IndexCache.kt](app/src/main/java/tech/granet/grove/IndexCache.kt) | `IndexCache`, `Snapshot`, `metadata`, `record`, `inspect`, `verified`, `invalidate`, `invalidated`, `generation`, `published`, `target`, `exists`, `clear`, `writeFiles`, `writeContacts`, `write`, `read`, `verifyRead`, `files`, `contacts` |
| [IndexMetadata.kt](app/src/main/java/tech/granet/grove/IndexMetadata.kt) | `IndexValidity`, `IndexMetadata`, `fresh` |
| [IndexRecoveryPolicy.kt](app/src/main/java/tech/granet/grove/IndexRecoveryPolicy.kt) | `IndexRecoveryPolicy`, `release`, `repairAllowed` |
| [IndexRefreshRequests.kt](app/src/main/java/tech/granet/grove/IndexRefreshRequests.kt) | `IndexRefreshCause`, `IndexRefreshRequests`, `delayMillis`, `request` |
| [IndexRevocationGate.kt](app/src/main/java/tech/granet/grove/IndexRevocationGate.kt) | `IndexRevocationGate`, `allowed`, `renew`, `cancel` |
| [IndexState.kt](app/src/main/java/tech/granet/grove/IndexState.kt) | `IndexState`, `resolve` |
| [IndexStatusController.kt](app/src/main/java/tech/granet/grove/IndexStatusController.kt) | `IndexStatusController`, `observeIndex`, `indexStatus`, `indexActivity`, `retryIndex` |
| [IndexWork.kt](app/src/main/java/tech/granet/grove/IndexWork.kt) | `IndexWork`, `failure`, `recover`, `prefs`, `name`, `token`, `started`, `lastStarted`, `pending`, `repair`, `workId`, `currentWorkId`, `enabled`, `allowed`, `begin`, `finished`, `enqueue`, `enqueueRecovered`, `schedule`, `cancel`, `reconcile` |
| [IndexWorker.kt](app/src/main/java/tech/granet/grove/IndexWorker.kt) | `IndexWorker`, `onStopped`, `doWork` |
| [LauncherPackageEvents.kt](app/src/main/java/tech/granet/grove/LauncherPackageEvents.kt) | `LauncherPackageEvents`, `changed` |
| [MainActivity.kt](app/src/main/java/tech/granet/grove/MainActivity.kt) | `MainActivity`, `onPackageAdded`, `onPackageRemoved`, `onPackageChanged`, `onPackagesAvailable`, `onPackagesUnavailable`, `onCreate`, `handleOnBackPressed`, `onStart`, `onResume`, `onStop`, `onDestroy`, `onSaveInstanceState`, `onNewIntent`, `dispatchTouchEvent` |
| [NativeEnvelope.kt](app/src/main/java/tech/granet/grove/NativeEnvelope.kt) | `NativeEnvelope`, `decode` |
| [NativeFailureReporter.kt](app/src/main/java/tech/granet/grove/NativeFailureReporter.kt) | `NativeFailureReporter`, `failed` |
| [NativeResults.kt](app/src/main/java/tech/granet/grove/NativeResults.kt) | `NativeResults`, `search`, `mime`, `wallpaper` |
| [PinDragController.kt](app/src/main/java/tech/granet/grove/PinDragController.kt) | `PinDragController`, `Drag`, `scrollNearEdge`, `releaseHold`, `finishDrag`, `attach` |
| [PinnedApps.kt](app/src/main/java/tech/granet/grove/PinnedApps.kt) | `PinnedApps`, `moveTo`, `shift`, `native` |
| [PortablePolicy.kt](app/src/main/java/tech/granet/grove/PortablePolicy.kt) | `PortablePolicy`, `value`, `rule`, `ruleBool`, `ruleInt`, `bool`, `int` |
| [PresentationController.kt](app/src/main/java/tech/granet/grove/PresentationController.kt) | `PresentationController`, `start`, `applyTheme`, `refresh`, `shutdown` |
| [Search.kt](app/src/main/java/tech/granet/grove/Search.kt) | `Search`, `Query`, `normalizeAll`, `normalizeFallback`, `normalize`, `prepare`, `score`, `scoreNormalized`, `editDistanceAtMost` |
| [SearchAccessController.kt](app/src/main/java/tech/granet/grove/SearchAccessController.kt) | `SearchAccessController`, `hasContactAccess`, `requestContactAccess`, `explainContactAccess`, `requestFileAccess`, `explainFileAccess` |
| [SearchActions.kt](app/src/main/java/tech/granet/grove/SearchActions.kt) | `SearchActions`, `openWeb`, `openPlayStore`, `webResultMenu`, `playStoreMenu` |
| [SearchCalculator.kt](app/src/main/java/tech/granet/grove/SearchCalculator.kt) | `SearchCalculator`, `Result`, `NotCalculation`, `Answer`, `Invalid`, `Reason`, `InvalidExpression`, `Parser`, `calculate`, `fallback`, `parse`, `expression`, `term`, `factor`, `number`, `bounded`, `operator`, `peek`, `whitespace`, `fail` |
| [SearchController.kt](app/src/main/java/tech/granet/grove/SearchController.kt) | `SearchController`, `refreshSettings`, `sourceKey`, `refreshSources`, `cancelPending`, `shutdown`, `reconcileAccess`, `indexFiles`, `refreshContacts`, `hasContactAccess`, `requestContactAccess`, `explainContactAccess`, `requestFileAccess`, `explainFileAccess`, `applySearchSettings`, `showSearch`, `beforeTextChanged`, `onTextChanged`, `afterTextChanged`, `renderSearch`, `refreshLiveDisplay`, `displaySearch` |
| [SearchFrameGate.kt](app/src/main/java/tech/granet/grove/SearchFrameGate.kt) | `SearchFrameGate`, `shouldRender` |
| [SearchLiveQueries.kt](app/src/main/java/tech/granet/grove/SearchLiveQueries.kt) | `SearchLiveQueries`, `shutdown`, `queryLiveContacts`, `queryLiveFiles` |
| [SearchPublicationGate.kt](app/src/main/java/tech/granet/grove/SearchPublicationGate.kt) | `SearchPublicationGate`, `allowed` |
| [SearchResults.kt](app/src/main/java/tech/granet/grove/SearchResults.kt) | `SearchResults`, `Prepared`, `prepare`, `matching` |
| [SearchScreen.kt](app/src/main/java/tech/granet/grove/SearchScreen.kt) | `SearchScreen`, `AppRow`, `FileRow`, `SettingsRow`, `ContactRow`, `row`, `heading`, `render` |
| [SearchSettingsEffects.kt](app/src/main/java/tech/granet/grove/SearchSettingsEffects.kt) | `SearchSettingsEffects`, `contacts`, `files`, `protectedSources`, `reconcile` |
| [SearchSourceState.kt](app/src/main/java/tech/granet/grove/SearchSourceState.kt) | `SearchSourceState`, `Disabled`, `PermissionRequired`, `Loading`, `Ready`, `Partial`, `Failed`, `fromFileScan`, `resolve` |
| [SearchSources.kt](app/src/main/java/tech/granet/grove/SearchSources.kt) | `SearchSources`, `reconcile`, `indexFiles`, `refreshContacts`, `loadContacts`, `loadFiles`, `clearContacts`, `clearFiles`, `shutdown`, `status` |
| [SearchTutorial.kt](app/src/main/java/tech/granet/grove/SearchTutorial.kt) | `SearchTutorial`, `show`, `centered`, `feature`, `page`, `navigation`, `refresh`, `settle`, `destroy` |
| [SearchTutorialController.kt](app/src/main/java/tech/granet/grove/SearchTutorialController.kt) | `SearchTutorialController`, `SearchTutorialAvailability`, `restore`, `save`, `restoreEntry`, `interceptEntry`, `availability`, `show`, `fail`, `page`, `refresh`, `forward`, `back`, `stop`, `destroy` |
| [SearchTutorialSession.kt](app/src/main/java/tech/granet/grove/SearchTutorialSession.kt) | `SearchTutorialSession`, `shouldPresent`, `present`, `suppress` |
| [SearchTutorialState.kt](app/src/main/java/tech/granet/grove/SearchTutorialState.kt) | `SearchTutorialState`, `forward`, `back`, `completed`, `shouldShow` |
| [SettingsActivity.kt](app/src/main/java/tech/granet/grove/SettingsActivity.kt) | `SettingsActivity`, `onCreate`, `handleOnBackPressed`, `onResume`, `reconcileAccess`, `onStop`, `onDestroy`, `onSaveInstanceState`, `applyTheme`, `navigate`, `back`, `rememberScroll`, `render`, `onLayoutChange`, `delegate`, `requestAccess`, `chooseHome`, `openLink`, `permitted` |
| [SettingsCapabilities.kt](app/src/main/java/tech/granet/grove/SettingsCapabilities.kt) | `SettingsHandler`, `SettingsCapabilities`, `Availability`, `Snapshot`, `Launch`, `SettingsMatches`, `SettingsSearchFallback`, `trusted`, `allowed`, `snapshot`, `launch`, `rows` |
| [SettingsCatalogue.kt](app/src/main/java/tech/granet/grove/SettingsCatalogue.kt) | `SettingsIcon`, `SettingsDestination`, `Grove`, `Android`, `SettingsEntry`, `SettingsCatalogue`, `grove`, `android`, `entry`, `destination` |
| [SettingsCommands.kt](app/src/main/java/tech/granet/grove/SettingsCommands.kt) | `SettingKey`, `CommandFeedback`, `SettingsCommands`, `toggle`, `theme`, `grid`, `change`, `replace`, `feedback` |
| [SettingsDocumentPages.kt](app/src/main/java/tech/granet/grove/SettingsDocumentPages.kt) | `SettingsDocumentPages`, `render`, `beforeTextChanged`, `onTextChanged`, `afterTextChanged`, `button` |
| [SettingsEntryActivity.kt](app/src/main/java/tech/granet/grove/SettingsEntryActivity.kt) | `SettingsEntryActivity`, `onCreate` |
| [SettingsGridPage.kt](app/src/main/java/tech/granet/grove/SettingsGridPage.kt) | `SettingsGridPage`, `render`, `draw`, `slider` |
| [SettingsGroups.kt](app/src/main/java/tech/granet/grove/SettingsGroups.kt) | `SettingsGroups`, `card` |
| [SettingsLabels.kt](app/src/main/java/tech/granet/grove/SettingsLabels.kt) | `SettingsLabels`, `title`, `localize` |
| [SettingsMatcher.kt](app/src/main/java/tech/granet/grove/SettingsMatcher.kt) | `SettingsMatcher`, `matching`, `normalize` |
| [SettingsPages.kt](app/src/main/java/tech/granet/grove/SettingsPages.kt) | `SettingsPages`, `title`, `render`, `source`, `toggle`, `toggleAction`, `refreshStatus`, `feedback`, `row`, `link`, `gridLabel` |
| [SettingsRepository.kt](app/src/main/java/tech/granet/grove/SettingsRepository.kt) | `SettingsSnapshot`, `SettingsOutcome`, `Saved`, `Invalid`, `Conflict`, `Unavailable`, `SettingsRepository`, `snapshot`, `update` |
| [SettingsScrollState.kt](app/src/main/java/tech/granet/grove/SettingsScrollState.kt) | `SettingsScrollState`, `remember`, `position`, `snapshot`, `restore` |
| [SettingsSearchPresentation.kt](app/src/main/java/tech/granet/grove/SettingsSearchPresentation.kt) | `SettingsSearchPresentation`, `icon`, `row` |
| [SettingsSession.kt](app/src/main/java/tech/granet/grove/SettingsSession.kt) | `ConfigCandidate`, `SettingsSession`, `readDocument`, `export`, `validateDraft`, `runDocument`, `cancelDocument`, `apply`, `onCleared` |
| [SetupController.kt](app/src/main/java/tech/granet/grove/SetupController.kt) | `SetupController`, `restore`, `saveState`, `destroy`, `setupPending`, `settings`, `startFirstRunSetup`, `launcherSettings` |
| [SetupDefaults.kt](app/src/main/java/tech/granet/grove/SetupDefaults.kt) | `SetupDefaults`, `configuration` |
| [SetupMergePolicy.kt](app/src/main/java/tech/granet/grove/SetupMergePolicy.kt) | `SetupMergePolicy`, `merge`, `conflict`, `field` |
| [SharedFileProvider.kt](app/src/main/java/tech/granet/grove/SharedFileProvider.kt) | `SharedFileProvider`, `relative`, `openFile`, `query`, `getType`, `delete` |
| [StartupController.kt](app/src/main/java/tech/granet/grove/StartupController.kt) | `StartupController`, `beginHome`, `ensureLauncherCallback`, `clearCoreRecovery`, `showCoreRecovery`, `applyStartupPlan` |
| [StartupCoordinator.kt](app/src/main/java/tech/granet/grove/StartupCoordinator.kt) | `StartupCoordinator`, `Plan`, `coldStart`, `resume` |
| [SwipePracticeMotion.kt](app/src/main/java/tech/granet/grove/SwipePracticeMotion.kt) | `SwipePracticeMotion`, `Guide`, `touched`, `released`, `resume`, `pause`, `destroy`, `success`, `onAnimationEnd`, `clearSuccess`, `direction`, `start`, `onAnimationRepeat`, `stop`, `onAttachedToWindow`, `onDetachedFromWindow`, `onDraw` |
| [ThemeColors.kt](app/src/main/java/tech/granet/grove/ThemeColors.kt) | `glyphs`, `ThemeColors`, `surface`, `icon`, `iconSurface`, `buttonSurface`, `onButtonSurface`, `wallpaperButtonColors`, `resolve` |
| [ThemeMode.kt](app/src/main/java/tech/granet/grove/ThemeMode.kt) | `ThemeMode`, `PresentationPolicy`, `parse`, `wallpaperColors`, `sourceChanged` |
| [TutorialCommands.kt](app/src/main/java/tech/granet/grove/TutorialCommands.kt) | `TutorialCommands`, `searchReplayPending`, `replaySearch`, `replayPending`, `replay` |
| [TutorialReplay.kt](app/src/main/java/tech/granet/grove/TutorialReplay.kt) | `TutorialReplayDecision`, `TutorialReplayPolicy`, `decide`, `shouldSeedFavoritesOnSkip` |
| [TutorialSwipeHost.kt](app/src/main/java/tech/granet/grove/TutorialSwipeHost.kt) | `TutorialSwipeHost`, `onInterceptTouchEvent`, `onTouchEvent`, `performClick` |
| [UninstallBatch.kt](app/src/main/java/tech/granet/grove/UninstallBatch.kt) | `UninstallBatch`, `native`, `start`, `accepted`, `cancel`, `advance` |
| [WallpaperArt.kt](app/src/main/java/tech/granet/grove/WallpaperArt.kt) | `WallpaperArt`, `source`, `indexForId`, `customFile`, `customCandidateFile`, `customBackupFile`, `create` |
| [WallpaperCatalog.kt](app/src/main/java/tech/granet/grove/WallpaperCatalog.kt) | `WallpaperKind`, `WallpaperSource`, `CommonsWallpaper`, `UriCompat`, `WallpaperCatalog`, `encodeTitle`, `source`, `indexForId` |
| [WallpaperController.kt](app/src/main/java/tech/granet/grove/WallpaperController.kt) | `WallpaperApplyOutcome`, `WallpaperController`, `shutdown`, `artwork`, `importCustom`, `background`, `preview`, `apply`, `validateCustomImage`, `promoteCandidate`, `decode`, `centerCrop` |
| [WallpaperImageStore.kt](app/src/main/java/tech/granet/grove/WallpaperImageStore.kt) | `WallpaperImageStore`, `customFile`, `customCandidateFile`, `customBackupFile` |
| [WallpaperImages.kt](app/src/main/java/tech/granet/grove/WallpaperImages.kt) | `WallpaperImages`, `sample`, `decodeBundled`, `decode`, `centerCrop` |
| [WallpaperPicker.kt](app/src/main/java/tech/granet/grove/WallpaperPicker.kt) | `WallpaperPicker`, `show`, `iconButton`, `move`, `load`, `chooseDestination`, `showCredits` |
| [WallpaperPresentationController.kt](app/src/main/java/tech/granet/grove/WallpaperPresentationController.kt) | `WallpaperPresentationController`, `wallpapers`, `importCustom`, `commitHomeSelection`, `applySelection` |
| [WallpaperRenderer.kt](app/src/main/java/tech/granet/grove/WallpaperRenderer.kt) | `WallpaperRenderer`, `create`, `createCanvas` |
| [WidgetFlow.kt](app/src/main/java/tech/granet/grove/WidgetFlow.kt) | `WidgetFlow`, `pick`, `showPicker`, `configure`, `finish`, `cancel`, `render` |
| [WidgetRegistry.kt](app/src/main/java/tech/granet/grove/WidgetRegistry.kt) | `WidgetRegistry`, `restore`, `allocate`, `finish`, `cancel`, `remove` |
| [WidgetScreen.kt](app/src/main/java/tech/granet/grove/WidgetScreen.kt) | `WidgetScreen`, `render`, `installLongPress`, `showMenu`, `resize`, `configure` |
| [UiKit.kt](app/src/main/java/tech/granet/grove/ui/UiKit.kt) | `MenuRow`, `dp`, `message`, `label`, `wallpaperLabel`, `wallpaperTextContrast`, `titleText`, `bodyText`, `warningText`, `iconRow`, `menuDialog`, `infoDialog`, `confirmDialog`, `listDialog`, `scrollDialog`, `inputDialog`, `settingsButton`, `toggleRow`, `addSection` |

### Native, build and policy files

| Source | Responsibility |
|---|---|
| [actions_policy.rs](rust/grove-core/src/actions_policy.rs) | `evaluate` |
| [bridge.rs](rust/grove-core/src/bridge.rs) | `java_strings`, `Java_tech_granet_grove_CoreBridge_searchNative`, `Java_tech_granet_grove_CoreBridge_classifyNative`, `Java_tech_granet_grove_CoreBridge_renderWallpaperNative`, `Java_tech_granet_grove_CoreBridge_policyNative`, `Java_tech_granet_grove_CoreBridge_normalizeNative`, `Java_tech_granet_grove_CoreBridge_openSharedNative`, `Java_tech_granet_grove_CoreBridge_closeSharedNative` |
| [calculator.rs](rust/grove-core/src/calculator.rs) | `ten`, `normalize`, `bound`, `add`, `multiply`, `divide`, `text`, `peek`, `whitespace`, `operator`, `expression`, `term`, `factor`, `calculate`, `decimal_and_precedence`, `invalid_and_limits` |
| [config.rs](rust/grove-core/src/config.rs) | `validate_config`, `canonical`, `migration_and_duplicate_ownership` |
| [config_edits.rs](rust/grove-core/src/config_edits.rs) | `same_name`, `upper`, `lower`, `evaluate`, `folder_names_match_jvm_simple_case` |
| [contacts.rs](rust/grove-core/src/contacts.rs) | `digits`, `unicode_phone_digits` |
| [decisions.rs](rust/grove-core/src/decisions.rs) | `evaluate` |
| [gesture_session.rs](rust/grove-core/src/gesture_session.rs) | `f`, `gesture`, `evaluate` |
| [image_policy.rs](rust/grove-core/src/image_policy.rs) | `evaluate` |
| [lib.rs](rust/grove-core/src/lib.rs) | `score`, `search_provider_schema_matches_kotlin_validation`, `theme_schema_accepts_all_modes_and_rejects_invalid_values`, `ranking_preserves_existing_search_order`, `top_k_matches_kotlin_priority_queue_selection`, `top_k_large_scale_keeps_earliest_equal_scores`, `utf16_edit_distance_matches_kotlin_for_non_ascii`, `grids_validate_types_bounds_and_legacy`, `legacy_configuration_ignores_retired_fields`, `categories_and_configs`, `wallpaper_renders_opaque_pixels_at_expected_size` |
| [mime.rs](rust/grove-core/src/mime.rs) | `classify` |
| [policy.rs](rust/grove-core/src/policy.rs) | `b`, `n`, `strings`, `distinct`, `normalize`, `evaluate`, `normalization_and_bounds`, `protected_publication`, `pin_and_refresh` |
| [reports.rs](rust/grove-core/src/reports.rs) | `evaluate`, `bounded_safe_fields` |
| [search.rs](rust/grove-core/src/search.rs) | `is_java_space`, `cmp_index`, `top_indices`, `prepare_query`, `score_label`, `score_prepared`, `edit_distance_at_most`, `full`, `band_matches_full_matrix_exhaustively` |
| [shared_file.rs](rust/grove-core/src/shared_file.rs) | `open`, `openat`, `invalid`, `protected`, `owned`, `open_shared`, `new`, `root`, `drop`, `refuses_escape_links_protected_paths_and_nonfiles`, `swapping_ancestor_for_link_never_opens_outside_file` |
| [wallpaper.rs](rust/grove-core/src/wallpaper.rs) | `argb_to_f`, `f_to_argb`, `blend_over`, `lerp_color`, `gradient_at`, `render_wallpaper` |
| `app/build.gradle.kts`, root Gradle files | SDK/build/signing/dependency/native-test configuration. |
| `scripts/build-rust-android.sh` | Three Android ABIs, locked Cargo build and 16 KiB linking. |
| `scripts/verify-release.sh` | Certificate, ABI, alignment, checksums and release provenance. |
| `scripts/audit-maven.py` | Resolved runtime graph advisory check; incomplete/service failures fail the check. |
| `.github/workflows/android.yml` | Source/native/advisory/performance checks and gated signed publication. |
| `AndroidManifest.xml`, `res/xml` | Component exports, grant scope, backup/data extraction. |
| `PRIVACY.md`, `NOTICE`, `LICENSE` | Data handling and original/third-party license obligations. |

### JVM test catalogue

| Test | Test methods |
|---|---:|
| [AppCatalogTest](app/src/test/java/tech/granet/grove/AppCatalogTest.kt) | 5 |
| [BoundaryFailureTest](app/src/test/java/tech/granet/grove/BoundaryFailureTest.kt) | 3 |
| [ConfigDocumentGateTest](app/src/test/java/tech/granet/grove/ConfigDocumentGateTest.kt) | 3 |
| [ConfigDocumentsTest](app/src/test/java/tech/granet/grove/ConfigDocumentsTest.kt) | 3 |
| [ConfigTest](app/src/test/java/tech/granet/grove/ConfigTest.kt) | 25 |
| [ConfigTransactionTest](app/src/test/java/tech/granet/grove/ConfigTransactionTest.kt) | 3 |
| [ConfigWorkflowTest](app/src/test/java/tech/granet/grove/ConfigWorkflowTest.kt) | 6 |
| [ContactIndexTest](app/src/test/java/tech/granet/grove/ContactIndexTest.kt) | 8 |
| [ContactLifecycleTest](app/src/test/java/tech/granet/grove/ContactLifecycleTest.kt) | 7 |
| [CoreRecoveryPolicyTest](app/src/test/java/tech/granet/grove/CoreRecoveryPolicyTest.kt) | 3 |
| [CrashReporterPrivacyTest](app/src/test/java/tech/granet/grove/CrashReporterPrivacyTest.kt) | 1 |
| [DrawerStateTest](app/src/test/java/tech/granet/grove/DrawerStateTest.kt) | 4 |
| [FileIndexTest](app/src/test/java/tech/granet/grove/FileIndexTest.kt) | 3 |
| [FirstRunStateTest](app/src/test/java/tech/granet/grove/FirstRunStateTest.kt) | 3 |
| [GestureSessionTest](app/src/test/java/tech/granet/grove/GestureSessionTest.kt) | 4 |
| [GesturesTest](app/src/test/java/tech/granet/grove/GesturesTest.kt) | 7 |
| [GridPolicyTest](app/src/test/java/tech/granet/grove/GridPolicyTest.kt) | 4 |
| [GroveErrorRegistryTest](app/src/test/java/tech/granet/grove/GroveErrorRegistryTest.kt) | 5 |
| [GroveErrorRoutingTest](app/src/test/java/tech/granet/grove/GroveErrorRoutingTest.kt) | 3 |
| [IndexRefreshRequestsTest](app/src/test/java/tech/granet/grove/IndexRefreshRequestsTest.kt) | 3 |
| [IndexStateTest](app/src/test/java/tech/granet/grove/IndexStateTest.kt) | 1 |
| [NativeEnvelopeTest](app/src/test/java/tech/granet/grove/NativeEnvelopeTest.kt) | 3 |
| [NativeFailureReporterTest](app/src/test/java/tech/granet/grove/NativeFailureReporterTest.kt) | 2 |
| [NativeParityTest](app/src/test/java/tech/granet/grove/NativeParityTest.kt) | 5 |
| [NativeResultsTest](app/src/test/java/tech/granet/grove/NativeResultsTest.kt) | 3 |
| [OnboardingPolicyTest](app/src/test/java/tech/granet/grove/OnboardingPolicyTest.kt) | 5 |
| [PinnedAppsTest](app/src/test/java/tech/granet/grove/PinnedAppsTest.kt) | 6 |
| [PresentationPolicyTest](app/src/test/java/tech/granet/grove/PresentationPolicyTest.kt) | 5 |
| [ReportPromptPolicyTest](app/src/test/java/tech/granet/grove/ReportPromptPolicyTest.kt) | 4 |
| [SearchCalculatorTest](app/src/test/java/tech/granet/grove/SearchCalculatorTest.kt) | 8 |
| [SearchFeaturesTest](app/src/test/java/tech/granet/grove/SearchFeaturesTest.kt) | 7 |
| [SearchFrameGateTest](app/src/test/java/tech/granet/grove/SearchFrameGateTest.kt) | 3 |
| [SearchPublicationGateTest](app/src/test/java/tech/granet/grove/SearchPublicationGateTest.kt) | 6 |
| [SearchResultsTest](app/src/test/java/tech/granet/grove/SearchResultsTest.kt) | 4 |
| [SearchSourceStateTest](app/src/test/java/tech/granet/grove/SearchSourceStateTest.kt) | 4 |
| [SearchTest](app/src/test/java/tech/granet/grove/SearchTest.kt) | 8 |
| [SearchTutorialSessionTest](app/src/test/java/tech/granet/grove/SearchTutorialSessionTest.kt) | 7 |
| [SearchTutorialStateTest](app/src/test/java/tech/granet/grove/SearchTutorialStateTest.kt) | 6 |
| [SettingsDiscoveryTest](app/src/test/java/tech/granet/grove/SettingsDiscoveryTest.kt) | 9 |
| [SettingsRepositoryTest](app/src/test/java/tech/granet/grove/SettingsRepositoryTest.kt) | 5 |
| [SettingsScrollStateTest](app/src/test/java/tech/granet/grove/SettingsScrollStateTest.kt) | 2 |
| [SetupMergePolicyTest](app/src/test/java/tech/granet/grove/SetupMergePolicyTest.kt) | 2 |
| [StartupCoordinatorTest](app/src/test/java/tech/granet/grove/StartupCoordinatorTest.kt) | 1 |
| [TutorialReplayPolicyTest](app/src/test/java/tech/granet/grove/TutorialReplayPolicyTest.kt) | 6 |
| [UninstallBatchTest](app/src/test/java/tech/granet/grove/UninstallBatchTest.kt) | 3 |
| [WallpaperControllerTest](app/src/test/java/tech/granet/grove/WallpaperControllerTest.kt) | 3 |
| [WallpaperRegistryTest](app/src/test/java/tech/granet/grove/WallpaperRegistryTest.kt) | 5 |

## Tracked acceptance

#118 current ownership documentation; #119 responsibility splits; #120 portable Rust migration; #121 failure-contract evidence; #123 configuration/native authority; #124 cancellation confirmation; #125 executor lifetime; #126 release versioning; #127 security; #128 measured performance; #129 imports/deprecations/dead structures. #131 public Settings entry, #132 exact stream bounds, #133 secure file handoff and #134 report retention/handoff track individual security findings. #122 tracks overall completion. PR #130 links implementation and current evidence. Issues remain open until their acceptance is actually met; hardware/advisory/release limitations must be recorded explicitly.
