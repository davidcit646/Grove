# Grove build verification

The 0.1.26 alpha source has not been built or installed on a device in this checkout. The GitHub Actions workflow builds and tests the source on pushes to `main`.

## Earlier 0.1.24 verification

This revision adds contact search through Android's aggregate Contacts Provider, with a contact action menu for available numbers, compatible messaging apps, and the device's contact card. Contact data is read only after permission is granted, refreshed on provider changes and app changes, and rechecked when returning to Grove. The app drawer lists applications promptly, then reveals more while icons load in batches.

- Android debug APK build succeeded with Gradle 8.11.1, SDK 35, NDK 27.2.12479018, and Rust cross compilation for arm64, ARMv7, and x86-64.
- 38 Android unit tests and 2 Rust unit tests passed.
- Android lint completed with 0 errors and 32 warnings: 23 Kotlin extension suggestions, 6 text localization suggestions, 2 accessibility suggestions, and 1 all files storage policy warning.
- Contact actions use Android intents and the contact's own provider data. WhatsApp messaging is offered when Android can format a number internationally and its app is installed; third-party contact actions depend on the app publishing a contact data row. No contact records are saved to disk by Grove.
- The APK has not been installed or profiled on a device for this revision. It uses a development signing key.

Historical verification records in `verification/` describe earlier releases only.
