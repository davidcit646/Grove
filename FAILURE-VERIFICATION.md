# Failure-policy verification ledger

Refactor branch chain through PR #58, 2026-10-04. This records executable source checks and the remaining Android-only acceptance work for #32. A passing JVM/CI test does not mean the default Home app, provider, signed APK, or process death was exercised on a device.

| Capability | Fail decision and visible outcome | Deterministic proof | Android-only scenario still required |
| --- | --- | --- | --- |
| Launcher enumeration | Core Home unavailable with Retry and Settings when callback registration/enumeration fails; icon decode failure keeps app tile/placeholder. | StartupCoordinatorTest checks cold/resume/revocation planning. | #34 inject LauncherApps failure and all icon decodes failing; verify default Home does not crash loop. |
| Contacts | Denied -> permission action; provider failure -> Retry; empty successful list -> Ready(0); failed refresh remains retryable. | SearchSourceStateTest, StartupCoordinatorTest, ContactIndexTest. | #34 deny/revoke/grant, null/throwing provider, observer and stale generation, details unavailable. |
| Shared files | Denied -> permission action; unreadable root -> failure; child failure -> Partial with skipped count; cancel -> no published stale index. | FileIndexTest injects directory opener failure/cancel; SearchSourceStateTest. | #34 shared-storage revocation, provider open failure, canonical path/symlink and external file intent. |
| Widgets | Missing provider/createView -> removable placeholder; failed allocation/bind/configure retains or releases pending ID and offers retry/removal. | Registry flow is Android-dependent; no pure fake currently. | #34 configured/simple widget, bind/configure cancellation, process death, orphan cleanup, missing provider, createView throw and host listening failure. |
| Wallpaper | Preview failure -> Retry; apply failure -> no preference commit; Home uses temporary background while off-thread art prepares. | WallpaperControllerTest covers redirect trust. | #34 failed download/decode/apply, rotation, bitmap lifecycle and cosmetic fallback. #35 checks Home frame time. |
| Native | Missing load/JNI/malformed result -> once-per-class diagnostic and Kotlin search/MIME/Canvas/config fallback. | NativeFailureReporterTest, NativeResultsTest, SearchResultsTest, SearchTest, Rust tests. | #33/#34 packaged ABI symbol/load smoke and Kotlin/Rust parity on device. |
| Config and documents | Reject size/schema/UTF-8/partial read before activation; damaged custom config retained; activate after committed storage. | ConfigTest, ConfigDocumentsTest. | #34 persistence failure, restart and fallback recovery with real preferences/provider. |
| Drawer/gestures/uninstall | Invalid folder/pin move no commit; pointer cancel clears capture; uninstall cancel stops queue. | DrawerStateTest, PinnedAppsTest, GestureSessionTest, GesturesTest, UninstallBatchTest. | #34 nested widget scrolling, folder drag, rotation, canceled system uninstall and process recreation. |
| Crash reports | Opening chooser retains report; explicit deletion removes it. | Android chooser delivery state cannot be observed by unit test. | #34 cancel chooser, failed email, delete and repeated launch. |
| Release | Missing signing secret/artifact fails; APK/AAB certificate, three ABIs, alignment, checksums and commit verified before manual publication. | PR workflow negative-secret gate; `scripts/verify-release.sh` on signed main/release run. | #36 signed build with configured secret, clean install/update on device and tag/artifact comparison. |

## Gates

1. Every draft PR in the stack must pass Android debug build, JVM tests, lint and Rust tests. A failure blocks merging descendants until fixed.
2. Run #34 on Android 12 and current Android with build SHA, device/API, date, reproduction and outcome recorded in `TESTING.md`; file defects as issues. No such device run has occurred in this environment.
3. Run #35 cold/warm and 15,000-file benchmarks on the same device/build method before calling the performance work complete.
4. Run the signed main and manual tag workflows only after the signing secret is configured; compare uploaded checksums and commit. No release is published by this refactor.

These Android-only rows are outstanding work, not passing claims. They are the remaining closure criteria for #32, #34, #35 and #36.
