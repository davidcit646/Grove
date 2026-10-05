# Grove architecture

Status: the capability refactor is merged on main. This follow-up splits setup presentation and optional search/widget orchestration. [FAILURE-POLICY.md](FAILURE-POLICY.md) defines the decision contract; [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) separates source checks from Android device work.

`MainActivity` owns Activity lifecycle, ActivityResult launchers, Home/search/drawer navigation, and the root Android view. `SearchSources` owns optional contact/file indexes and cancellation; `WidgetFlow` owns the widget setup sequence while the Activity retains result launchers. `FirstRunSetup` owns the full-screen shell and navigation; `FirstRunPages` renders page content with `FirstRunComponents` visual primitives and `FirstRunState` provisional answers. The Activity is about 1,145 lines after this extraction; remaining drawer and navigation coordination is tracked by #6. It delegates work and decisions to these owners:

| Owner | Boundary and timing | Failure outcome / retry | Verification |
| --- | --- | --- | --- |
| `StartupCoordinator`, `AppCatalog`, `AppIconStore` | Cold/resume plan, launcher enumeration and icon decode after a responsive shell | Core app catalog failure shows recovery Home; missing icon keeps label/placeholder; recheck on package/resume | #20-#22, StartupCoordinatorTest, #34 |
| `SearchSources`, `SearchSourceState`, `ContactIndex`, `FileIndex` | Enabled source and permission at feature/resume; provider on worker; file canonical path again at action | Denied/unavailable/partial are distinct; Retry and stale generation cleanup | #5, #9, #23, SearchSourceStateTest, FileIndexTest, #34 |
| `ContactActions`, `FileActions`, `SearchActions` | Current permission/package/path before external action | A failed contact/file/intent action reports local error; Home remains usable | #6, #32, #34 |
| `GestureSession`, `HomeTouchRouter`, `DrawerState`, `DrawerTiles`, `DrawerDragController`, `FolderActions`, `PinDragController`, `UninstallBatch` | Touch/selection state before animation or persisted drawer edit; OS uninstall result before next launch | Disabled/invalid/canceled means no action or commit; cancellation clears queue | #14, #24, #25 and respective unit tests, #34 |
| `WidgetFlow`, `WidgetRegistry`, `WidgetScreen` | Allocation and persisted ID transition; provider again at render | Missing provider/createView yields removable placeholder; interrupted setup offers retry/removal | #26, #34 |
| `WallpaperController`, `WallpaperPicker`, `ThemeColors` | Preview/download/worker preparation; system apply before Home preference commit | Preview Retry, failed apply preserves selection; cosmetic color fallback | #4, #29, #31, #34/#35 |
| `ConfigDocuments`, `ConfigStore`, `FirstRunSetup`, `FirstRunPages`, `FirstRunComponents`, `FirstRunState`, `MainActivity.commitConfig` | Bounded read/schema check, committed activation; provisional onboarding answers; startup settings check after minimal UI | Invalid import leaves active config; damaged custom config preserved; write failure keeps prior in-memory layout; unreadable startup settings show Retry and Android Home settings; denied optional source disabled | #2, #27, #30 and unit tests, #34 |
| `CoreBridge`, Rust `search/mime/config/wallpaper/bridge` modules | Native load and per-call payload validation | Bounded diagnostic and equivalent Kotlin fallback where safe | #12, #15, #33, NativeResultsTest, Rust tests, #34 |
| `CrashReporter` | Private report retention and user-directed chooser | Handoff leaves report for retry; explicit delete | #28, #34 |
| Gradle, Rust build script, release verifier/workflow | Compile/import/resource/package checks at build; signature/ABI/checksum/tag at release | Required failure stops job and publication; no runtime import sweep | #3, #10, #36, #34 |

The JSON schema and preference compatibility remain versions 1–7. A damaged custom configuration is kept separately from the safe fallback until explicit replacement. Search and files remain in memory; optional permissions do not gate the core Home shell.

Gradle `preBuild` calls `scripts/build-rust-android.sh` with NDK 27.3.13750724, producing arm64, ARMv7 and x86-64 `.so` files. PR CI runs Rust tests, Android debug build, JVM tests, lint and the missing-signing negative check. A main build requires signing and verifies APK/AAB certificate, native payloads/alignment and checksums. The manual release workflow rebuilds from a supplied tag and exact commit; nothing is published by the refactor itself. Positive signing and device install/update are pending #34/#36. This split passed source CI on its branch; new Android widget, permission, and onboarding interactions have not been device-tested for this change.

Run the dated device and performance gates in [TESTING.md](TESTING.md) and [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) before calling #17 or #6 complete.

## MainActivity lane split (#6)

`MainActivity` is the Android host: lifecycle, result registrations, root views,
platform services, and routing. Lane controllers are activity-scoped and own their
mutable feature state; they use the host for Android effects and explicit calls to
other controllers. This is a single HOME Activity, so switching between Home,
drawer, and search retains the existing task/lifecycle behavior.

| Owner | Lane/state | Failure boundary |
| --- | --- | --- |
| StartupController | Config loading, launcher callback, recovery | Required config/service/catalog failure closes normal Home to Retry/system settings; optional indexing starts afterward. |
| CatalogController | App snapshot, prepared app search, icon publication, generation | Failed enumeration closes catalog to recovery; icons keep placeholders; stale generations cannot publish. |
| HomeController | Home rendering, scroll, animation, wallpaper backdrop | Optional backdrop failure keeps gradient; superseded/destroyed bitmap output is recycled. |
| DrawerController | Grid, filtering, selection, folders/drag | Folder/pin mutation passes through successful config commit before selection is cleared. |
| SearchController | Search worker, query generation, source snapshots/permissions | Optional source state is isolated; stale query output cannot publish. |
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
search/catalog/wallpaper work. #6 remains open until device behavior is verified.
