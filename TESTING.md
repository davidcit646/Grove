# Device acceptance checklist

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
