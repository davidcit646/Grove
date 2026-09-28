# Grove architecture

`MainActivity` coordinates HOME navigation, gestures, Android result contracts, and app launching. Android framework calls remain in Kotlin.

- `HomeScreen`, `SearchScreen`, `LauncherSettingsScreen`, and `WidgetScreen` build the respective UI surfaces.
- `WallpaperController` owns wallpaper downloads, decoding, cropping, and system application.
- `ConfigStore` owns saved configuration and the recoverable fallback. The JSON format and preference keys are unchanged.
- `FileIndex` scans accessible shared storage on the worker thread.
- `SearchResults` ranks apps and files in stable order.
- `rust/grove-core` implements fuzzy score calculation, MIME categories, and bounded config preflight. `CoreBridge` batches JNI calls. Kotlin ranking and categories remain available if the native library cannot load; Kotlin's config parser remains authoritative.

The Gradle `preBuild` task calls `scripts/build-rust-android.sh`. It uses NDK 27.3.13750724 and Rust targets for arm64, ARMv7, and x86-64, then places the `.so` files under `app/build/rustJniLibs` for APK packaging. CI runs Rust unit tests, the Android build, Kotlin unit tests, and lint.

Keep `Config.parse` and the Rust preflight compatible with exported versions 1–6. A broken saved custom config must remain recoverable until the user explicitly saves a valid replacement or loads defaults. When changing Rust ranking, compare it with `Search.scoreNormalized` on exact, prefix, multiword, and typo cases before removing the Kotlin fallback.
