# Grove signing key

`grove-release.p12` is the password-protected, long-lived Grove release key.
Its password is **not** in this repository. Keep the password backed up securely.
Never commit the password or print it in CI logs. Publishing the password would
allow anyone to ship an APK that Android accepts as Grove.

Set the repository Actions secret `GROVE_SIGNING_PASSWORD` to the saved password.
The Android workflow builds and uploads `grove-signed-release` (APK, App Bundle,
checksums, and provenance) on each push to main. A missing secret fails that job.
The manual `publish-verified.yml` workflow requires an existing tag and exact commit
SHA; it builds, tests, signs, verifies, and publishes from that same checkout.
Only distribute a verified signed APK to users. The debug APK is signed
with a different temporary key on each runner and cannot update an installed
release APK.

The signing alias is `grove`. The certificate SHA-256 fingerprint is
`6E:34:97:7D:69:ED:45:4A:5B:8A:C6:B2:34:CB:2D:24:03:90:3B:33:ED:19:A6:BE:1C:5A:BD:04:4F:AE:63:97`.

The previous 0.1.29-alpha release was signed with an ephemeral debug key.
Installing the first build with this key requires exporting settings and
reinstalling once. Subsequent builds with a higher versionCode and this key
will update in place. The exported configuration does not include widgets.

The upload/local release certificate below may differ from Play App Signing's
app-signing certificate. Verify clean install and in-place update on a device
before distribution. A release run cannot substitute for those device checks.
