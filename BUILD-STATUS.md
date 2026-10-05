# Grove build verification

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

[GROVE-STATUS.md](GROVE-STATUS.md) is a target specification. Its [implementation matrix](GROVE-STATUS.md#implementation-status-on-main) lists proposed search/index, wallpaper, theme, and error-workflow behavior; adding this document does not verify or ship those features.
