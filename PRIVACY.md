# Grove Launcher Privacy Policy

Last updated: October 5, 2026

Grove Launcher is developed by GraNet IT Solutions (CITARE HOLDINGS LLC). Questions about this policy can be sent through https://granet.tech/.

## Information Grove uses on your device

- Grove lists installed launchable apps to display its app drawer, search results, and pinned apps. Your layout and settings are stored locally on your device.
- If you enable Contact search and grant Android's Contacts permission, Grove reads names and lookup IDs from Android's Contacts Provider for local search. If you separately enable Contact indexing, those names and IDs are stored in Grove's private on-device cache to speed search; phone numbers and messaging channels are read from Android only when you choose a contact action. Grove does not upload a contact copy.
- If you enable File search and grant Android's All files access, Android grants broad access to shared storage. Grove searches names and paths; if you separately enable File indexing, those names and paths plus file type and scan coverage are stored in Grove's private on-device cache. Grove does not read file contents, change files, or upload the index. Opening a result grants the selected app access to that one file.
- Search and indexing have separate switches for each source. Search with indexing off reads the permitted Android source live; bounded file searches can be partial. Turning search off hides that source's results. Turning indexing off cancels that indexer and deletes its cache; Android permission remains until you revoke it in system settings. Revocation stops protected reads and hides existing results.

## Reports, network requests, and retention

- Grove does not have an account, ads, analytics, or automatic telemetry. If automatic crash capture is enabled, up to ten privacy-bounded reports containing Grove error codes and diagnostic details such as filtered stack information and device/app version information are kept in Grove's private storage. Explicit problem reports remain user-directed even when automatic capture is disabled. Grove prepares a draft for `support@granet.tech`; you review and send it through an app you choose, or copy the bounded diagnostic text when no mail handler is available. Grove does not automatically transmit reports. You can delete pending reports in Launcher settings.
- Grove's curated built-in wallpapers are packaged in the app and require no runtime network request. Their source, author, and license metadata remain available offline. If you choose a photo or image file through Android's picker, Grove validates and stages a bounded private copy for preview/apply; canceled, invalid, unsupported, or oversized choices do not replace the committed wallpaper.
- Choosing Google or Play Store search opens the selected external service or app with your search text. Those services then handle the query under their own policies.
- Local settings and committed custom-wallpaper copies remain until you replace/remove them or uninstall Grove; abandoned staged custom-wallpaper data is not promoted to the committed slot. Enabled contact/file indexes are stored separately in app-private files and refreshed when stale (contacts after about 15 minutes, files after about one day), on relevant change events, or on retry. Disabling indexing or revoking access deletes the affected cache; uninstalling Grove removes both. Android backup and device transfer of Grove's app data are disabled. Pending reports are retained locally until you delete them, Grove removes older reports under its ten-report limit, or you uninstall Grove. Opening the email composer does not prove a message was sent and does not delete those reports.

Grove does not sell your personal data. Android permissions remain under your control in system settings. This policy should be updated if Grove's behavior changes.
