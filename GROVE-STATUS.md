Grove Checks

Grove Configuration
/data/usr/0/Grove/files/datastore/grovesettings.conf

Every settings toggle as a boolean 1:0 (which includes layout and gesture prefs). System theme, as a light, dark, wallpaper. Wallpaper, for the  system theme light, dark, wallpaper pref.

##Grove Start
#Main Thread
Android initializes -> load settings from disk -> Draw first frames from saved settings -> lazily render app icons as they become available & submit to cache.
#Background work:
Start android system listeners for app changes and theme listeners, for light mode/dark mode and system wallpaper changes.

##Startup Failure
#Startup Failure Rules
1. If settings file is not present, generate a default settings file, where permissions states default to denied, and gestures default to on. The system theme will default to system theme, which grove will grab on startup and apply the value that's passed.
2. If app list is not cached in ram, use android callback to get applist, and lazily render icons. If android cannot return, show the "Grove has encountered an error" workflow, so they can email us.
#Startup Failure Procuders
1. If settings does not exist, show the first time user setup, so the user can naturally pick their settings. Apply settings to grovesettings.conf.

##Grove has encountered an error workflow
#GHEAEW (pronounced G-He-Ew) is the workflow that gets called, when an error occurs that can prevent Grove from functioning as intended. The developer email is support@granet.tech and the contents should display what the error code is. In this workflow, the user can choose whether to send the error to us. Reporting is offered at every severity. Continue is available only when the affected function can safely run or degrade; a Stop error instead offers retry or recovery. GHEAEW owns the error reporting workflow. GHEAW will open the users mail app, populate the TO field, and include necessary information. All the user has to do is hit send.
#Error Code Severity
Recover -   The service can be fully recovered.
Degrade -   The service can run, but in a degraded state.
Stop    -   The service has encountered a critical fault and cannot continue.
#Error Code Library:
Config Errors---------------100-199
App Inventory & Management--200-299
UI/UX Errors----------------300-399
System Errors---------------400-499
GFI Errors------------------500-599
GCI Errors------------------600-699


##File Indexing and Contact Indexing:
#Grove File Indexing (GFI) is a service that runs in the background, that indexes the contents of discoverable user folders, for quicker access to files and folders. This can be turned off or disabled in settings.
#Grove Contact Indexing (GCI) is a service that runs in the background, that indexes the contents of discoverable contacts, and their details, such as name, phone number, messaging number, addresses, etc. This indexing data is stored on-device, and can be turned off in settings.
#GCI and GFI States::
Indexed-------------------------1
Needs Indexing------------------2
Indexing Disabled---------------3
Indexing Error------------------4
Indexing Ready------------------5
Indexing Stale------------------6 <---{Falls back to Needs Indexing}
Indexing Cache Not Available----7 <---{Falls back to Needs Indexing}
Indexing Cache disabled---------8 <---{Falls back to indexing disabled}
#Indexing Source Of Truth:
In order of priority, with android system being the source of truth: Android System --> Index Cache --> UI.
#Indexing Cache is an up-to-date repo of indexed files and indexed contact info from android system.
#Indexing Cache acceptable states are listed above. In the event that index cache is missing, the system will make the cache and then proceed with indexing. The cache being disabled will prevent this behaviour, and will disable indexing alltogether.
#GFI and GCI  each have their own indexing cache, with their own indexer. GFI and GCI do not share a single cache, nor do they share indexers. Each indexing service is its own sovergn entity. They are, however, tied at the hip by convention, listed states, and failure modes. GFI/GCI erroring, does not result in the search feature becoming unusable, it just means we can't index properly. is is an important distinction, as search can still function without indexing.
#Non-Indexing Search Functionality is used when the user wants to use contact or file search, but does not enable indexing. Instead of using GFI/GCI, we will use a separate service called Grove File Search, GFS (FS not to be confused with File System, it means File Search). GFS will search the available file system, and show results to the user. This will be slower, so it is against UX convention to have this disabled by default. During initial setup, this can be exposed as an option for the user to pick, and it can be enabled by default. Opting out of indexing, does not mean search is disabled. Disabling file search permissions disables both file indexing AND file search, for the obvious reason, that we cannot index/search what we cannot access.
#Grove Contact Searching (GCS) is when GCI is disabled, but we still have contact permissions. Its the same flow as GFS, except with contacts. We search live, and return results live, per contact, with contact info being exposed when the user taps on the specified contact. Disabling permissions also disables this.
#GFI/GCI/GFS Permission Source of Truth: android system --> grovesettings.conf --> cache --> ui. Flow of information, as established above: on startup we ask for perms access, store that result, keep it on file in settings, and keep listeners open for perms changes, and update the grovesettings.conf file accordingly. The source of truth in this, is android system perms, which is granted or disabled by the user. That should happen only once, and that's during initial setup. WHich is also why we run initial setup when the settings file is deleted or missing (to establish the baseline). From there, eventListeners and sysListeners can pickup and change the settings file, if perms are changed by the user in the system settings app, and not the grove app itself. HOWEVER, enabling the permission DOES NOT automatically enable file searching. The user must still enable it in settings, to make use of it.
#Permissions denied flow: when permission is denied, we say that GCI/GFS/GFI is disabled as well. File system access disables GFS/GFI, contact permission disables GCI/GCS

##Grove Wallpaper Selector (GWS) is a service that allows you to select a wallpaper from some pre-defined selection of wallpapers, while also allowing the user to choose a wallpaper of their own, by selecting it from their photos or file app. The default wallpapers are provided free of charge, with credits and licensing provided inside the selector via a button. The Selector is a modal with swipe and buttons to change wallpapers. Wallpapers are loaded in background, and the user can select a wallpaper during the initial startup tutorial.
#GWS Failure Categories
UI Failure--------------------------UI
Caching/Pre-Caching Failure---------CACHE
HTTP/HTTPS Failure------------------NETWORK
Selection/Apply Failure-------------APPLY
UX Failure--------------------------UX
Storage Read Error------------------READ
Storage Write Error-----------------WRITE
#GWS diagnostic codes are namespaced: GWS-<category>-<detail>, for example GWS-NETWORK-01. When a GWS failure causes a Grove-level failure, report both codes, for example 300 + GWS-UI-01. The 100-699 ranges remain exclusively Grove-level codes; GWS codes never change their meaning. Detail numbers are assigned in a separate registry as implementation proceeds.
#GWS source of truth lives in android full stop. Wallpapers can be disabled, setting the screen to black, which means that we set the wallpaper, to an image of #000000. To do that, however, we neet to invoke android wallpaper selector, to change that wallpaper. We also need to grab what the current wallpaper is, from android, instead of relying solely on settings for that. So settings contains toggles, switches, and configurations that can change how wallpapers behaves, but for system related items, android callbacks establish the source of truth.
#Example source of truth for firstTime-setup: existing android wallpaper --> user changes the wallpaper in initial setup --> GWS calls android wallpaper selector and changes the wallpaper to what the user selected.
#Example source of truth for changing a wallpaper later: existing android wallpaper --> user changes wallpaper in GWS --> GWS calls android walpaper selector and changes the wallpaper to what the user selected.
#Example of source of truth overwrite: existing android wallpaper --> user disables the wallpaper in GWS --> gws calls android wallpaper selector and changes the wallpaper to what the user selected.
#GWS can fail, even spectacularly, and the rest of the app can continue running along without any hiccups. There are no permissions nor are there any crazy settings we can create that would make GWS a time bomb or nuclear bomb that touches other aspects of the system. If GWS causes Grove to lock, that is a Grove-level system fault and receives an assigned code from the 400-499 range, alongside the GWS diagnostic if known.
#GWS failing closed: GWS failing closed operations should be relatively uncommon, but should not take the ui down with it. User selects wallpaper A --> GWS attempts retrieval --> https failure --> prompt user to retry, or select wallpaper b. We failed closed, in that we cannot proceed with Wallpaper A. We then use a graceful fallback, to attempt another retry or pursue a different avenue.

##Dependency Stack (completed design rules)
A hard dependency is necessary for that service to do its job. It is not necessarily a hard dependency of the launcher. A soft dependency permits the service to run in a defined reduced mode. Check prerequisites before starting dependent work; never treat a saved permission flag as an actual grant.

#Grove startup and home screen
- Android home/activity lifecycle: hard. If Android cannot launch or resume Grove, Grove cannot operate (Stop); let Android's home selection and recovery mechanisms work.
- Android package inventory: hard for the app list/drawer. If unavailable, retry; show the Grove error workflow and a usable recovery surface if possible. Do not claim a cached list is current.
- Settings storage: soft when missing or invalid and writable (rebuild defaults, Recover; run onboarding when no valid prior setup exists). If defaults cannot be held in memory or a minimal home cannot render, Stop.
- Wallpaper/current system palette: soft (neutral background/palette, Degrade). A wallpaper failure cannot prevent app launch.
- File/contact permission grants: soft (disable only the corresponding features). Permission checks are hard for those features to access protected data, not for Grove startup.
- App icon cache: soft (placeholder icon, then lazy load/retry; Degrade).
- Theme and package listeners: soft (refresh on next resume or explicit retry; Degrade). Listener setup must not block the first usable frame.

#Tutorial and settings
- Grove settings writer: hard for persisting choices; if unavailable, keep the screen usable but do not claim a choice was saved. Report and retry (Degrade).
- Permission state query: hard before showing a feature as granted or accessing protected data; if unavailable, show unavailable and retry (fail closed for access).
- Permission grant itself: soft. Denial skips the corresponding optional features and tutorial steps; the home remains usable.
- Wallpaper selector: soft. Skipping or failing selection leaves the existing Android wallpaper as is.

#App inventory, launch, and management
- Android package APIs: hard for live inventory, launch resolution, install/uninstall actions. Failure stops that action only, with an error and retry; the rest of Grove remains available.
- Cached labels/icons: soft, display placeholders and reload. Cache must not authorize launching an absent app.
- Selection, pinning, folders: settings persistence is hard for committing changes; reject or roll back a failed save. App availability is checked before launching; a stale entry can be removed without breaking home.

#Search
- App search: hard dependency on current app inventory; if unavailable, show a scoped failure while other search routes remain available.
- File search: hard dependency on Android file access and the user's enabled search preference. GFI is soft; use GFS live search when indexing is off, stale, or failed, if permission and preference allow it.
- Contact search: hard dependency on Android contact access and the user's enabled search preference. GCI is soft; use GCS live search when indexing is off, stale, or failed, if permission and preference allow it.
- External web/Play search: soft dependency on a resolvable destination and connectivity; failed external search must not disable local search.
- A missing or revoked grant fails closed for the affected data source; a user-disabled search preference remains off even if Android later grants permission.

#GFI and GCI (separate services, caches, and indexers)
- Android data provider and actual permission: hard for reading that service's data. If unavailable or revoked, stop that indexer immediately, discard or quarantine its inaccessible cache, and omit its results (fail closed).
- User indexing preference: hard authorization for indexing. Disabled means no background indexing; permission alone never enables it.
- Cache: soft. Missing/stale/unreadable cache triggers rebuild when indexing is enabled; otherwise live search can serve permitted queries.
- Background worker: soft for search. A failed worker marks the relevant index in error and routes permitted queries to live search, without affecting the other index or the launcher.
- At query time, Android's current permission state and the user's current preference outrank cache contents and saved permission snapshots.

#GFS and GCS (live search)
- Android data provider, actual permission, and enabled search preference: hard. Denial or disablement returns no protected results and does not invoke the provider.
- GFI/GCI and their caches: no dependency. They may be missing or disabled.
- Provider/query failure: fail closed for that query, show a scoped error and retry; app and other search categories remain available.

#GWS
- Android wallpaper service/picker: hard for applying a selected wallpaper. Failure leaves the existing wallpaper unchanged and offers retry/another selection (fail closed for the apply operation).
- Wallpaper thumbnails/cache/network: soft. Show available local choices, a placeholder, retry, or let the user skip; do not block home.
- Selected source read and Android acceptance: hard for committing that selection. Verify success before displaying it as applied. On failure, retain the prior wallpaper and report a GWS diagnostic.
- GWS availability: soft for Grove startup, tutorial, home, search, and app management.

#GHEAEW error workflow
- An error event/code: hard to show a specific report. If unknown, show a generic code without inventing a cause.
- Mail app: soft. Offer to copy the diagnostic text when no mail handler exists; user explicitly sends any email.
- Reporting: optional at Recover, Degrade, and Stop severity. Recover and Degrade may offer Continue when safe. Stop offers retry, settings, or Android recovery; Continue must never imply the stopped function is working.
- Include code, affected service, severity, and safe diagnostic context. Exclude private file contents, contact details, and secrets by default.

## Open implementation checks
- Confirm the real Android settings path and current config schema against the repo; the path above is a design placeholder until verified.
- Assign exact 100-699 codes and GWS detail codes in a registry; avoid assigning numbers from prose examples.
- Verify which Android file scopes and permissions Grove actually supports before promising live search or background indexing of all discoverable folders.
- Map these rules to the existing activities/classes, then test startup with missing/corrupt settings, revoked permissions, broken caches, absent mail app, and wallpaper apply failure.
