# Grove Launcher Privacy Policy

Last updated: October 8, 2026

Grove Launcher is developed by GraNet IT Solutions (CITARE HOLDINGS LLC). Questions about this policy can be sent to support@granet.tech or through https://granet.tech/.

## Local processing and permissions

Grove has no account, advertising, analytics, or automatic telemetry. Grove does not declare Android's Internet permission and does not make direct network requests. Its launcher settings and search indexes are stored on your device.

- Grove lists installed launchable apps to display the app drawer, search results, and pinned apps. Your layout and settings remain local.
- Contact search is optional and requires Android's Contacts permission. Grove reads names and lookup IDs for local search. When Contact indexing is enabled, contact IDs, lookup keys, and display names are stored in Grove's private cache. Phone numbers and messaging channels are read from Android when you choose a contact action; they are not cached in the search index. Bounded searches and indexes can be partial, and private cache metadata records coverage.
- File search is optional and requires Android's All files access, which grants broad access to shared storage. Grove searches file names and paths. File indexing stores names, paths, file types, coverage metadata, and timestamps in Grove's private cache. File search and indexing do not read file contents, modify your files, or upload an index. Opening a result grants the selected app read access to that file.
- Search and indexing have separate switches for each source. With indexing off, enabled search reads the permitted Android source live and can return partial results. Turning search off hides its results, cancels its indexer, and deletes its cache. Turning indexing off cancels indexing and deletes its cache. Revocation stops protected reads and hides protected results. Android permissions remain under your control in system settings.

## External actions and handoffs

When you explicitly choose an external action, Grove passes the requested information to another app or Android service using an Intent, URL, chooser, or clipboard. The receiving app or service may access the Internet and handles that information under its own privacy policy.

- Google Search, ChatGPT, Gemini, and Google Play actions pass your search text to the selected service or app.
- Copy places the selected search or diagnostic text on Android's clipboard. Share passes the selected text to the app you choose through Android's sharing interface.
- Contact actions can pass a selected phone number to a dialer or SMS app, open WhatsApp or WhatsApp Business with a selected number or contact channel, open a Messenger contact channel, or open/edit the Android contact card. These actions occur only when you select them.
- Opening a file passes its content URI and temporary read permission to your chosen app. Opening the Privacy Policy or project website uses an external app/browser.
- Reporting prepares diagnostic text for an email app or clipboard as described below. Grove never sends reports automatically.

These user-directed handoffs are separate from Grove's local search and indexing. You can use Home and ordinary app search without granting Contact search or File search access.

## Reports and diagnostics

Automatic local crash/error capture is **enabled by default** and can be disabled in Launcher settings. Explicit problem reports remain available when automatic capture is disabled.

Up to ten privacy-bounded reports are kept in Grove's private storage. Reports include Grove error codes, filtered stack information, and device/app version details. Diagnostics omit exception messages, contact data, file paths, imported configuration, and sensitive values.

Grove prepares an email draft for support@granet.tech by default; the recipient can be changed in settings. You review and send it through an app you choose, or copy the bounded diagnostic text when no mail handler is available. Opening the email composer does not prove delivery and does not delete the local report. You can delete pending reports in Launcher settings.

## Wallpaper and appearance

Android displays the applied Home wallpaper, including static and live wallpapers. Grove reads Android's wallpaper ID and color metadata locally to refresh optional button colors. It does not read or copy the system wallpaper image for Home rendering, upload it, or request additional storage access for appearance. System, Light, Dark, and Wallpaper colors preferences remain on-device. A remembered Grove wallpaper selection may differ from a wallpaper later changed in Android or another app.

Built-in wallpapers are bundled with the app and require no runtime download. Their source, author, and license information is available offline. When you explicitly choose a custom image through Android's picker, Grove reads that image's contents and validates and stages a bounded private copy for preview/apply. Canceled, invalid, unsupported, or oversized choices do not replace the committed wallpaper. This explicit image import is separate from file search and indexing, which do not read file contents.

## Retention and deletion

Local settings and committed custom-wallpaper copies remain until replaced/removed or Grove is uninstalled. Abandoned staged wallpaper data is not promoted to the committed slot. Contact/file indexes are separate app-private files, refreshed when stale (contacts after about 15 minutes, files after about one day), on relevant changes, or on retry. Disabling the source/indexing or revoking access blocks further protected publication and attempts to delete the affected cache. Grove reports persistence or deletion failures so deletion can be retried; it does not claim that an unsuccessful deletion completed. Uninstalling Grove removes app-private data.

Pending reports remain until you delete them, Grove removes older reports under its ten-report limit, or Grove is uninstalled. Copies you explicitly export, share, copy, or send are outside Grove's private storage; deleting a local item does not remove those external copies. For questions or deletion requests about reports sent to the developer, contact support@granet.tech. Grove does not sell personal data.

Android backup and device-to-device transfer of Grove's app data are disabled.

## Onboarding and defaults

New installations default the separate contact/file indexing preferences on, while both optional search sources remain off until chosen. No protected indexing runs unless the source's search is enabled and Android currently permits access. Existing saved choices, legacy imports, and recovery defaults are preserved. Permission denial does not reset the indexing preference.

Setup uses Android's local dynamic-color palette when available, with a theme fallback. Its fresh-install Fern background is generated locally and does not apply or upload a wallpaper. Tutorial replay preserves Android's existing wallpaper. Provisional setup answers and page/practice state can be retained in Android's local Activity saved-state bundle. Preferences activate only after successful Finish.

## Settings, configuration import, and export

Home and drawer grid choices and launcher preferences are stored in local configuration. Home and Settings belong to the same app. Provisional editor, grid, and email drafts can be retained in Android saved state; drafts do not activate settings.

Imported configuration requires review and explicit Apply. Importing does not grant Android permissions or apply wallpaper through Android. Export includes portable launcher preferences and layout, but excludes widget IDs, reports, device grants, and tutorial replay markers. Grove writes an export only to the destination you select.

This policy should be updated when Grove's data handling changes.
