# Grove architecture

Status: stacked draft refactor through PR #58. These branches have not been merged or verified on Android hardware. [FAILURE-POLICY.md](FAILURE-POLICY.md) defines the decision contract, [SMALL-FILE-FAILURE-REVIEW.md](SMALL-FILE-FAILURE-REVIEW.md) covers the smaller files, and [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) distinguishes passing source checks from device work.

`MainActivity` still owns Activity lifecycle, ActivityResult launchers, Home/search/drawer navigation and Android view dispatch. It remains large; #6 is not closed. It delegates work and decisions to these owners:

| Owner | Boundary and timing | Failure outcome / retry | Verification |
| --- | --- | --- | --- |
| `StartupCoordinator`, `AppCatalog`, `AppIconStore` | Cold/resume plan, launcher enumeration and icon decode after a responsive shell | Core app catalog failure shows recovery Home; missing icon keeps label/placeholder; recheck on package/resume | #20-#22, StartupCoordinatorTest, #34 |
| `SearchSourceState`, `ContactIndex`, `FileIndex` | Enabled source and permission at feature/resume; provider on worker; file canonical path again at action | Denied/unavailable/partial are distinct; Retry and stale generation cleanup | #5, #9, #23, SearchSourceStateTest, FileIndexTest, #34 |
| `ContactActions`, `FileActions`, `SearchActions` | Current permission/package/path before external action | A failed contact/file/intent action reports local error; Home remains usable | #6, #32, #34 |
| `GestureSession`, `DrawerState`, `UninstallBatch` | Touch/selection state before animation or persisted drawer edit; OS uninstall result before next launch | Disabled/invalid/canceled means no action or commit; cancellation clears queue | #14, #24, #25 and respective unit tests, #34 |
| `WidgetRegistry`, `WidgetScreen` | Allocation and persisted ID transition; provider again at render | Missing provider/createView yields removable placeholder; interrupted setup offers retry/removal | #26, #34 |
| `WallpaperController`, `WallpaperPicker`, `ThemeColors` | Preview/download/worker preparation; system apply before Home preference commit | Preview Retry, failed apply preserves selection; cosmetic color fallback | #4, #29, #31, #34/#35 |
| `ConfigDocuments`, `ConfigStore`, `FirstRunState` | Bounded read/schema check, committed activation; provisional onboarding answers | Invalid import leaves active config; damaged custom config preserved; denied optional source disabled | #2, #27, #30 and unit tests, #34 |
| `CoreBridge`, Rust `search/mime/config/wallpaper/bridge` modules | Native load and per-call payload validation | Bounded diagnostic and equivalent Kotlin fallback where safe | #12, #15, #33, NativeResultsTest, Rust tests, #34 |
| `CrashReporter` | Private report retention and user-directed chooser | Handoff leaves report for retry; explicit delete | #28, #34 |
| Gradle, Rust build script, release verifier/workflow | Compile/import/resource/package checks at build; signature/ABI/checksum/tag at release | Required failure stops job and publication; no runtime import sweep | #3, #10, #36, #34 |

The JSON schema and preference compatibility remain versions 1–7. A damaged custom configuration is kept separately from the safe fallback until explicit replacement. Search and files remain in memory; optional permissions do not gate the core Home shell.

Gradle `preBuild` calls `scripts/build-rust-android.sh` with NDK 27.3.13750724, producing arm64, ARMv7 and x86-64 `.so` files. PR CI runs Rust tests, Android debug build, JVM tests, lint and the missing-signing negative check. A main build requires signing and verifies APK/AAB certificate, native payloads/alignment and checksums. The manual release workflow rebuilds from a supplied tag and exact commit; nothing is published by the refactor itself. Positive signing and device install/update are pending #34/#36.

Run the dated device and performance gates in [TESTING.md](TESTING.md) and [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) before calling #17 or #6 complete.
