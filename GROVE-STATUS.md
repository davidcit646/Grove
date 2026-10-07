# Grove behavior and failure rules

**Current code map:** [GROVE-SYSTEM-MAP.md](GROVE-SYSTEM-MAP.md) is the complete release-pinned ownership, invariant, failure-method and information-flow reference, including every Kotlin/Rust source file and named declaration. [SYSTEM-CATALOG.md](SYSTEM-CATALOG.md) retains historical source audits. This file remains authoritative for behavior and capability status.

**Status:** Target behavior and failure contract, with release-pinned implementation evidence below. This is not a claim that every rule or device scenario has been verified. See [Open implementation checks](#open-implementation-checks) before using it as a test checklist.

Grove is the phone's home screen. A broken wallpaper download or contact index should not make the user lose their launcher. Each feature owns its own failure, and protected data stays unavailable whenever Android permission or the user's Grove setting says it should.

## Implementation status on `main`

This file remains the source of truth for intended behavior and the canonical implementation-status matrix. **Release baseline:** [v1.0.0](https://github.com/davidcit646/Grove/releases/tag/v1.0.0), published October 6, 2026, at `f9455918d0bcd7f5f038bb3cd87fa64c4a93be48` (version code 32). `main` had the same source baseline when this reconciliation was prepared. Later documentation commits do not change the shipped binary. [Release CI](https://github.com/davidcit646/Grove/actions/runs/37491934642) passed Rust/JVM tests, debug assembly, lint, the missing-signing negative gate, signed APK/AAB verification and stable publication.

The five capabilities below are **included in v1.0.0**, replacing the earlier pre-release implementation notes. Included means implemented and packaged; it does not establish every target rule, Android/OEM interaction or failure path as verified. [TESTING.md](TESTING.md) owns detailed execution records and pending checks; [BUILD-STATUS.md](BUILD-STATUS.md) owns build/release evidence.

| Target behavior | Release owner / pinned code | Implementation at v1.0.0 | Tracking | Test / device evidence |
| --- | --- | --- | --- | --- |
| Search with indexing independently disabled; live GFS/GCS | [Config](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/Config.kt), [SearchController](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/SearchController.kt), [SearchSources](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/SearchSources.kt) | Separate persisted search/indexing switches; bounded permitted live lookup when indexing is off or a cache is unavailable | [#74](https://github.com/davidcit646/Grove/issues/74) | SearchPublicationGateTest, ConfigTest; device permission/query/action, latency and cancellation checks pending |
| Separate durable GFI/GCI caches and eight index states | [IndexCache](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/IndexCache.kt), [IndexWork](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/IndexWork.kt), [IndexState](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/IndexState.kt) | Separate atomic app-private metadata caches, independently scheduled workers and explicit states; caches never authorize access | [#75](https://github.com/davidcit646/Grove/issues/75) | IndexStateTest, IndexRefreshRequestsTest; device corrupt/stale/partial cache, failed write, revocation and process-death checks pending |
| User-selected wallpaper and solid black choice | [WallpaperArt](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/WallpaperArt.kt), [WallpaperController](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/WallpaperController.kt), [WallpaperPicker](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/WallpaperPicker.kt) | Three generated designs, ten packaged Commons images, solid black and a bounded user-selected image; Android apply precedes Grove preference commit | [#82](https://github.com/davidcit646/Grove/issues/82), [#76](https://github.com/davidcit646/Grove/issues/76) (#76 closed as duplicate) | WallpaperRegistryTest, WallpaperControllerTest; offline/apply/cancel/invalid-input/lifecycle device checks pending |
| Android-applied-wallpaper reconciliation and explicit theme modes | [PresentationController](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/PresentationController.kt), [Config](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/Config.kt), [ThemeColors](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/ThemeColors.kt) | Android renders Home static/live wallpaper; ID/color metadata refreshes button colors; System/Light/Dark/Wallpaper choices persist | [#77](https://github.com/davidcit646/Grove/issues/77), [#29](https://github.com/davidcit646/Grove/issues/29) | PresentationPolicyTest, ConfigTest; external/live/Home/Lock/Both and OEM device checks pending |
| GHEAEW severity/code registry, safe report draft, default recipient | [GroveErrors](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/GroveErrors.kt), [CrashReporter](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/CrashReporter.kt) | Assigned numeric/GWS registry, Recover/Degrade/Stop UI, bounded retained reports, support@granet.tech draft and no-mail copy fallback; no automatic send | [#78](https://github.com/davidcit646/Grove/issues/78), [#28](https://github.com/davidcit646/Grove/issues/28) | GroveErrorRegistryTest, GroveErrorRoutingTest, CrashReporterPrivacyTest, ReportPromptPolicyTest; actual dialog/mail/copy/retention failure checks pending |

PRs [#73](https://github.com/davidcit646/Grove/pull/73), [#83](https://github.com/davidcit646/Grove/pull/83), [#87](https://github.com/davidcit646/Grove/pull/87), [#90](https://github.com/davidcit646/Grove/pull/90) and [#92](https://github.com/davidcit646/Grove/pull/92) are merged and included in this baseline. The #6/#34 device gates were closed unrun by user scope decision; closure is not passing evidence. Limited user acceptance of tested workflows does not complete the broader Android/OEM/accessibility/fault-injection matrix in TESTING.md and FAILURE-VERIFICATION.md. [#79](https://github.com/davidcit646/Grove/issues/79) tracks this reconciliation.

**Documented onboarding exception — #89:** The rules below retain the original setup indexing-choice description. [#89](https://github.com/davidcit646/Grove/issues/89) explicitly requested indexing preferences on for genuine fresh installs and indexing controls only in Settings. v1.0.0 implements that request: optional search sources start off, work requires enabled search plus current Android access, and existing/imported/replayed choices are preserved. Search off stops the affected indexer and deletes its cache; an indexing preference alone never authorizes work. This exception is recorded separately rather than silently rewriting the target rules.

## The rules in plain language

1. **Get the home screen on screen first.** Load saved choices, show a usable home, and fill in icons as they become available. Start package, theme, and wallpaper listeners without holding up that first usable screen.
2. **Ask Android what is true now.** Android owns installed apps, permissions, contacts, accessible files, and the applied wallpaper. Saved settings and caches can speed things up, but they cannot overrule Android.
3. **Honor the user's switches.** Android granting a permission does not turn Grove search or indexing on. The corresponding Grove setting must also be enabled.
4. **Contain failures.** If a feature cannot do its job, stop that operation, explain it, and keep unrelated parts of Grove available.
5. **Report only with the user's action.** Grove can prepare an email to `support@granet.tech`; the user reviews and sends it. Never include private file contents, contact details, or secrets by default.

A **hard dependency** is something a particular operation needs to succeed. It is not automatically a hard dependency of the whole launcher. A **soft dependency** permits a defined fallback. **Fail closed** means do not perform an operation or expose results without its prerequisites. **Fail open** here means the rest of Grove remains usable; it never means bypassing a permission or preference.

## Starting Grove

1. Android starts or resumes Grove.
2. Grove loads its saved configuration and draws a usable home with available app information. Disk work and icon rendering must not hold up the main thread.
3. Grove checks the current Android state and reconciles it with saved choices.
4. In the background, Grove observes app changes, light/dark theme changes, and wallpaper changes. If a listener cannot start, refresh on resume or retry instead of blocking home.
5. Grove loads or renders missing app icons lazily and caches them for next time.

On a genuine first run with no saved settings, create safe defaults and show setup. If saved custom settings are corrupt, preserve them separately and show a safe fallback with a recovery choice; do not treat corruption as a fresh install. Permission states begin as **ungranted until checked against Android**; gestures begin enabled; theme follows the system. Save chosen settings only after a confirmed write, and never claim a failed write succeeded.

If Android's app inventory cannot be read, retry and show a scoped error with whatever recovery surface Grove can render. Do not present stale cached apps as a verified current list. If Grove cannot render even a minimal home, that is a **Stop** failure.

**Verified v1.0.0 storage:** [GroveApp](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/GroveApp.kt) supplies private SharedPreferences named `grove` to [ConfigStore](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/ConfigStore.kt). Config is JSON under `config`, with separate damaged/fallback recovery entries; [Config](https://github.com/davidcit646/Grove/blob/f9455918d0bcd7f5f038bb3cd87fa64c4a93be48/app/src/main/java/tech/granet/grove/Config.kt) emits schema v12. The original draft path was a placeholder and is not used.

## Who owns each piece of data?

| Data or decision | Source of truth | What Grove may save |
| --- | --- | --- |
| Installed apps and whether an app can launch | Android package APIs | Labels and icons as disposable caches; pinned app choices |
| Permission to read contacts or files | Android's current permission state | Last observed state for display, never as proof of access |
| Whether contact/file search or indexing is enabled | The user's Grove setting | The setting itself |
| Contacts and accessible files | Android providers and permitted storage | Separate, rebuildable local indexes when enabled |
| Applied wallpaper | Android wallpaper state | Grove's selector choices and display preferences |
| Theme preference, layout, and gestures | The user's Grove settings | Persisted configuration, validated on load |

When sources disagree, check the authoritative source before the affected operation. A revoked permission takes effect even if an index or an old settings value says it was previously granted.

## Search and indexing

Grove has two optional indexes: **Grove File Indexing (GFI)** and **Grove Contact Indexing (GCI)**. They run independently, use separate caches, and follow the same state and failure conventions. Indexing stores discoverable metadata on the device to speed search; it should not read file contents merely to index filenames. Each service can be disabled in settings.

Indexing and searching are separate choices:

- With search and indexing enabled, Grove can use the relevant local index.
- With search enabled but indexing disabled, **Grove File Search (GFS)** or **Grove Contact Search (GCS)** queries the permitted Android source live. It may be slower, but search still works.
- With an index missing, stale, or broken, permitted search can fall back to GFS or GCS while the index rebuilds or remains disabled.
- With search disabled in Grove, the source returns no results, even when Android permission exists.
- With Android permission denied or revoked, both indexing and live search for that source stop and show no protected results. One source's failure does not disable the other or app search.

The first-run tutorial may explain the speed tradeoff and offer indexing as a choice. It must not silently turn indexing on just because a permission was granted. Android permission requests are made when needed; subsequent permission changes are rechecked, including after returning from system settings.

### Index states

| State | Meaning | Next action |
| --- | --- | --- |
| `1 Indexed` | Cache is current and usable | Search it, subject to current permission and Grove setting |
| `2 Needs indexing` | Work is required | Queue indexing when enabled and permitted |
| `3 Indexing disabled` | User turned indexing off | Do not index; use live search if search is enabled |
| `4 Indexing error` | Indexing failed | Report the scoped error; use permitted live search |
| `5 Indexing ready` | Prerequisites are met | Start or resume indexing |
| `6 Indexing stale` | Cache is out of date | Treat as needs indexing |
| `7 Cache unavailable` | Cache is missing or unreadable | Rebuild if enabled; otherwise use permitted live search |
| `8 Cache disabled` | Cache use is deliberately off | Treat as indexing disabled |

These numbers are contract state labels, **not** Grove error codes; v1.0.0 implements the named states in `IndexState`. The cache never gives itself permission to return data. On revocation, stop the affected indexer immediately and discard or quarantine inaccessible cached results before another query can expose them.

## Wallpaper selection

**Grove Wallpaper Selector (GWS)** offers built-in wallpapers with credits and license information, plus a choice from the user's photos or files. The selector can appear during setup or later. Previews load in the background; a failed preview must not freeze the home screen.

Android owns the applied wallpaper. Grove reads the current state, asks Android to apply a selection, and shows it as applied only after success. If the user chooses a black background, GWS proposes a `#000000` wallpaper through the Android wallpaper flow; implementation and platform behavior still need verification.

A wallpaper operation **fails closed**: if wallpaper A cannot download, be read, or be applied, do not claim A is active. Keep the prior wallpaper, offer retry or another choice, and let the user continue using Grove. Network, preview cache, and the entire selector are **soft dependencies** of the launcher. An unhandled GWS exception that locks Grove is a Grove-level system fault.

GWS diagnostic categories are `UI`, `CACHE`, `NETWORK`, `APPLY`, `UX`, `READ`, and `WRITE`. Use `GWS-<category>-<detail>` once exact details are assigned, such as `GWS-NETWORK-01`. If it also causes a Grove-level error, report both codes. GWS codes do not consume the main Grove numeric ranges.

## What depends on what?

| Operation | Must have to succeed | If unavailable |
| --- | --- | --- |
| Render minimal home | Android activity lifecycle and a renderable UI | Stop; offer recovery where possible |
| Show current app inventory or launch an app | Android package/launch APIs | Stop that action, retry, and report; keep other Grove surfaces usable |
| Show app icons | Icon loader/cache | Use placeholders and retry lazily |
| Persist a setting, pin, or folder change | A successful settings write | Reject or roll back that change; do not say it was saved |
| Show an optional feature as permitted | A current Android permission check | Treat access as unavailable until checked |
| Search apps | Current app inventory | Show a scoped failure; other search sources can remain available |
| Search files or contacts | User-enabled search, current Android access, working provider | Return no results for that source; other sources continue |
| Index files or contacts | User-enabled indexing, current Android access, working provider | Stop that indexer; permitted live search may continue |
| Use an index cache | Valid, permitted, current cache | Rebuild or use live search; never trust a stale grant |
| Apply a wallpaper | Readable selected source and Android accepting the change | Leave old wallpaper in place and offer retry/another choice |
| Open a prepared error email | Mail app that handles the intent | Offer copyable diagnostic text; reporting stays optional |

Web and Play Store searches need a resolvable destination and working external service. Their failure does not disable local search. A missing theme or wallpaper listener can be handled with a refresh on resume. A missing settings file can be rebuilt; a failed save cannot be represented as success.

## When something goes wrong

The **“Grove has encountered an error”** workflow (GHEAEW, “G-He-Ew”) owns user-facing reporting. Show the affected feature, a useful message, severity, and an assigned code when one exists. Offer retry or a safe alternative. The user can choose to prepare an email addressed to `support@granet.tech`, review it in their mail app, and press Send. If no mail app is available, offer to copy the diagnostic text.

| Severity | Meaning | User action |
| --- | --- | --- |
| **Recover** | Grove can restore the service fully | Retry or continue after recovery |
| **Degrade** | The service or Grove can run with reduced function | Continue safely or retry |
| **Stop** | The affected critical function cannot continue | Retry, open relevant settings, or use Android recovery; never label it working |

The reporting choice exists at every severity. “Continue” is offered only when the affected path can safely continue or degrade.

| Grove code range | Owner |
| --- | --- |
| `100–199` | Configuration |
| `200–299` | App inventory and management |
| `300–399` | UI and UX |
| `400–499` | System |
| `500–599` | GFI |
| `600–699` | GCI |

The ranges are reserved; v1.0.0 assigns individual codes in the `GroveErrors` registry linked above. That registry, rather than an example in this document, defines assigned codes. Error reports include safe context, not private data.

## Open implementation checks

- Compare this proposal with the current activities, settings schema, search/index code, and wallpaper implementation. Split ownership along those real boundaries rather than creating classes solely to match acronyms.
- Confirm Android's accessible file scope and permission behavior before promising searches across every user folder. Keep app-private and system data outside the promise.
- Keep exact code assignments and diagnostic details in the existing GroveErrors registry; verify actual UI/report behavior on device.
- Test missing/corrupt settings, failed saves, unavailable app inventory, revoked permissions, stale/broken caches, missing mail app, and wallpaper download/apply failure. Verify each failure leaves unrelated features usable and never leaks disabled search results.
