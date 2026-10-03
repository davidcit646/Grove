# Smaller Kotlin boundaries after the refactor

Review target: stacked refactor through PR #54 plus this branch, 2026-10-03. This is a source review; #34 is the Android device gate. The baseline inventory is [FAILURE-BOUNDARY-AUDIT.md](FAILURE-BOUNDARY-AUDIT.md). Issue #31.

| File | External input or decision | Check and outcome | Verification |
| --- | --- | --- | --- |
| `HomeScreen.kt` | Screen configuration and callbacks | Receives validated Config; optional controls and widgets render independently. Widget failures are contained by WidgetScreen. | #21 recovery, #26, #34 |
| `LauncherSettingsScreen.kt` | Toggle and document actions | Emits candidate Config to Activity; #27 commits before activation. Search enabled means user preference, with permission/loading/failure shown in SearchScreen. Crash reports remain until explicit delete. | #23, #27, #28, #34 |
| `SearchScreen.kt` | App/contact/file source state | Explicit Disabled, PermissionRequired, Loading, Ready, Partial and Failed; failed source shows Retry while other results remain available. | SearchSourceStateTest, #23, #34 |
| `Search.kt` | Query text | Normalization and bounded score; no Android call. Kotlin fallback is required when native unavailable. | SearchTest, #33 |
| `SearchResults.kt` | Prepared labels and native order | Inputs prepared once; CoreBridge fallback owns native failure behavior. Ranking parity and 15k latency still need #33/#35. | SearchResultsTest, #7 |
| `PinnedApps.kt` | Item/target keys | Missing key or target returns unchanged order; DrawerState commits only changed candidates. | PinnedAppsTest, DrawerStateTest |
| `Gestures.kt` | Distance/duration/settings | Unsupported or disabled gesture returns NONE. GestureSession clears capture on pointer cancellation. | GesturesTest, GestureSessionTest, #34 |
| `ThemeColors.kt` | Theme attribute and wallpaper bitmap | Missing attribute uses a safe color; wallpaper color extraction failure uses legible green/white. Extraction now runs on wallpaper worker. | #29, #34/#35 |
| `ui/UiKit.kt` | Shared labels, rows and dialog callbacks | Presentation only; callers own action checks. Theme text is used for labels and default rows in light/dark modes. | #34 visual/font check |
| `WallpaperPicker.kt` | Download/decode preview and destination | Preview failure exposes Retry; apply is gated on a ready preview. Controller applies before Home preference commit. | #29, #34 |
| `WallpaperArt.kt` | Native generated pixels | CoreBridge can use Canvas fallback for generated art; malformed JNI result validation belongs to #33. | #33, #34 |
| `WidgetScreen.kt` | Provider info, RemoteViews, label, size | Missing/throwing provider yields removable placeholder; createView failure does not remove Home; label failure retains removal menu; size failure is logged. Registry owns IDs. | #26, #34 |
| `ContactIndex.kt` | Provider cursors, rows and details | Null aggregate or details cursor is a failure, distinct from empty results. Activity exposes failure and retry; details failure shows an unavailable message. | ContactIndexTest, #5/#23, #34 |

This review found and fixed three concrete fail-open leaks: wallpaper color extraction on Home, null contact-details cursor appearing as an empty success menu, and widget removal depending on a provider label. Shared text color now follows the theme. Android view inflation and packaged resource failures remain build/device gates, not exceptions to silently hide.

Remaining cross-file proof is tracked by #32 (deterministic failure injection), #33 (native parity), #34 (device), and #35 (measurements). No readiness is inferred from a successful permission request alone.
