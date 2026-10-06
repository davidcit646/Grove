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
`EE:C2:C5:AB:1F:F5:23:11:2A:52:E5:83:0D:5B:50:16:9B:7F:74:75:B1:7D:AA:5B:74:22:C0:F4:97:14:7F:F3`.

The previous 0.1.29-alpha release was signed with an ephemeral debug key.
Installing the first build with this key requires exporting settings and
reinstalling once. Subsequent builds with a higher versionCode and this key
will update in place. The exported configuration does not include widgets.

The upload/local release certificate below may differ from Play App Signing's
app-signing certificate. Verify clean install and in-place update on a device
before distribution. A release run cannot substitute for those device checks.

## User-authorized replacement — October 6, 2026

The original key password was unavailable, and the user explicitly authorized replacing the key. This is a new RSA-4096 release key (PKCS12, alias grove), protected by the user-selected password. Set GROVE_SIGNING_PASSWORD to that exact password; its plaintext must stay outside Git and build logs. Previous-key signed installations cannot update directly to this key: export configuration, uninstall/reinstall, then import. Widget bindings need separate reconfiguration. This is replacement, not Android signing-certificate rotation or recovery of the old key.

Source `d8ad0c95b03c02b30edb2f971d0b2e970f2221d5` passed [CI](https://github.com/davidcit646/Grove/actions/runs/37487799875): Rust/JVM tests, debug assembly, lint and missing-signing gate. Local checks confirmed that the exact selected 512-character password decrypts the private key, RSA signing/verifying succeeds, a wrong password is rejected and the verification/provenance certificate pins agree. This is not signed Android artifact proof: APK/AAB certificate and alignment checks still require a main build with the Actions secret set. GROVE-STATUS.md is unchanged.
