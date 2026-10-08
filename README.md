# Grove Launcher

An Android 12+ home launcher by GraNet IT Solutions. Version 1.0.1, version code 33 (cleanup candidate).

## Features

- Home screen with clock, pinned apps, widgets, gestures and customizable grids.
- App drawer with folders, search and Android uninstall actions.
- Permission-controlled app, contact and file search with independent indexing controls.
- Local wallpapers, theme choices, guided setup and recoverable configuration import/export.
- Adaptive color and monochrome themed launcher icons.

There is no account or telemetry. Built-in wallpapers work offline. Google and Play Store searches open external apps or web pages.

## Build

Install JDK 17, Android SDK 36, NDK 27.3.13750724, and Rust with Android arm64, ARMv7 and x86-64 targets. Gradle builds the Rust core before packaging.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Choose Grove in Android's default Home app selector. Keep another launcher installed so you can switch back. Debug and signed release artifacts are distinct; device acceptance and Play readiness are separate from a passing source build.

## Documentation

- [Code documentation](code_documentation.md): architecture, system catalogue, behavior contract, failure policies, test evidence and release instructions.
- [Build verification](code_documentation.md#build-status) and [device acceptance](code_documentation.md#testing).
- [Signing](code_documentation.md#signing-readme) and [Play readiness](code_documentation.md#play-readiness).
- [Privacy policy](PRIVACY.md). Broad file-search access requires Play policy review before distribution.

## License

Apache-2.0 for original source and artwork. Third-party dependencies and Commons wallpapers retain their respective licenses; see [NOTICE](NOTICE).
