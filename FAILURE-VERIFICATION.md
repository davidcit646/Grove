# Failure-policy verification ledger

**Scope note (2026-10-05):** #34 and #35 were closed unrun by user scope decision. References to them below identify historical device/performance checks, not open gates or passing results. #32, #33 and #36 still track implementation and release verification.

Integrated refactor source through 2026-10-04. This records executable source checks and remaining Android-only acceptance work for #32. [GROVE-STATUS.md](GROVE-STATUS.md) is the target behavior; its [implementation matrix](GROVE-STATUS.md#implementation-status-on-main) marks new proposals separately. A passing JVM/CI test does not mean the default Home app, provider, signed APK, or process death was exercised on a device.

| Capability | Fail decision and visible outcome | Deterministic proof | Android-only scenario still required |
| --- | --- | --- | --- |
| Launcher enumeration | Core Home unavailable with Retry and Android Home settings when startup configuration, callback registration, or enumeration fails; optional widget/setup state cannot block Home; icon decode failure keeps app tile/placeholder. | StartupCoordinatorTest checks cold/resume/revocation planning. | #34 inject LauncherApps failure and all icon decodes failing; verify default Home does not crash loop. |
| Contacts | Denied -> permission action; provider failure -> Retry; empty successful list -> Ready(0); failed refresh remains retryable. | SearchSourceStateTest, StartupCoordinatorTest, ContactIndexTest. | #34 deny/revoke/grant, null/throwing provider, observer and stale generation, details unavailable. |
| Shared files | Denied -> permission action; unreadable root -> failure; child failure -> Partial with skipped count; cancel -> no published stale index. | FileIndexTest injects directory opener failure/cancel; SearchSourceStateTest. | #34 shared-storage revocation, provider open failure, canonical path/symlink and external file intent. |
| Widgets | Missing provider/createView -> removable placeholder; failed allocation/bind/configure retains or releases pending ID and offers retry/removal. | Registry flow is Android-dependent; no pure fake currently. | #34 configured/simple widget, bind/configure cancellation, process death, orphan cleanup, missing provider, createView throw and host listening failure. |
| Wallpaper | Preview failure -> Retry; apply failure -> no preference commit; Home uses temporary background while off-thread art prepares. | WallpaperControllerTest covers redirect trust. | #34 failed download/decode/apply, rotation, bitmap lifecycle and cosmetic fallback. #35 checks Home frame time. |
| Native | Missing load/JNI/malformed result -> once-per-class diagnostic and Kotlin search/MIME/Canvas/config fallback. | NativeFailureReporterTest, NativeResultsTest, SearchResultsTest, SearchTest, Rust tests. | #33/#34 packaged ABI symbol/load smoke and Kotlin/Rust parity on device. |
| Config and documents | Reject size/schema/UTF-8/partial read before activation; damaged custom config retained; activate and publish edits only after committed storage. The #74/#75 review branch keeps Kotlin and Rust native preflight aligned on schema v8, including both indexing booleans. | ConfigTest covers v1–v8 migration/round-trip and malformed v8; Rust tests cover v8 native acceptance, indexing-field type rejection and v9 rejection; ConfigDocumentsTest covers bounded document I/O. | #84 restart with the native library loaded after wallpaper/settings persistence; real preference failure and preserved-raw recovery remain device/instrumented evidence. |
| Drawer/gestures/uninstall | Invalid folder/pin move no commit; pointer cancel clears capture; uninstall cancel stops queue. | DrawerStateTest, PinnedAppsTest, GestureSessionTest, GesturesTest, UninstallBatchTest; Android touch routing is not JVM-tested. | #34 nested widget scrolling, folder and pin drag, rotation, canceled system uninstall and process recreation. |
| Crash reports | Opening chooser retains report; explicit deletion removes it. | Android chooser delivery state cannot be observed by unit test. | #34 cancel chooser, failed email, delete and repeated launch. |
| Release | Missing signing secret/artifact fails; APK/AAB certificate, three ABIs, alignment, checksums and commit verified before manual publication. | PR workflow negative-secret gate; `scripts/verify-release.sh` on signed main/release run. | #36 signed build with configured secret, clean install/update on device and tag/artifact comparison. |

## Gates

1. Every draft PR in the stack must pass Android debug build, JVM tests, lint and Rust tests. A failure blocks merging descendants until fixed.
2. Run #34 on Android 12 and current Android with build SHA, device/API, date, reproduction and outcome recorded in `TESTING.md`; file defects as issues. No such device run has occurred in this environment.
3. Run #35 cold/warm and 15,000-file benchmarks on the same device/build method before calling the performance work complete.
4. Run the signed main and manual tag workflows only after the signing secret is configured; compare uploaded checksums and commit. No release is published by this refactor.

These Android-only rows are outstanding work, not passing claims. They remain evidence gaps; #34/#35 were closed unrun, while #32/#36 retain their own acceptance criteria.

## Proposal-only verification still to design

The current test rows above do not prove separate indexing/live GFS-GCS ([#74](https://github.com/davidcit646/Grove/issues/74)), durable GFI/GCI caches and eight states ([#75](https://github.com/davidcit646/Grove/issues/75)), user/black wallpaper ([#76](https://github.com/davidcit646/Grove/issues/76)), external Android wallpaper and theme reconciliation ([#77](https://github.com/davidcit646/Grove/issues/77)), or the GHEAEW code/severity/report-draft workflow ([#78](https://github.com/davidcit646/Grove/issues/78)). Add deterministic tests and dated device cases as those features are implemented; do not mark them passing from the existing source tests.
