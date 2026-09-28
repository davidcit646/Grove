# Google Play readiness

This is a development checklist, not a claim of Google Play approval.

- The Android app targets API 36 and builds arm64 native code. Build and device validation are pending for this source revision.
- The first-run flow asks separately for contact search and all-files search. Neither permission is required to use the launcher. The user chooses Grove as the Home app through Android's system role request.
- `MANAGE_EXTERNAL_STORAGE` remains in the manifest. Google Play requires a declaration and approval for this permission, and permits on-device search only when searching files across external storage is the app's core purpose. Grove is primarily a launcher, so its optional file search may fail this standard. Before a Play submission, either obtain Play's approval with an accurate core-use declaration or replace broad access with a narrower design such as user-selected folders through the Storage Access Framework. Do not submit a claim that approval is guaranteed.
- Review and publish [PRIVACY.md](PRIVACY.md) under the developer's identity and link its public URL in the Play listing. The app's menu opens this policy. Complete an accurate Data safety form describing contact lookups, file-name indexing, locally saved crash reports, wallpaper downloads, and any sharing through Android intents. Verify the public URL after pushing; the local draft alone is insufficient.
- Use a stable release signing key and Play App Bundle for publication. The GitHub debug APK uses development signing and is intended for testing.
- Test setup on fresh installs, upgrades, denied permissions, TalkBack, font scaling, rotation, and Android 12 through 16 before distribution.
