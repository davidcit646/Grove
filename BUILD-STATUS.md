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
