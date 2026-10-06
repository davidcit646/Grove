# Device acceptance checklist

## PR #87 source verification (2026-10-05)

Review branch `codex/search-theme-audit-31-77-85`, production/test source `531d7870222d194018551e4a54cd63b8454ef8b4`, passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37356982336): Rust tests, debug APK assembly, JVM tests, lint and the missing-signing-secret negative gate. Signed release steps were skipped in this PR run. This is source/build verification; new Android visual, permission, provider and lifecycle cases below have not been run by the assistant. `GROVE-STATUS.md` remains unchanged.

#86 follow-up source `a2b7347ff0afefb401fbd5fe96bf60f653e5d2aa` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37360704390) (Rust, APK, JVM tests, lint and missing-signing gate). It removes the global Home gradient; text shadows and inset-sized system-bar protection are local only. Device visual acceptance remains pending.

## PR #87 Android acceptance checklist

- #86: compare bright, dark and solid-black wallpapers against Android/source views in each theme. No full-screen dimming; clock/date, pinned labels, app icons and status/navigation icons remain readable. Repeat with live wallpaper, rotation, gesture/three-button navigation and IME open; bar protection must stay within system-bar bounds.
- Type, erase and replace queries rapidly while contact/file live queries and index jobs complete. Search must not blank/flash or repeatedly rebuild unchanged rows. Revoke/disable a source during this test: its protected rows disappear immediately; app search stays usable.
- Change Home wallpaper outside Grove, including a live wallpaper; return Home/restart and verify Android's actual image remains visible. The library may remember Grove's last choice but must not assert it is currently applied.
- Apply Home, Lock and Both choices. Lock-only must leave Home wallpaper and Home-derived button colors unchanged. Test apply failure and successful Android apply followed by failed Config save; Home still reflects Android, and only a confirmed save updates the remembered choice.
- Change System/Light/Dark/Wallpaper colors, switch Android night mode, rotate/restart and import legacy v1/v8/v9 settings. Verify v11 roundtrip and Kotlin/native acceptance; malformed themes/grids and future v12 reject. Unavailable Android colors use legible theme fallback, including light wallpaper colors with black button text.
- Use a slow/broken document provider while Home remains responsive. Change a setting during import: the delayed document must not overwrite it. Cancel/recreate while reading. Fail a config save and verify settings toggle rollback, retained setup/editor recovery, and no stale generic retry.
- Reach the contact scan bound and verify Partial rather than complete Indexed. Revoke contacts after opening a menu, then select call/text/contact actions: no protected intent may launch. Verify genuine empty results remain Ready(0).
- Verify real mail-app detection, no-handler copy fallback and failed clipboard access. Fail report-setting saves and report deletion: no false saved/copied/deleted message. Make widget-provider enumeration throw: report unavailable rather than empty.

These are new review-branch checks, not covered by the earlier main acceptance. Historical verification-only issues #20–#26, #29, #30, #32 and #33 were closed on David's explicit acceptance on 2026-10-05; that decision does not manufacture individual test results.


## #74/#75 indexer review gate (pending device execution)

Record build SHA, device/API, and result for each case. Do not mark source review or JVM tests as Android proof.

- Upgrade v7 settings with both search flags on: both indexing flags remain off, live search still returns contacts/files with current grants, and Home remains responsive. Import a v8 document with each search/index combination, then export/reload v9 and verify the wallpaper is represented by a stable source ID while the four search/index choices are unchanged.
- Regression #84: with the native library loaded, load a valid legacy v8 configuration, change a wallpaper or ordinary setting so Grove commits v9, recreate/restart Grove, and verify both v8 input and the resulting v9 save reload without fallback recovery. Malformed v8/v9 and unsupported v10 must still enter the normal rejection/recovery path without erasing the preserved raw configuration.
- Grant, deny, and revoke contacts and All files access while a query and background build are running. No protected row may remain visible or be committed after revocation; restoring a grant alone must not enable a Grove switch.
- Turn indexing off during a build and verify worker cancellation, private cache deletion and permitted live search; turn search off independently and verify no rows while explicitly enabled indexing may refresh. App search and Home must work throughout.
- Force unavailable, empty, corrupt, oversize, stale and partially scanned caches; force provider null/throw and a failed/low-storage atomic write. Check scoped status, retry/fallback, no false Indexed result, and isolation of the other source.
- Type quickly and leave Search during a live 15,000-file scan. Verify bounded time, cancellation, a truthful partial label, no main-thread stall, and no old query publication. Repeat after process kill/reboot, low battery, storage pressure, and contact observer registration failure.
- Inspect app-private cache contents and privacy text: only contact name/lookup ID and file name/path/type/coverage, no phone numbers or file contents. Verify backup/device-transfer exclusion, deletion on indexing disable/revocation, and no contact/file data in logs.

**Scope note (2026-10-05):** Validation-only issues #2, #5, #6, #14, #34 and #35 were closed at the user's request. Their unrun device and performance rows below remain a checklist, not passing evidence. The current test APK is a debug `.test` package from source identical to main at `7015420`; a signed production install/update remains #36.

[GROVE-STATUS.md](GROVE-STATUS.md) is the target behavior, with a [current-versus-proposed matrix](GROVE-STATUS.md#implementation-status-on-main). Do not mark proposal-only cases as failures of the current release without first implementing their linked issues. The 2026-10-04 user acceptance of some integrated Grove Test conditions closed #17 by a scope decision; it was not the full dated Android 12/current-device matrix below.

## #21/#22/#23 recovery, catalogue and search gate (pending device execution)

Source CI on the review branch proves the deterministic state machines, cancellation gates, app/icon fallback behavior, bounded/partial file-state mapping, and build/lint/unit integration. Device evidence is still required for Android framework behavior.

- Force LauncherApps/service enumeration failure while Grove is the default Home. Verify the minimal recovery surface appears with Retry and Android Home settings, no crash loop occurs, and a successful Retry restores the app list. Restart the process while failed and after recovery.
- Force every app icon decode to fail while enumeration succeeds. Verify every app remains launchable/searchable with fallback imagery and that Home/drawer do not enter core recovery.
- Install, update/change, and remove one package. Verify only the changed package's icon is invalidated/reloaded where applicable, stale icon batches do not overwrite the refreshed generation, and drawer/pin behavior is unchanged.
- Change display density/icon size or otherwise trigger a new icon size. Verify the prior-size cache is discarded and no stale-size icons remain.
- Search while contacts/files are loading, then revoke permission, disable the Grove source, leave Search, and rapidly issue a second query. Verify old results disappear and no stale generation publishes afterward.
- Force contact provider failure then recovery; force unreadable root and unreadable child directories; verify Failed versus Partial versus Ready(0) remain visibly distinct and Retry affects only that source.
- Exercise a live file query that hits the 15,000-file/2.5-second bound. Verify the UI reports Partial rather than Ready/full coverage, remains responsive, and app/contact results remain usable.
- Verify app search distinguishes Loading, Ready/empty, Degraded icons, and Failed catalogue rather than presenting all of them as an empty result.

## Refactor verification record (2026-10-04)

The stacked refactor has CI source checks, but no Android device or emulator was available in this workspace. The cases below are pending, not passed. For every execution record date, device/model, API level, build commit SHA, clean install or upgrade, exact steps, observed outcome and linked defect. Run on Android 12 and current Android. Keep signed APK update results separate from debug APK results.

| Date | Device / API | Build SHA | Scenario | Outcome / issue |
| --- | --- | --- | --- | --- |
| Pending | Android 12 | Pending | Default Home cold/warm, reboot and recovery | Not run (#34) |
| Pending | Current Android | Pending | Permission revoke/provider failure, widgets, gestures, wallpaper | Not run (#34) |
| Pending | 16 KB page device/emulator | Pending | Signed APK/AAB native load and update | Not run (#33/#36) |
| Pending | Same device before/after | Pending | Home first draw and 15,000-file search latency/allocations | Not run (#35) |

For the latest stacked build, inject a malformed preference type and a failing config commit at cold start. Verify the minimal recovery surface offers Retry and Android Home settings, preserves the saved value, and does not crash-loop as default Home. Fail widget metadata restore independently and verify apps still load. Force a save failure while pinning, renaming a folder, finishing setup, and changing launcher settings; verify no unsaved in-memory state or success message appears. Then retry with writable storage and restart to check persistence. Exercise drawer tile hold/drag/drop and pinned tile hold/drag/edge scroll with a widget under the gesture path. Record the build SHA for each result.

See [FAILURE-VERIFICATION.md](FAILURE-VERIFICATION.md) for the capability-by-capability failure cases. CI passing is required but does not fill any row in this table.


## Grove Status implementation verification (pending device execution)

| Design requirement | Issue | Required evidence |
| --- | --- | --- |
| Search works live with indexing off, and a denied/revoked source reveals no stale results | [#74](https://github.com/davidcit646/Grove/issues/74) | Clean/upgrade config migration, provider failure, cancellation and query/action permission checks on device |
| Independent, rebuildable contact/file caches with eight states | [#75](https://github.com/davidcit646/Grove/issues/75) | Cache missing/stale/corrupt, process death, revocation, partial scan, size/retention and privacy checks |
| Packaged/user/solid-black wallpaper library | [#82](https://github.com/davidcit646/Grove/issues/82) (incorporates #76) | Offline built-ins and credits, stable-ID migration, cancel/invalid/oversized input, Home/Lock/Both, apply/config-sync failure, prior-wallpaper preservation, rotation/process recreation |
| Android wallpaper reconciliation and explicit theme choices | [#77](https://github.com/davidcit646/Grove/issues/77) | External wallpaper and light/dark changes, Home/Lock/Both, restart and failed preference commit |
| Severity/code error workflow and safe report draft | [#78](https://github.com/davidcit646/Grove/issues/78) | Each severity, no mail handler, chooser cancellation, report redaction/retention, no automatic send |

PR #83 implements #27, #74/#75, #78, #81 and #82 (including #76) in source with deterministic tests; production/test head `13aa0c10ac635e216fe555484bc2807d3a98bb2a` passed Android CI run #567; the rows above remain Android evidence gaps, not implementation gaps. Record device/API, build SHA, steps and outcome. The closed [#34](https://github.com/davidcit646/Grove/issues/34) checklist records unrun device work; [#32](https://github.com/davidcit646/Grove/issues/32) remains the broader failure-injection gate.

## Wallpaper picker (#82 review branch)

- Browse every packaged curated image with networking disabled. Verify preview/apply works offline and each item exposes readable author, source and license metadata; verify generated art and the separate true `#000000` option.
- On short screens, landscape, gesture-navigation insets, large font/display size and long attribution text, verify preview/details can scroll without hiding Previous/Next, Retry, credits, destination controls or the custom-image entry.
- Choose an image through Android's picker/document flow; cancel, select unsupported/corrupt/oversized content, revoke/lose access, rotate/recreate the process, and supersede one pending choice with another. None may replace committed state until validation and the required apply path succeed.
- Apply packaged/generated/custom/black sources to Home, Lock and Both. Verify Android apply failure preserves the prior Grove choice; lock-only leaves Grove Home selection unchanged; successful Android Home apply followed by Config failure reports the split state and can retry persistence without reapplying Android.
- Inspect committed/staged private custom-wallpaper files across success, cancellation, replacement and failure. Verify only confirmed custom content is promoted, old committed content remains available through failed attempts, and uninstall removes app-private copies.

## First-run setup (0.1.29)

- Clear app data, launch Grove, and verify the eight-step setup appears over Home with visible Back, Next, and Skip controls and readable light-mode status icons.
- Turn both swipe gestures off and verify Next skips swipe practice, the count becomes seven steps, and Back returns to gesture selection. Enable only one gesture and verify practice shows only that gesture.
- Check the new cards, larger type, and navigation in light/dark mode, small screens, large font/display settings, and landscape. Permission details must remain readable and scrollable.
- Change both swipe switches, practice an upward and downward swipe, then hide each home control independently. Verify the choices persist after Finish and restart.
- Search for an installed app outside the first page of results, select pins, finish, and verify only the chosen apps appear on Home. Replay setup and check that the current choices are preselected.
- From Launcher settings → Tutorials, queue replay and then cancel it before leaving settings; verify `setup_complete` remains true and Home/configuration is unchanged. Queue again, return Home, rotate/recreate, and verify only one setup instance appears. On an existing installation with no favorites, Skip must not auto-seed apps; on a genuine fresh first run, Skip may retain the existing safe seed behavior.
- Skip contacts and file access and verify Grove still opens apps and search. Replay setup, grant contacts, deny it on another run, and confirm both paths return to setup.
- Open all-files settings, return without granting, then grant and return; verify the displayed state matches Android's actual setting.
- Finish setup and accept or decline Android's Home chooser. Confirm the previous launcher remains available and upgrades of an existing Grove install do not force setup.
- Test TalkBack, large text, rotation, keyboard navigation, and the Android Back action on every page.
- Turn Contact search and File search off separately in Launcher settings. Verify each source and its permission prompt vanish from Search, active scans stop, and cached results clear while Android still shows the permission as granted.
- Re-enable each source after permission was granted, verifying results return without another system prompt. Revoke permission in Android settings and confirm Grove asks again only if the source remains enabled.
- Import old version-6 settings and verify both search sources retain their previous enabled behavior. Export version-7 settings, disable both, reimport, and verify the disabled state survives.

Before calling this version stable, test on physical Android 12 and a current Android device:

- Select Grove as default Home, press Home from another app, reboot, and return home.
- Launch apps, pin/unpin them, rotate the device, and restart the process; favorites persist.
- Install/update/remove apps and verify the drawer refreshes.
- Search accented names and multiword names; test no results and keyboard dismissal.
- Add a widget that requires setup and one that does not. Cancel both binding and configuration and verify no visible orphan widget.
- Resize/remove widgets, rotate, restart, and uninstall a widget provider; recover through its Edit → Remove control.
- Import/export configuration, reject invalid JSON and unsupported schema versions, and preserve settings after rejection.
- Apply each wallpaper and test wallpaper permission or OEM failures.
- Test TalkBack, large fonts, landscape, gesture navigation, and three-button navigation.
- Verify no launcher crash loop; switch back to the original Home app through system settings.

Device behavior and visual layout have not yet been validated on hardware. Local build results are recorded in BUILD-STATUS.md.

## 0.1.3 interaction checks

- On Home, swipe down vertically and verify app search opens with keyboard focus when enabled.
- Disable Swipe down to search; repeat and verify Home remains unchanged.
- On Home, swipe up vertically and verify the app drawer opens when enabled.
- Disable Swipe up for app drawer; repeat and verify Home remains unchanged.
- Interact/scroll inside an Android widget and verify launcher gestures do not fire from a touch that starts inside the widget.
- Open Settings -> Launcher settings and verify all eight switches persist across launcher restart.
- Toggle Show Apps button, Show clock, and Show pinned apps independently and in all combinations.
- Tap the Home clock to open the phone's Clock alarms screen; disable "Tap clock to open Clock" in launcher settings and confirm the clock no longer opens it. Tap the date to open Calendar. If either app is missing, confirm Grove shows a short unavailable message.
- With all three Home visibility switches disabled, verify Search apps remains available; long-press empty home space opens the menu when enabled; any user-added widgets remain visible.
- Import a version-1 JSON configuration and verify the legacy home appearance remains visible and both new gestures default enabled.
- Export configuration and verify schema version 3 includes `gestures` and `homeScreen`.

### 0.1.3 additions

- Long-press several drawer and pinned apps; verify Uninstall opens Android's package-removal confirmation and canceling leaves the app installed.
- Reopen the drawer repeatedly after startup; verify icons do not visibly reload or flash placeholders.
- Verify single-tap launcher menu is off by default and tap-and-hold is on by default.
- Toggle each launcher-menu gesture independently and verify behavior.
- Hide Search, All apps, clock, and pinned apps; verify Home can be wallpaper/widgets only.
- Verify tapping/holding app tiles and widgets does not accidentally open the launcher context menu.
- Export configuration and verify schema version 3 contains the new gesture and showSearchButton keys.

- Disable every gesture and visibility toggle, reopen Grove through APPLICATION_PREFERENCES, and restore controls.
- Verify multi-touch, horizontal movements, taps on app tiles, and scrollable widget content do not trigger home gestures.
- Verify the APK installs, launches, and opens system uninstall confirmation in an emulator.

## 0.1.5 polish checks

- Swipe up/down on Home and verify the surface gives movement feedback before transitioning.
- Open the app drawer at its first row, drag downward, and verify the drawer follows the finger and closes after a deliberate swipe.
- Partially pull the drawer down and release before the threshold; verify it settles back into place.
- Scroll the drawer down, then attempt the same downward gesture; verify it scrolls normally and does not close until returned to the top.
- Hold a pinned icon until it lifts. Drag to earlier, adjacent, and final slots, release, and verify the order persists after restart.
- Hold then release without moving: app options appear, including Uninstall. A quick tap still launches the app.
- Drop outside the pins, interrupt with Home, rotate, and test multi-touch; verify no accidental launch or reorder.
- Drag over enough rows to test scrolling at the edges. Verify taps and scrolling inside widgets still work.
- Verify no Arrange button or dialog remains.
- Open/close the drawer and search in light and dark mode; verify content slides without fading or white flashes. Test with system animator duration disabled.
- Toggle Launcher settings -> Home screen -> Pinned apps at bottom. Enabled should place pins after widgets and above All apps; disabled should place pins before widgets, directly below clock/search controls.
- Export configuration and verify schema version 4 contains `pinnedAppsAtBottom`.
- Import version 1, 2, and 3 configs and verify pinned apps default to the legacy bottom placement.


## PR #90 onboarding implementation — October 5, 2026

Production/test source `026be7f0314c355f0449fe23796a557ee0417e22` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37399253894): seven Rust tests, debug APK assembly, JVM tests, lint, and the missing-signing negative gate. This is source/build verification; PR #90 remains unmerged and its Android device acceptance is pending. GROVE-STATUS.md is unchanged. Issue #89 explicitly requests fresh-install indexing defaults and Settings-only indexing controls; this overrides the older onboarding indexing-choice description without editing that contract.

- Fresh install: indexing preferences start on, search sources start off, and startup has no indexing toggle. Without grants/search enablement, neither worker reads protected data. Enable either source and grant access; Finish activates only after saving.
- Existing-install replay and legacy import: retain explicit indexing opt-outs and existing pins/gestures/search choices. Replay Skip leaves existing config unchanged.
- Contacts/files show one-sentence purpose, a switch and accurate access status. Enabling requests Android directly, without the previous explanatory dialog. Denial, cancellation or a failed settings intent leaves Home reachable.
- Disable search with permission still granted: affected observer/work/cache stop and clear. Re-enable: saved indexing preference is honored. Disable indexing in Settings: cache clears and permitted live search remains usable. Revoke access during pending work: no later cache/result publication.
- Verify dynamic-color and fallback themes, light/dark, Material Symbols, original installed-app icons, TalkBack, large fonts, short screens and landscape.
- Observe initial foreground alpha 0→1 over the stationary fresh Fern backdrop. Replay preserves Android wallpaper; optional artwork failure uses the palette fallback. Home remains undimmed after exit.
- Next/Back slide in opposite directions; RTL reverses them. Rapid navigation cannot overlap or skip pages; animations disabled shows usable final state immediately.
- Rotate/recreate, background/return and leave for Android permissions during motion. Current page, provisional choices/pins and practice restore; alpha is 1 and translation is 0. Permission redraws do not replay startup fade. Fail Finish persistence: retain final setup for retry.

These PR #90 Android cases remain pending until David's device acceptance; automated coverage does not claim actual device/OEM outcomes.

## Juniper acceptance — PR #92 (not device-run)

Production/test source `994df71d741b67dd65ed690840bd67b48cef1cdb` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37404896738): eight Rust tests, 163 JVM tests, debug APK assembly, Android lint and the missing-signing negative gate. Signed release steps were skipped. PR #92 is stacked on unmerged PR #90; Juniper device acceptance remains pending. GROVE-STATUS.md is unchanged.

- Open Launcher settings from Home and Android APPLICATION_PREFERENCES; navigate/back through all seven categories, rotate, and return Home. Verify page slides, animation-disabled behavior, RTL, light/dark/Wallpaper themes, system bars, small/landscape displays, large font, keyboard and TalkBack focus/labels. Settings must have an opaque readable background.
- Change every existing switch/theme and restart. Verify only the intended setting changed; pin/folder/wallpaper/widget state remains. Fail preference writes: UI rolls back, no saved claim and no active revision change. Recover and retry from retained UI.
- Set both grids to 1×1, 1×10, 10×1 and 10×10; traverse overflow pages, filter/clear the drawer, shrink from a last page, pin/unpin/reorder and restore Automatic. All items remain reachable, labels/touch targets readable and folder/selection actions correct. Dense grids may scroll; dimensions define page capacity, not forced viewport fit.
- Toggle contact/file search and indexing independently. Deny/cancel/grant/revoke access during query/indexing, return from system settings and verify accurate status. Index-off retains permitted live search; disable/revoke cancels work and clears cache without stale publication. Retry only schedules eligible sources; inject scheduling failure and confirm saved preferences with unavailable refresh.
- Edit/import invalid, oversized, malformed UTF-8 and future-schema documents; retain draft/recovery without changing active settings. Review a valid import, change settings in the other host, then Apply: require re-review. Exercise ABA changes, failed save, discard, explicit defaults replacement, provider delay/failure/cancel, export failure and concurrent navigation.
- Rotate during document work and grid/email editing: no duplicated launch/apply or lost retained draft. Kill/recreate process: editor draft requires explicit new review and a pending import must be reread; no automatic activation. Cancel a queued document before its worker starts: it must not read/apply/publish later.
- Queue/cancel tutorials under Help & diagnostics and return Home. Exactly one replay appears. Change theme/grid during setup then Finish: preserve unrelated changes; same-lane conflict restarts review; failed config/marker write keeps retry truthful. Existing replay Skip preserves configuration.
- Add/bind/configure/cancel widgets through Home settings, edit folders from App drawer, and apply Home/Lock/Both wallpaper through Appearance. Verify the existing owner gets the action exactly once, no leaked widget IDs, retained wallpaper split-state recovery, and unchanged drag/selection behavior. Test cold settings entry and return Home.
- Change reporting/email, force write/delete/copy/mail-handler failures, and verify truthful messages and retained reports. About version/privacy/license/artwork links and offline credits remain accessible. Reports/email/replay/widget IDs must not enter portable configuration exports.

## Juniper cleanup recheck (pending)

Cleanup source `a8f1ecdc5be3cd51b685425f6f4c896bd91d67bc` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37407249555): eight Rust tests, JVM tests, debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. The user confirmed the original Juniper build loads and core workflows work, and supplied a recording showing repeated contact-index status transitions. The cleanup still requires device revalidation; no merge occurred.

- Keep Search settings open for one minute with contact/file access allowed. Saved-index status should remain separate from background refresh; no rapidly replacing active scans. Test contact-sync bursts while a scan runs and immediately after it finishes: only one queued/deferred follow-up, bounded by the 30-second provider-event window. Newly changed contacts must appear after refresh.
- Refresh manually while idle and while queued/running; idle refresh should be immediate and active refresh should coalesce. Force provider failure/retry exhaustion; failed work remains failed, no permanently blocked successor. Disable/revoke during pending follow-up: no protected read/cache publication, no resumed canceled job.
- Inspect category/destination icons and both Search cards in light/dark/Wallpaper theme, large font, small screen, RTL and TalkBack. All existing switches/access/refresh actions retain their behavior.
- IndexRefreshRequestsTest covers notification storms before/during scanning, one deferred successor, sequential-event throttling, manual retry timing, clock reversal and failed scheduling/marker acceptance.

## Contact-path fixes #93–#102 — October 6, 2026

Production/test source `6cafc09247cbc3f96eb1a21a1ebc4bfb025bd130` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37409473807): eight Rust tests, the JVM suite (173 test methods), debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. Source trees locally and on the pushed branch match. PR #92 remains draft, stacked on unmerged PR #90; the new device acceptance is pending. GROVE-STATUS.md is unchanged.

The current owner/caller/consumer and failure inventory is [CONTACT-REVIEW.md](CONTACT-REVIEW.md). Package changes no longer request contact indexing; ContactChanges owns process observation, IndexWork owns scheduling/recovery/repair, IndexCache owns confirmed cache publication and shared metadata, and the two UIs render those outcomes. The previous cleanup pass did not prove idle stability; this source fixes the subsequent full-audit findings.

Record build SHA, device/API, date and actual outcome; these are pending checks, not passing claims.

- #93/#94: leave Home and Settings Search idle with a fresh cache for at least two minutes. No recurring contact jobs or catalogue reloads from worker receiver/component events. Refresh files without triggering contacts; external install/update/remove still updates catalogue. Real contact edits request the provider-throttled source refresh.
- #95: simulate missing/terminal WorkManager work with a persisted token; Retry recovers. Force asynchronous enqueue failure; show unavailable and release only its own token. Cancel before Worker starts, kill/restart and retry; old work cannot commit after a newer reservation.
- #96: edit contacts during a running scan. Its successful cache is visible despite a delayed successor, but remains stale if it missed the change. Exactly one successor refreshes it. Force write failure, revoke access and supersede a load; no protected or stale-generation snapshot publishes.
- #97/#100: start with Settings only, grant contacts/files and return from Android settings. Eligible indexing starts without requiring Home. Revoke or disable during scanning, then return: cancel, clear cache and hide protected results. With contact indexing off and live search on, edit/delete contacts while a query is displayed: query refreshes without an indexing job. Recreate hosts; only one process observer remains. Force observer registration failure and verify visible scoped status.
- #98: retain a corrupt/oversize/unsupported-version cache and force unsuccessful repair repeatedly, including restart. At most one automatic repair cycle occurs until a successful commit or manual Retry; permitted live search remains available and the other source stays usable.
- #99: merge/reaggregate contacts so cached numeric IDs change. Menus still resolve the same lookup identity. Delete/change the contact after menu opening and before call/text/channel/view/edit: reject changed/deleted details and request reopening. Revocation at action time blocks protected launch. Verify genuine WhatsApp/Business/Messenger channels and number deduplication.
- #101: use empty, valid, partial, stale, future-dated and corrupt caches. Settings and Search agree on validity, coverage and current TTL; touching file mtime cannot make invalid JSON available. Keep a screen open across TTL expiry and query again; readiness reevaluates without a JSON reload.
- #102: use a slow provider and many invalid rows. Live deadline/raw-row limits yield Partial; a genuinely empty provider yields Ready(0); typing, leaving Search, revoke and cancel cannot publish old rows. Record cancellation behavior for the actual OEM provider; ignored CancellationSignal is a platform limitation requiring evidence, not a passing deadline claim.

## Grouped settings cards — October 6, 2026

Source `42984548fd4c6211bde60df7871acd20a421c9fb` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37456480396): Rust tests, the JVM suite, debug APK assembly, lint and the missing-signing gate. Signed release steps were skipped. This is source/build verification; visual device acceptance is pending. GROVE-STATUS.md is unchanged.

`SettingsGroups` owns only Material card surfaces, spacing and accessibility headings. `SettingsPages` and `SettingsDocumentPages` place their existing controls inside these groups; SettingsCommands, the shared repository and platform/feature owners retain all actions and settings authority. Search uses the same helper. Home groups are Buttons, Clock and date, Pinned apps, Gestures, Widgets and Default launcher; Drawer, Appearance, Configuration, Help, About and artwork credits also use labeled cards. No setting keys, navigation routes, platform actions or command calls were removed.

Pending device check: inspect every grouped page in light/dark and dynamic colors, large text and RTL. Cards and headings remain distinct; switches/rows remain reachable through scrolling and independently focusable. Toggle each Home control, navigate both grids, open widget/default-launcher/folder/wallpaper actions, change theme, replay tutorial and use configuration/report workflows. Verify failed saves still roll back the original toggle and no card intercepts child actions.

## Widget entry polish — October 6, 2026

Source `3bc0b75f4a6f897886996c54bce4055b3c2f2853` passed [Android CI](https://github.com/davidcit646/Grove/actions/runs/37457299182): Rust/JVM tests, debug APK assembly, lint and missing-signing gate. Signed release steps were skipped. Widgets is the first card in Home screen settings; the Widget controls acknowledgment has only Got It! (Not now removed). Acknowledgment persistence, provider picker cancellation, bind/configure and interrupted-setup recovery are unchanged. GROVE-STATUS.md is unchanged.

Pending device check: open Home screen settings and reach Add widget without scrolling. With tutorial acknowledgment unset, verify only Got It! is shown and a confirmed acknowledgment opens the picker; failed acknowledgment persistence reports failure. Add/configure a widget and verify picker/Android cancellation and pending-widget Retry/Remove still work.


### Settings scroll preservation — 2026-10-06

Source `f1666e7fd338b3ca89a3f4191b61bbb3a495ff3c` passed CI run 37459613835: Rust tests, JVM suite (175 test methods), debug APK assembly, lint and missing-signing negative gate. Signed release steps were skipped. `SettingsScrollStateTest` covers independent routes, refresh/Back positions, known-route recreation, negative clamping and snapshot independence.

Device checks pending: scroll down and toggle several options; verify the same position remains after repository refresh, returning from a child page, rotation and external permission/settings return. Rapid changes before layout must not replace a remembered position with zero. Shorter content should clamp normally.

Debug Grove Test APK: `Grove-Juniper-scroll-f1666e7.apk`, SHA-256 `df4c2ba1643721541630d33db6e51e093b9a4924489db9dfecaaa883d5dde00c`. Verified GitHub archive digest, ZIP integrity, manifest and all three Rust ABIs.
