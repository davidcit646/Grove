# Grove Google Play submission package

Issue: [#112](https://github.com/davidcit646/Grove/issues/112)  
Permission declaration: [#111](https://github.com/davidcit646/Grove/issues/111)  
Privacy reconciliation: [#110](https://github.com/davidcit646/Grove/issues/110)

This file is the canonical working copy for Grove's first Google Play submission. It records answers that can be established from the release and separates them from Play Console/account decisions that still require an explicit submission-time choice.

## Release candidate

| Field | Value |
| --- | --- |
| App | Grove Launcher |
| Package | `tech.granet.grove` |
| Release baseline | v1.0.0 |
| Version code | 32 |
| Target SDK | 36 |
| Artifact | Signed Android App Bundle (`.aab`) |
| License | Apache-2.0 |
| Project | https://github.com/davidcit646/Grove |
| Privacy policy | https://github.com/davidcit646/Grove/blob/main/PRIVACY.md |
| Support email | `support@granet.tech` — publicly listed on granet.tech |
| Developer website | https://granet.tech/ — live public site |

If a newer production build is submitted, re-audit this file against that exact artifact and use a monotonically higher version code.

## Main store listing

Google Play currently limits the app name to 30 characters, the short description to 80 characters, and the full description to 4,000 characters.

### App name

**Grove Launcher**

### Category

**Personalization**

### Short description

74 characters:

> Search-focused Android launcher with no ads, no tracking, and no nonsense.

### Full description

> Grove Launcher is a free, open-source, search-focused Android launcher built for people who want a fast Home experience without ads, tracking, sponsored clutter, or unnecessary nonsense.
>
> Search from one place across installed apps, Grove settings, supported Android settings, contacts you choose to enable, calculations, and files on your device. File Search is designed to locate files across user-visible shared storage when you explicitly enable it and grant Android's All files access permission.
>
> Grove keeps its search indexes and launcher configuration on your device. File indexing uses file metadata such as names, paths and types rather than reading file contents for search. Contact indexing stores names and lookup identifiers rather than phone numbers. Grove does not contain ads or analytics, and the app does not declare Android's Internet permission.
>
> Customize Home and the app drawer with pinned apps, folders, widgets, gestures, grid controls, themes, wallpaper-aware colors, packaged artwork, your own wallpaper, and light, dark or system appearance.
>
> Grove also includes local calculator results, searchable launcher settings, curated Android settings shortcuts, configuration import/export, local diagnostic reports, and first-run tutorials.
>
> Permissions are optional and tied to the features that need them. Grove remains usable as a launcher without Contact Search or File Search. Android controls whether those permissions are granted or revoked.
>
> Grove is free and open source under the Apache License 2.0.

The File Search wording must remain prominent while Grove requests `MANAGE_EXTERNAL_STORAGE`; it is part of the factual basis for #111.

## App content answers

### Ads

**Does the app contain ads? — No**

Grove contains no advertising SDK or sponsored placement.

### App access

**No restricted sign-in or account is required.**

Reviewer note:

> Grove does not require an account, subscription, organization login, or test credentials. Optional Contact Search and File Search require the corresponding Android permission or special access when the reviewer chooses to test those features. File Search review steps are provided in Grove's All files access declaration.

### Privacy policy

Use:

https://github.com/davidcit646/Grove/blob/main/PRIVACY.md

Before submission:

- complete #110;
- verify the public URL loads without authentication;
- verify the in-app Settings link on the exact release build;
- ensure the policy identifies Grove Launcher and the publishing entity;
- ensure the policy covers accessed data, use, external handoffs, retention/deletion, and a privacy contact mechanism.

### Target audience

**Product audience:** people who want a search-focused Android launcher with no ads, no tracking, and no unnecessary clutter.

**Initial Play target age groups:** **13–15, 16–17, and 18+**.

Grove is not child-directed and is not marketed as a children's app. The initial distribution is United States only, so the listing and creative assets should remain adult/general-audience productivity/personalization material rather than child-oriented artwork.

Do not enable Google's **Restrict Minor Access** control; Grove is not an adult-only app.

### Content rating

**Desired U.S. rating: ESRB Everyone (E).**

The final rating is assigned through Google Play's IARC questionnaire rather than manually chosen. Complete the questionnaire truthfully from the release behavior and record the assigned result. Based on the current content inventory, Grove is intended to qualify for the U.S. **Everyone** rating.

Current behavior to represent accurately:

- no app-authored violence;
- no app-authored sexual content;
- no gambling;
- no controlled-substance feature;
- no social network or anonymous chat;
- no in-app purchases;
- user-selected external app/browser handoffs exist.

Record the assigned IARC rating after completion.

## Data safety worksheet

Google Play defines data as collected when it is transmitted off the device to the developer or a third party. Access and processing that stay solely on-device do not count as collection. Some user-initiated transfers to third parties are exempt from the sharing declaration when the user reasonably expects the transfer.

The final form must be audited against the exact submitted AAB and #110.

### Data that remains on-device in Grove

| Data | Grove behavior | Play working answer |
| --- | --- | --- |
| Installed launchable apps | Queried locally for launcher/search | Not collected |
| Contact names / lookup IDs | Optional local search/index | Not collected |
| File names / paths / MIME/category / coverage | Optional local search/index | Not collected |
| Custom wallpaper image | Read only when explicitly selected; handled locally | Not collected |
| Launcher configuration | Stored locally | Not collected |
| Search query text | Used locally unless user explicitly chooses an external action | Not collected by Grove by default |

The release does not declare `android.permission.INTERNET`, so Grove itself has no ordinary direct network capability for silently uploading these local indexes.

### Diagnostics

Grove retains privacy-bounded reports locally. Automatic **local capture** is not automatic transmission.

When a user explicitly reviews and sends a diagnostic email to `support@granet.tech`, diagnostic information is transmitted off-device to the first-party developer. Treat that path conservatively in Data safety as optional first-party collection unless the final Play questionnaire wording clearly excludes it.

Working declaration:

- **App info and performance → Crash logs / Diagnostics**
- Collection: **Yes, optional**
- Purpose: **App functionality / developer support**
- Sharing: **No third-party sharing by Grove**
- Collection trigger: **Only when the user explicitly sends the reviewed diagnostic email**
- Automatic transmission: **No**

The final privacy policy must state the same behavior.

### User-directed external handoffs

Grove can, after explicit user action, hand selected information to another app/service, including browser searches, Google, ChatGPT, Gemini, Google Play, Android share targets, clipboard, dialer/SMS/messaging apps, an email app, or an app selected to open a file.

These are user-directed transfers and must be disclosed in the privacy policy. For the Play Data safety sharing field, apply Google's user-initiated-action exception only where the UI makes the destination/transfer reasonably expected.

## All files access

Owned by #111.

Play declaration use case:

**Search (on device)**

Do not claim that the entire launcher becomes unusable without the permission. The truthful statement is that Grove remains a launcher without the permission, while its advertised full-device File Search cannot operate as designed without broad visibility of user-visible shared storage.

Reviewer path:

1. Install Grove.
2. Complete or skip first-run setup.
3. Open Launcher settings → Search → Files.
4. Enable File Search.
5. Read Grove's disclosure.
6. Continue to Android's All files access screen and grant access.
7. Return to Grove.
8. Search for a neutral test file in user-visible shared storage.
9. Verify the file appears.
10. Disable/revoke access and verify protected results disappear.

No account credentials are required.

## Release and distribution

### Pricing

**Free**

### In-app purchases

**None**

### Ads

**None**

### Play App Signing

Record the Play App Signing state and upload/app-signing certificate information once the app is created in Play Console.

Do not assume the GitHub release-signing certificate and Play distribution certificate are identical after Play App Signing is configured.

### Countries and regions

**Initial distribution: United States only.**

Do not enable additional countries or regions for the first release unless the publisher explicitly expands the rollout after the initial Play review.

### Production-access/testing requirements

Treat the Play Console account as authoritative. If the developer account is shown an account-specific testing or production-access gate, complete exactly that gate and record it here. Do not infer that a gate is or is not required solely from general documentation.

## Store assets

### App icon

Required Play asset:

- 512 × 512 px;
- 32-bit PNG with alpha;
- no more than 1,024 KB.

The in-app application icon currently comes from `app/src/main/res/drawable/ic_grove.xml`. Prepare the Play icon from the approved Grove branding rather than inventing unrelated artwork.

### Feature graphic

Required:

- 1024 × 500 px;
- JPEG or 24-bit PNG;
- no alpha.

Direction: communicate the launcher + unified-search experience, not a generic logo-only banner.

### Phone screenshots

Prepare at least four strong screenshots even though Play can accept a smaller minimum.

Recommended sequence:

1. Home — wallpaper, clock/date, pinned apps.
2. Unified Search — apps plus Grove/Android settings.
3. File Search — neutral test filenames only.
4. App drawer / folders.
5. Widgets/customization.
6. Appearance/theme/wallpaper settings.

Do not include real contacts, personal filenames, private notifications, credentials, or misleading composited functionality.

## Reviewer notes

General reviewer note:

> Grove Launcher is a free, open-source launcher with no account, ads, analytics, or automatic telemetry. Contact Search and File Search are optional. Grove explains protected access before requesting it. File Search uses Android All files access only for local on-device file-name/path search and indexing. Grove does not declare Android's Internet permission. No reviewer credentials are required.

## Submission checklist

### Repository / release

- [ ] #110 privacy reconciliation complete.
- [ ] #111 All files access package complete.
- [x] Store-listing copy drafted and length-safe.
- [x] Ads answer drafted.
- [x] App-access answer drafted.
- [x] Data safety worksheet drafted.
- [x] Reviewer note drafted.
- [x] Content-rating behavior inventory drafted.
- [x] Screenshot plan drafted.
- [ ] Final AAB re-audited against this file.

### Publisher decisions

- [x] Product audience defined: search-focused launcher users who want no ads/tracking/clutter.
- [x] Play target age groups selected: 13–15, 16–17, 18+; not child-directed.
- [x] Initial distribution selected: United States only.
- [x] Desired U.S. content rating recorded: ESRB Everyone; final result remains IARC-assigned.
- [x] Public support email confirmed: `support@granet.tech`.
- [x] Public developer website confirmed: https://granet.tech/.
- [ ] Legal Play publisher/developer name confirmed against the actual Play Console account. The public website currently uses “GraNet IT Solutions LLC,” while Grove's privacy policy identifies GraNet IT Solutions under CITARE HOLDINGS LLC; align the public/legal naming used for submission rather than guessing.

### Visual assets

- [ ] 512 × 512 Play icon prepared.
- [ ] 1024 × 500 feature graphic prepared.
- [ ] Final screenshots captured from release UI.

### Play Console

- [ ] App created with package `tech.granet.grove`.
- [ ] Main store listing entered.
- [ ] Privacy policy entered.
- [ ] Ads declaration completed.
- [ ] App access completed.
- [ ] Target audience completed.
- [ ] IARC questionnaire completed.
- [ ] Data safety completed.
- [ ] All files access declaration submitted.
- [ ] Developer/account verification complete.
- [ ] Countries/regions and pricing configured.
- [ ] Signed AAB uploaded.
- [ ] Play App Signing information recorded.
- [ ] Automated/pre-launch reports reviewed.
- [ ] Production/testing access gate completed if Play Console requires one.
- [ ] Release submitted for review.
- [ ] Exact Google review response recorded in #112.

## Change rule

Any release change affecting permissions, data access, external handoffs, diagnostics, SDKs, networking, accounts, ads, purchases, or File Search requires this package, #110/#111 as applicable, and the Play declarations to be re-audited before publishing.
