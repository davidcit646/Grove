# Grove Launcher

An Android 12+ home launcher by GraNet IT Solutions. Current source version: 0.1.30 alpha. See [BUILD-STATUS.md](BUILD-STATUS.md) for verification status.

[GROVE-STATUS.md](GROVE-STATUS.md) is the target behavior and failure contract. Its [implementation status](GROVE-STATUS.md#implementation-status-on-main) distinguishes shipped code from proposals, including separate live search/indexing, durable index caches, custom/black wallpaper choices, theme modes, and error codes. The open [documentation issue](https://github.com/davidcit646/Grove/issues/79) keeps that distinction current. Source checks and Android device results are separate in [TESTING.md](TESTING.md).

## 0.1.29 changes

- Reworked first-run setup with larger type, progress, visual cards, app icons in the pin picker, and readable permission disclosures. The setup colors follow the Android light or dark theme.
- When both Home swipe gestures are disabled, the swipe-practice page is omitted. When one is enabled, practice shows only that gesture and the progress count reflects the visible pages.

## 0.1.28 changes

- Accept Wikimedia Commons' dedicated `thumb.wikimedia.org` thumbnail redirects while retaining HTTPS, host, image, and download-size checks. This fixes remote wallpapers whose previews previously failed even with a working connection.
- Keep the wallpaper navigation and credits visible when error text or large fonts need more room; scroll the preview details instead. Preview photos now crop to the phone aspect ratio instead of stretching.

## 0.1.27 changes

- Fresh installs get a guided full-screen setup for home gestures and controls, a swipe practice area, an installed-app pin picker, and independent Contact search and whole-device File search switches. Each permission request has a separate disclosure and can be declined.
- Launcher settings has a Search section. Disabled sources disappear from search, stop indexing, and clear in-memory results without revoking Android permissions.
- Setup offers Android's default Home-app chooser after the user finishes. It can be replayed from Grove settings; existing installations keep their layout and settings.

## 0.1.25 changes (Nova: Rust migration, uncompiled — needs build + test)

- Search is now one native call per keystroke: `CoreBridge.searchNative` scores and
  top-K selects in Rust, returning winning indices. The old score-array + Kotlin
  PriorityQueue path is the fallback when the native library is absent.
- The Rust edit distance now counts UTF-16 code units exactly like Kotlin, so the
  old non-ASCII Kotlin fallback in ranking is deleted — native and Kotlin agree
  bit-for-bit. `Search.normalize`/`prepare` stay in Kotlin (tiny, correct, needed
  for the fallback).
- File extension table moved to Rust (`classifyNative` returns `mime|category`);
  `FileIndex` still consults Android's `MimeTypeMap` first, so behavior is unchanged.
- Procedural wallpapers render in Rust (`renderWallpaperNative` → ARGB pixels);
  the Canvas painter remains as the no-native fallback.
- New/updated Rust unit tests: top-K ordering parity (incl. 20k-label tie test),
  UTF-16 edit-distance parity (accents, emoji surrogate pairs), classify table,
  wallpaper opacity/size. See [RUST-PLAN.md](RUST-PLAN.md).

## 0.1.24 changes (Nova patch — uncompiled, needs Codex review + build)

Contact menu fixes (`ContactIndex.kt`, `MainActivity.contactMenu`):
- Phone numbers are normalized to digits before deduplication, so `917-669-7537` and `9176697537` no longer produce doubled Call/Text rows. The first original display format is kept.
- WhatsApp sync rows (profile / voice call / video call) collapse to one row per app variant via `collapseChannels()`; WhatsApp Business stays separate.
- "Message via WhatsApp" is now gated by `whatsAppTargets()`: it appears only when the contact has real WhatsApp sync data AND the app is installed — never speculatively for every phone number.
- One Call row + one Text row per contact. Tapping opens Android's app chooser (Phone, Google Voice, Linphone, …). A number-picker submenu appears only for genuinely multi-number contacts.
- New `ContactIndexTest.kt` (8 unit tests) covering normalization, channel collapse, WhatsApp gating, and install gating.

UI kit (`tech.granet.grove.ui.UiKit` — the reusable "CSS library"):
- `Context.dp()`, `Context.message()`, `Context.label()`, `titleText()`, `bodyText()`, `warningText()`.
- `iconRow()` — one tappable icon+title+subtitle row builder used by search results and menus.
- One-line dialogs: `menuDialog()`, `infoDialog()`, `confirmDialog()`, `listDialog()`, `scrollDialog()`.
- `settingsButton()`, `toggleRow()`, `LinearLayout.addSection()`.
- `MainActivity`, `SearchScreen`, `LauncherSettingsScreen`, `WidgetScreen`, `HomeScreen` all migrated onto the kit; five copies of `dp()` removed; 22 scattered dialog builders consolidated (5 bespoke ones remain: icon grid, text inputs, config editor, recovery). Behavior is intended to be identical — verify with a build.

Also see [CONTACT-REVIEW.md](CONTACT-REVIEW.md) for the perf + security pass on the contact path (threading, caching, log hygiene, permission rationale).

## Build

Install JDK 17, Android SDK 36, NDK 27.3.13750724, and Rust with the Android arm64, ARMv7, and x86-64 targets. Then run:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Gradle builds the Rust core before packaging the APK. Choose Grove in Android's default Home app selector. Keep another launcher installed so you can switch back in Android Settings.

## Features

- Home screen with clock, pinned apps, Android widgets, gestures, and optional navigation buttons. Hold a pinned app to move it or open its actions.
- Customizable app drawer with app folders, bulk selection, pinning, and Android's uninstall confirmation.
- Dedicated search with ranked app, contact, and optional file matches, followed by Google and Play Store actions. Long press a result for its context actions.
- Optional shared storage indexing. Android's all files access is requested only when file search is enabled; private app data and system partitions remain inaccessible. The index is bounded and never reads file contents.
- Procedural wallpapers and ten Wikimedia Commons color selections. Selected Commons files are downloaded on demand and cached privately; credits and license links are in [NOTICE](NOTICE).
- Settings with recoverable JSON configuration import/export under Advanced. Widget IDs remain local and are excluded from exports.

There is no account or telemetry. Internet access is used for the chosen Commons wallpaper; Google and Play Store searches open external apps or web pages.

## Architecture and security

Android views, storage permissions, widgets, intents, and wallpaper APIs remain in Kotlin. Rust handles batched search scoring, MIME categories, and bounded configuration preflight through JNI. Kotlin ranking remains a fallback if the native library is unavailable, and Kotlin validates the final configuration. See [ARCHITECTURE.md](ARCHITECTURE.md) and [TESTING.md](TESTING.md).

File sharing uses read only, per intent content URI grants. The provider is not exported. Configuration input and wallpaper downloads have size limits; wallpaper redirects stay on the expected HTTPS hosts. Android's broad storage access remains necessary for device wide file search and requires Play policy review and approval before distribution; see [PLAY-READINESS.md](PLAY-READINESS.md) and [PRIVACY.md](PRIVACY.md).

The APK under `app/build` is a development build, not a release signed distribution. Release signing and Play distribution are separate work.

## License

Apache-2.0 for original source and artwork. Third party dependencies and Commons wallpapers retain their respective licenses; see [NOTICE](NOTICE).
