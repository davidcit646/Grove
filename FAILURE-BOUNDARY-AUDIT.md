# Grove failure-boundary audit

Baseline: `main` at `181c4ab2302614e88bbee23221b1ba2424148b9f` (2026-10-03). This is a source audit, not a device verification. PR #1 and PR #16 are open against this baseline and are not treated as merged behavior. The target policy is in [FAILURE-POLICY.md](FAILURE-POLICY.md). Parent tracking issue: #17; audit issue: #18.

## How to read this inventory

`Closed` means the affected action or capability stops when a required condition is absent; it does not automatically mean killing the default Home process. `Open` means a named fallback keeps a capability usable. `Partial` must be distinguishable from complete results. A `?` means behavior needs device verification. Each row identifies the current boundary, the required target, and its implementation/verification owner. Rows marked `retain` are reviewed, but still need regression coverage when callers change.

## Production Kotlin: all 23 files

| File | Current boundary and behavior at baseline | Target policy / owner |
| --- | --- | --- |
| `GroveApp.kt` | `Application.onCreate` installs the crash handler. No central startup preflight. | Keep startup minimal; coordinate later capability checks in #20 and test handler installation in #32. |
| `MainActivity.kt` | Owns lifecycle, app discovery, icons, contacts, files, search, gestures, drawer, folders, uninstall, widgets, wallpaper, config I/O, and navigation. `showHome` precedes asynchronous app/contact/file loads; errors have varied local handling. | Thin lifecycle/result coordinator and typed capability state (#6, #20-#27, #29). Core discovery failure shows recoverable Home (#21); no swallowed cancellation (#14). |
| `Config.kt` | Validates schema versions 1-7, types, wallpaper range, favorites, and folder bounds during parse. Legacy search defaults are intentional for pre-v7 imports. | Retain validation before activation. Explicitly test old schema and invalid values (#27, #32). |
| `ConfigStore.kt` | Bounds JSON size/depth; damaged saved config is preserved while a default fallback runs. `save` uses `SharedPreferences.apply()`, which does not confirm durable write. | Retain recoverable fallback; distinguish parsing from persistence/commit failure and test restoration (#27, #32). |
| `FirstRunSetup.kt` | UI, page/gesture state, pins, and permission callbacks share one class. Disabled gestures alter pages. | Extract state/renderer (#2); map permission and skip outcomes to the contract (#30). |
| `AppIconStore.kt` | Process-global mutable bitmap map; size change clears it; direct callers can mutate it. | Cache owner/lifecycle (#8, #22); individual icon failure degrades to placeholder, not app-list loss. |
| `ContactIndex.kt` | Aggregate provider query fails if cursor is null; contact details can return empty on null cursor. MainActivity catches load failure but timestamps before success and details fallback becomes an empty menu. | Provider failure is distinct from genuinely empty contacts; retry after recovery (#5, #23), recheck permission/action state (#32). |
| `FileIndex.kt` | Bounded, symlink-aware traversal; directory-stream errors and canonicalization errors can be skipped silently, yielding apparently complete results. | Explicit partial/skipped count and root failure (#9, #23); cancellation remains distinct; enforce access again on open. |
| `CoreBridge.kt` | Native load and JNI calls use `runCatching` and Kotlin fallback without logging. Config preflight failure can appear as no native problem. | Per-operation native state, bounded diagnostic, parity and invalid result handling (#12, #33); fallback only when equivalent. |
| `CrashReporter.kt` | Saves private reports and asks before email; `sendReports` deletes reports after launching chooser, before send is known. Several reporter errors are suppressed to protect crash handling. | Crash handler must not recurse; keep reports on canceled handoff, explicit delete, bound retention and privacy (#28, #32). |
| `Gestures.kt` | Pure bounded classifiers return `NONE` for unsupported movement. | Retain closed gesture decision; move event lifecycle state out of Activity (#24), test cancel/multitouch. |
| `PinnedApps.kt` | Pure move helpers return unchanged list on missing item/target. | Retain no-op for invalid reorder; tests and caller commit rules (#25). |
| `HomeScreen.kt` | Renders controls/widgets/pins through callbacks; cannot represent capability readiness itself. | Render core recovery/degraded UI from state supplied by owner (#21, #31). |
| `LauncherSettingsScreen.kt` | Builds toggles; enabling contacts/files delegates to Activity. Crash-report settings read/write preferences. | Display actual enabled/permission/failed state, not implied success (#23, #31); persistence behavior in #27. |
| `Search.kt` | Normalizes/bounds query and scores; no external dependency. | Retain deterministic fallback and parity with Rust (#7, #33). |
| `SearchResults.kt` | Normalizes all labels and passes fresh arrays to native bridge for each query. | Preserve ordering, measure allocations and latency (#7, #35); expose source readiness through #23. |
| `SearchScreen.kt` | Renders results from booleans (`enabled`, `access`, `indexing`); cannot show partial/failure distinctly. | Render typed denied/loading/partial/unavailable states (#23, #31). |
| `ThemeColors.kt` | Falls back to a fixed color when a theme attribute is missing; `WallpaperColors.fromBitmap` has no local error branch. | Cosmetic failure uses explicit safe theme color and no Home loss (#31); test wallpaper color failure (#32). |
| `WallpaperArt.kt` | Native rendering falls back to Canvas; selected cached wallpaper can fall back to a built-in image in controller. | Distinguish chosen asset from fallback, avoid pretending selected asset loaded (#29, #33). |
| `WallpaperController.kt` | Network host/HTTPS/size/dimension checks, bounded redirects and preview/apply errors. `artwork` decodes synchronously when called by Home; preview failures return null. | Preserve security checks; move Home preparation off UI thread and define bitmap ownership (#4); typed preview/apply results (#29). |
| `WallpaperPicker.kt` | Uses `ready` and `null` preview with retry; disables apply until preview ready. | Preserve gating, show actionable cause where possible and commit only after apply success (#29, #31). |
| `WidgetScreen.kt` | Missing provider or failed `createView` yields placeholder with remove action; size update errors logged. | Preserve Home and widget ID ownership; explicit retry/remove and no leaked IDs (#26, #32). |
| `ui/UiKit.kt` | Shared view/dialog helpers; callbacks assume caller validates state. | Keep presentation-only; no hidden policy decisions or newly swallowed failures (#31). |

## Native, Android integration, build, and release

| File(s) | Current boundary and behavior | Target / owner |
| --- | --- | --- |
| `rust/grove-core/src/lib.rs` | One file contains search, MIME, config preflight, wallpaper, JNI, tests. Returns JNI defaults/errors for some invalid inputs. | Split by responsibility without changing exported symbols (#15); audit native result shape and Kotlin parity (#33). |
| `rust/grove-core/Cargo.toml`, `Cargo.lock`, `.cargo/config.toml` | Rust dependency and target configuration; verified by `cargo test` in CI. | Build resolution failure is closed; pin and test target/ABI behavior (#33, #36). |
| `app/src/main/AndroidManifest.xml` | Declares HOME, permissions, provider and app component. Broad file permission is optional at runtime but is a Play-policy concern. | Install/package checks closed at build; permission checks at feature/use boundary (#23, #34, #36). |
| `app/src/main/res/**` | XML drawables, styles, provider paths and extraction rules are packaged resources. Individual missing resource may fail inflation before an app-level result is possible. | Resource linking/lint and install smoke tests are closed build gates (#32, #34, #36). |
| `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`, Gradle wrapper | Declares SDK/NDK, signing, dependencies, Rust preBuild. Compile/import or dependency failure should fail the build. | Verify artifacts/ABI/signature on intended commit (#3, #36); never rely on runtime reflection to check compile-time imports. |
| `scripts/build-rust-android.sh` | `set -euo pipefail`, toolchain presence check, three Rust targets and copy of `.so` files. | Keep early toolchain failure; verify all expected ABI files and linkage in packaged output (#33, #36). |
| `.github/workflows/android.yml` | Debug build, tests, lint; missing main signing password prints a warning and exits 0, leaving green CI with no signed APK. | Required main release artifact must fail closed (#3, #36). PR #1 and #16 overlap here. |
| `.github/workflows/publish-0.1.26-alpha.yml` through `publish-0.1.29-alpha.yml` | Version-specific fixed historical run IDs and tags; can publish artifact from another commit. | One verified same-commit release flow (#10, #36). |
| `signing/grove-release.p12`, `signing/README.md` | Password-protected long-lived key is tracked; password is separately held. | Verify fingerprint/artifact and update path; never log or commit password (#36). |
| `Grove-Launcher-0.1.26-source.zip` | Stale tracked snapshot can be mistaken for current source. | Remove or generate from exact release commit (#13). |

## Test inventory and gaps

| Existing test | Current scope | Additional failure proof |
| --- | --- | --- |
| `ConfigTest.kt` | Schema migration, invalid values and round trips. | Broken persistence, fallback/recovery, failed import state (#27, #32). |
| `ContactIndexTest.kt` | Number normalization and messaging-channel decisions. | Provider unavailable then recovered, permission changes (#5, #23, #32). |
| `GesturesTest.kt` | Pure classifier behavior. | State machine cancellation, widget touch, multitouch/device gesture paths (#24, #34). |
| `PinnedAppsTest.kt` | Pure ordering operations. | Drawer state commit and interruption (#25, #32). |
| `SearchResultsTest.kt`, `SearchTest.kt` | Ranking and normalized scoring. | Native/Kotlin parity, 15,000-file latency/allocation (#7, #33, #35). |
| `WallpaperControllerTest.kt` | Allowed wallpaper destination rules. | Preview/apply failure and bitmap ownership on device (#4, #29, #34). |
| Rust tests in `lib.rs` | Search, MIME, config and wallpaper algorithms. | JNI boundary, ABI packaging and Kotlin parity (#15, #33). |

The tree contains no `app/src/androidTest` suite at this baseline. `TESTING.md` is a manual acceptance list, not evidence that its recent flows have run on hardware. Add focused instrumented/device checks for lifecycle, default Home, permissions, widgets, and signed updates (#32, #34, #36). Historical XML records under `verification/` are not current-release evidence. Baseline main CI has a successful debug/test/lint run at this commit; its signed release step can succeed without a signed artifact. No local Android SDK, Rust toolchain, or device was available in this audit workspace.

## Priority and closure

1. Resolve the competing release PR changes (#3, #36) and capture a stable baseline.
2. Define capability contract (#19), then extract core and feature owners (#20-#31, #33) while applying the contract across smaller files.
3. Prove fail-open/closed outcomes by injection, device runs, performance measurement, and artifact verification (#32, #34-#36).

This document closes the *inventory* scope of #18 when reviewed. It does not claim that its target policies are implemented or that on-device scenarios pass.
