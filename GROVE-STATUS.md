# Grove behavior and failure rules

**Current code map:** [SYSTEM-CATALOG.md](SYSTEM-CATALOG.md) records the baseline systems, invariants, data flow, dependencies and failure boundaries with pinned source lines.

**Status:** Design proposal. This describes how Grove should behave; it is not a claim that every rule is implemented today. See [Open implementation checks](#open-implementation-checks) before using it as a test checklist.

Grove is the phone's home screen. A broken wallpaper download or contact index should not make the user lose their launcher. Each feature owns its own failure, and protected data stays unavailable whenever Android permission or the user's Grove setting says it should.

## Implementation status on `main`

This is the target contract. The current app already has a recoverable Home, permission-aware in-memory contact/file search, partial file-scan reporting, off-thread wallpaper preparation, and a user-directed crash email. The following parts are **proposed, not shipped**:

| Target behavior | Current implementation | Tracking |
| --- | --- | --- |
| Search with indexing independently disabled; live GFS/GCS | One contact and one file search switch; enabling a source loads/scans it | [#74](https://github.com/davidcit646/Grove/issues/74) |
| Separate durable GFI/GCI caches and the eight index states below | Separate in-memory lists only; no persistent search index or index-only switch | [#75](https://github.com/davidcit646/Grove/issues/75) |
| User-selected wallpaper and solid black choice | Three generated designs and ten Commons choices | [#76](https://github.com/davidcit646/Grove/issues/76) |
| Android-applied-wallpaper reconciliation and explicit theme modes | Saved Grove selection plus wallpaper-button-color toggle; system theme styling | [#77](https://github.com/davidcit646/Grove/issues/77), [#29](https://github.com/davidcit646/Grove/issues/29) |
| GHEAEW severity/code registry, safe report draft, default recipient | Saved crash/nonfatal reports and a next-launch email choice; no numeric code UI or default address | [#78](https://github.com/davidcit646/Grove/issues/78), [#28](https://github.com/davidcit646/Grove/issues/28) |

The [PR #73](https://github.com/davidcit646/Grove/pull/73) MainActivity lane split is on `main`; its #6/#34 device gates were closed unrun by user scope decision. [TESTING.md](TESTING.md) and [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) retain the unrun Android device checklist. [#79](https://github.com/davidcit646/Grove/issues/79) tracks documentation alignment.

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

The original draft named `/data/usr/0/Grove/files/datastore/grovesettings.conf`. Treat that as an **unverified placeholder**, not an Android path or format to implement. Check the repository's actual storage code first. The desired behavior matters more than that filename.

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

These numbers are proposed service states, **not** Grove error codes. The cache never gives itself permission to return data. On revocation, stop the affected indexer immediately and discard or quarantine inaccessible cached results before another query can expose them.

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

The ranges are reserved; individual codes still need a registry. Do not invent a code from an example. Error reports include safe context, not private data.

## Open implementation checks

- Compare this proposal with the current activities, settings schema, search/index code, and wallpaper implementation. Split ownership along those real boundaries rather than creating classes solely to match acronyms.
- Confirm the actual settings location and format; remove the placeholder above once verified.
- Confirm Android's accessible file scope and permission behavior before promising searches across every user folder. Keep app-private and system data outside the promise.
- Decide exact code assignments and diagnostic details in one registry.
- Test missing/corrupt settings, failed saves, unavailable app inventory, revoked permissions, stale/broken caches, missing mail app, and wallpaper download/apply failure. Verify each failure leaves unrelated features usable and never leaks disabled search results.
