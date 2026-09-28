# Uploading Grove from Android

1. Extract the source ZIP on your phone.
2. Open your GitHub repository.
3. Upload the **contents of the `grove-launcher` folder** to the repository root. Do not upload the ZIP itself as the project source.
4. Make sure these are visible at the repository root: `app/`, `gradle/`, `gradlew`, `settings.gradle.kts`, and `.github/`.
5. Commit the upload to `main`.
6. Open **Actions -> Android build**.
7. When the run is green, open it and download the **grove-debug-apk** artifact.

The workflow runs `assembleDebug`, unit tests, and Android lint before publishing the APK artifact.
