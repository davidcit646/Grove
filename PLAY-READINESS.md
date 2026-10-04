# Google Play readiness

This is a development checklist, not a claim of Google Play approval.

- The Android app targets API 36 and builds arm64, ARMv7 and x86-64 native code. Draft refactor CI checks source; signed artifact and device validation remain pending.
- The first-run flow asks separately for contact search and all-files search. Neither permission is required to use the launcher. The user chooses Grove as the Home app through Android's system role request.
- `MANAGE_EXTERNAL_STORAGE` remains in the manifest. Google Play requires a declaration and approval for this permission, and permits on-device search only when searching files across external storage is the app's core purpose. Grove is primarily a launcher, so its optional file search may fail this standard. Before a Play submission, either obtain Play's approval with an accurate core-use declaration or replace broad access with a narrower design such as user-selected folders through the Storage Access Framework. Do not submit a claim that approval is guaranteed.
- Review and publish [PRIVACY.md](PRIVACY.md) under the developer's identity and link its public URL in the Play listing. The app's menu opens this policy. Complete an accurate Data safety form describing contact lookups, file-name indexing, locally saved crash reports, wallpaper downloads, and any sharing through Android intents. Verify the public URL after pushing; the local draft alone is insufficient.
- Configure `GROVE_SIGNING_PASSWORD` for the tracked release key. Main CI fails if it cannot build and verify a signed APK and App Bundle. The debug APK is for testing and cannot update an installation signed with a different key. Verify 16 KB page alignment and direct/Play install and update paths on devices before distribution.
- Test setup on fresh installs, upgrades, denied permissions, TalkBack, font scaling, rotation, and Android 12 through 16 before distribution.
