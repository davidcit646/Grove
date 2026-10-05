# Device acceptance checklist

## #74/#75 indexer review gate (pending device execution)

Record build SHA, device/API, and result for each case. Do not mark source review or JVM tests as Android proof.

- Upgrade v7 settings with both search flags on: both indexing flags remain off, live search still returns contacts/files with current grants, and Home remains responsive. Export/import v8, including the combinations where search is off but indexing is on.
- Regression #84: with the native library loaded, save a v8 configuration, change a wallpaper or ordinary setting, recreate/restart Grove, and verify the v8 configuration reloads without entering fallback recovery; malformed v8 and v9 must still enter the normal rejection/recovery path without erasing the preserved raw configuration.
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


## Grove Status proposal verification (pending implementation)

| Design requirement | Issue | Required evidence |
| --- | --- | --- |
| Search works live with indexing off, and a denied/revoked source reveals no stale results | [#74](https://github.com/davidcit646/Grove/issues/74) | Clean/upgrade config migration, provider failure, cancellation and query/action permission checks on device |
| Independent, rebuildable contact/file caches with eight states | [#75](https://github.com/davidcit646/Grove/issues/75) | Cache missing/stale/corrupt, process death, revocation, partial scan, size/retention and privacy checks |
| User photo/file and solid-black wallpaper | [#76](https://github.com/davidcit646/Grove/issues/76) | Cancel/invalid/oversized input, apply failure, prior-wallpaper preservation, rotation |
| Android wallpaper reconciliation and explicit theme choices | [#77](https://github.com/davidcit646/Grove/issues/77) | External wallpaper and light/dark changes, Home/Lock/Both, restart and failed preference commit |
| Severity/code error workflow and safe report draft | [#78](https://github.com/davidcit646/Grove/issues/78) | Each severity, no mail handler, chooser cancellation, report redaction/retention, no automatic send |

Record device/API, build SHA, steps and outcome when these features exist. The closed [#34](https://github.com/davidcit646/Grove/issues/34) checklist records unrun device work; [#32](https://github.com/davidcit646/Grove/issues/32) remains the failure-injection implementation gate.

## Wallpaper picker (0.1.28)

- On a clean install, browse all ten Commons images over a working connection; verify scaled photos that redirect to `thumb.wikimedia.org` show previews. Red, orange, blue, brown, and black and white previously failed.
- Disable connectivity and open an uncached image. Verify Retry, Previous, Next, credits, and X remain reachable; restore connectivity and retry.
- With large font and display size, confirm the preview/details scroll while Previous, Next, and credits stay visible.
- Check that landscape photos crop to the display instead of stretching; set a photo to Home, Lock, and Both, then confirm each destination.

## First-run setup (0.1.29)

- Clear app data, launch Grove, and verify the eight-step setup appears over Home with visible Back, Next, and Skip controls and readable light-mode status icons.
- Turn both swipe gestures off and verify Next skips swipe practice, the count becomes seven steps, and Back returns to gesture selection. Enable only one gesture and verify practice shows only that gesture.
- Check the new cards, larger type, and navigation in light/dark mode, small screens, large font/display settings, and landscape. Permission details must remain readable and scrollable.
- Change both swipe switches, practice an upward and downward swipe, then hide each home control independently. Verify the choices persist after Finish and restart.
- Search for an installed app outside the first page of results, select pins, finish, and verify only the chosen apps appear on Home. Replay setup and check that the current choices are preselected.
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
