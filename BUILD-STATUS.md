# Grove build verification

## Juniper source verification (2026-10-06)

Production/test source `994df71d741b67dd65ed690840bd67b48cef1cdb` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37404896738): eight Rust tests, 163 JVM tests, debug APK assembly, Android lint and the missing-signing negative gate. Signed release steps were skipped. PR #92 is stacked on unmerged PR #90; Juniper device acceptance remains pending. GROVE-STATUS.md is unchanged.

The debug APK contains Juniper and the pending onboarding source. This run verifies compilation, pure ownership/conflict/grid contracts and lint; it does not verify OEM interactions, screen accessibility or process recreation on a device.

## PR #87 source verification (2026-10-05)

Review branch `codex/search-theme-audit-31-77-85`, production/test source `531d7870222d194018551e4a54cd63b8454ef8b4`, passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37356982336): Rust tests, debug APK assembly, JVM tests, lint and the missing-signing-secret negative gate. Signed release steps were skipped in this PR run. This is source/build verification; new Android visual, permission, provider and lifecycle cases below have not been run by the assistant. `GROVE-STATUS.md` remains unchanged.

PR #83 production/test source at `13aa0c10ac635e216fe555484bc2807d3a98bb2a` passed Android CI run #567 on 2026-10-05: Rust tests, Android debug assembly, JVM unit tests, Android lint, and the missing-signing-secret negative gate all succeeded. The positive signed-release step remained skipped because `GROVE_SIGNING_PASSWORD` is not available in that PR context. This verifies source/build behavior only; Android device acceptance remains in [TESTING.md](TESTING.md). `GROVE-STATUS.md` was not modified by this implementation.

The 0.1.30 alpha source at baseline commit `181c4ab` passed Android debug build, unit tests, lint and Rust tests. That run did not produce a signed release artifact because the signing secret was unavailable. The integrated source refactor and [PR #73](https://github.com/davidcit646/Grove/pull/73) MainActivity lane split are on main. PR #73 passed Rust tests, debug APK assembly, JVM tests, Android lint, and the missing-signing-secret negative check at `fe4ac0e`. That is source verification, not device validation. Main's release workflow still fails closed without `GROVE_SIGNING_PASSWORD`; no full Android device matrix has been run for this change. Some integrated Grove Test conditions were accepted by the user when #17 was closed, but the broader dated evidence in [TESTING.md](TESTING.md) remains pending.

The source targets API 36. See [PLAY-READINESS.md](PLAY-READINESS.md) for the current distribution checklist. The optional broad file-search permission still requires a Play policy decision and declaration before a Play release; see [PLAY-READINESS.md](PLAY-READINESS.md).

## Earlier 0.1.24 verification

This revision adds contact search through Android's aggregate Contacts Provider, with a contact action menu for available numbers, compatible messaging apps, and the device's contact card. Contact data is read only after permission is granted, refreshed on provider changes and app changes, and rechecked when returning to Grove. The app drawer lists applications promptly, then reveals more while icons load in batches.

- Android debug APK build succeeded with Gradle 8.11.1, SDK 35, NDK 27.2.12479018, and Rust cross compilation for arm64, ARMv7, and x86-64.
- 38 Android unit tests and 2 Rust unit tests passed.
- Android lint completed with 0 errors and 32 warnings: 23 Kotlin extension suggestions, 6 text localization suggestions, 2 accessibility suggestions, and 1 all files storage policy warning.
- Contact actions use Android intents and the contact's own provider data. WhatsApp messaging is offered when Android can format a number internationally and its app is installed; third-party contact actions depend on the app publishing a contact data row. No contact records are saved to disk by Grove.
- The APK has not been installed or profiled on a device for this revision. It uses a development signing key.

Historical verification records in `verification/` describe earlier releases only.

[GROVE-STATUS.md](GROVE-STATUS.md) remains the immutable target specification for this work. PR #83 implements the linked search/indexing, configuration workflow, tutorial replay, wallpaper-library and error-reporting behavior in source, while external wallpaper/theme reconciliation and Android device evidence remain separate work.


## PR #90 onboarding implementation — October 5, 2026

Production/test source `026be7f0314c355f0449fe23796a557ee0417e22` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37399253894): seven Rust tests, debug APK assembly, JVM tests, lint, and the missing-signing negative gate. This is source/build verification; PR #90 remains unmerged and its Android device acceptance is pending. GROVE-STATUS.md is unchanged. Issue #89 explicitly requests fresh-install indexing defaults and Settings-only indexing controls; this overrides the older onboarding indexing-choice description without editing that contract.

## Juniper cleanup after initial device check

Cleanup source `a8f1ecdc5be3cd51b685425f6f4c896bd91d67bc` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37407249555): eight Rust tests, JVM tests, debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. The user confirmed the original Juniper build loads and core workflows work, and supplied a recording showing repeated contact-index status transitions. The cleanup still requires device revalidation; no merge occurred.

Settings categories and destinations now use matching Material Symbols Rounded icons. Search groups contact/file controls into equivalent Material cards. Cache availability and WorkManager activity are separate labels (Idle/Queued/Running/Waiting to retry/Failed); cache presence does not claim complete coverage. IndexWork serializes scheduling, coalesces queued notifications, retains at most one deferred follow-up for running work, and rate-limits sequential contact-provider events to a 30-second window. Manual refresh remains immediate when no work is active. A terminal failed scan remains failed rather than appending a dependent job that could be canceled. Permission, settings and generation gates remain authoritative.

## Contact-path fixes #93–#102 — October 6, 2026

Production/test source `6cafc09247cbc3f96eb1a21a1ebc4bfb025bd130` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37409473807): eight Rust tests, the JVM suite (173 test methods), debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. Source trees locally and on the pushed branch match. PR #92 remains draft, stacked on unmerged PR #90; the new device acceptance is pending. GROVE-STATUS.md is unchanged.

The current owner/caller/consumer and failure inventory is [CONTACT-REVIEW.md](CONTACT-REVIEW.md). Package changes no longer request contact indexing; ContactChanges owns process observation, IndexWork owns scheduling/recovery/repair, IndexCache owns confirmed cache publication and shared metadata, and the two UIs render those outcomes. The previous cleanup pass did not prove idle stability; this source fixes the subsequent full-audit findings.


## Search feature controls and tutorials (#104–#107) — October 6, 2026

Production/test source `193c30fb14775f9f885eec9e1a8548cd50345d7c` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37472889992): nine Rust tests, the JVM suite (205 test methods), debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. PR #92 remains draft, stacked on unmerged PR #90. This is source/build verification; the new animation/layout and device workflows remain pending. GROVE-STATUS.md is unchanged.

Search settings now offers independent Calculator, Grove settings search and Android settings search switches, all defaulting on. Schema v12 and both Kotlin/Rust validation preserve older preferences. Provider-only changes do not reconcile contact/file index work. Initial permission pages are vertically centered; swipe practice has an idle guide and a non-intercepting success overlay lasting 75ms entrance + 100ms hold + 75ms fade at the default system animation scale.

The first actual Search entry shows a separate full-screen Material You guide with centered two-card pages: Calculator/Apps, Contacts/Files, Grove/Android settings. Three forward actions enter Search; Back/Close before Finish never records completion. Completion and explicit Help replay use independent app-private markers. No demonstration reads protected providers, changes settings, asks for access or schedules indexing. Keyboard focus is deferred until completion. Failed completion or optional presentation leaves Search available; failed completion is suppressed for the current session and is not falsely saved.

## Search tutorial operating-agreement fixes — October 6, 2026

Production/test source `60f8f7fe1beeb0ad08219cc1e82ccfbcbed68f8d` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37476750855): nine Rust tests, JVM suite (213 source test methods), debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. Home swipe cleanup precedes tutorial presentation; failed replay requests retain session suppression; tutorial failures use the shared severity/code/report workflow. The user accepted the preceding APK in their tested workflows; the new dismissal and failure-path device cases remain in TESTING.md. No merge occurred; GROVE-STATUS.md is unchanged.

Debug APK: `Grove-Juniper-audit-60f8f7f.apk`, SHA-256 `ea51e905f61909741ef52bff096d29c2fdb6ef6c015e71e049ee5f10571090e5`. Verified artifact archive digest, APK ZIP integrity, manifest and three Rust ABIs.

## Grove 1.0.0 preparation — October 6, 2026

Source `f1bce9b98f92d1b5322121a0764e041de592b254` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37481707804): nine Rust tests, JVM suite (213 source test methods), debug APK assembly, lint and the missing-signing negative gate. The only production change after the device-accepted audit build is versionName 1.0.0 and versionCode 32. The user confirmed that APK works, then authorized merging for 1.0. Functional device acceptance does not establish every Android/OEM/accessibility/fault-injection case in the historical matrix. PR #90 is merged; PR #92 is authorized for main. GROVE-STATUS.md is unchanged.

This run skips signed release steps; production APK/AAB delivery remains conditional on main CI's existing certificate, ABI, alignment and checksum gates. The previous main release attempt lacked GROVE_SIGNING_PASSWORD. Do not label a debug Grove Test APK as a signed production release.

Verified debug APK: `Grove-1.0.0-test.apk`, SHA-256 `a0a94fa31c08e7c4d5054c84b3e4d08b152335eb295c10d1557b1f0a9a452140`; archive digest, APK integrity, 1.0.0/test manifest and three Rust ABIs verified.

## Grove 1.0 signing-key replacement — October 6, 2026

The user explicitly authorized replacing the unavailable-password release key. Source `d8ad0c95b03c02b30edb2f971d0b2e970f2221d5` passed [CI](https://github.com/davidcit646/Grove/actions/runs/37487799875): nine Rust tests, JVM suite, debug APK assembly, lint and missing-signing negative gate. Local cryptographic checks confirmed the selected password unlocks the new private key, signing/verification works and a wrong password fails. The release script pins the new certificate in APK/AAB checks and provenance. Old-key production installs require exporting settings and reinstalling; no old-key update continuity is claimed. Signed production generation remains blocked until GROVE_SIGNING_PASSWORD is configured with the selected password. GROVE-STATUS.md is unchanged.
