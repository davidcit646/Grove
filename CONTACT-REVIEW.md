# Contact path review — Grove Launcher 0.1.24 (Sept 28, 2026)

Scope: `ContactIndex.kt`, `MainActivity.contactMenu` / `refreshContacts`, manifest,
permission flow. Read-only review; no behavior changed.

## How contact data flows today

**Getting it**
- `ContactIndex.load()` queries `ContactsContract.Contacts` (id, lookup key, display
  name) on a background single-thread executor. Runs on `onResume` when data is >15 min
  old, on package add/remove/change, and via a `ContentObserver` (400 ms debounce).
- `ContactIndex.details()` queries `ContactsContract.Data` per contact, on demand, when
  the contact menu opens: phone numbers + WhatsApp/Messenger channel rows, capped at
  80 rows.

**Storing it**
- Memory only. `MainActivity.contacts` holds id + lookup key + display name (+ a
  normalized search form). Phone numbers and channel rows are fetched on demand and
  dropped after the menu closes. Nothing contact-related is written to disk, Config,
  or logs intentionally. This is the right design — keep it that way.

**Already good (do not "fix")**
- `READ_CONTACTS` is requested on demand; denial yields an empty list, no crash.
- Dial/SMS go through `Intent.createChooser` — the user picks the app every time
  (Phone, Google Voice, Linphone…), no silent default routing.
- Channel intents grant `FLAG_GRANT_READ_URI_PERMISSION` on a single Data row —
  minimal scope.
- Truncation caps everywhere (512-char names, 128-char numbers, 256-char mimetypes,
  80 rows/contact, 50k contacts) — memory-exhaustion hardening.
- Lookup URIs (not raw ids) for view/edit intents — resilient to contact aggregation.

## Performance

- **P1 — Menu queries queue behind bulk reloads.** `contactMenu`'s `details()` runs on
  `contactWorker`, the same single thread as full `load()` refreshes. A large contact
  list refreshing in the background stalls menu opening. *Fix:* give on-demand
  `details()` its own executor (or a second thread).
- **P2 — No cache on `details()`.** Every menu open re-queries the provider, even for
  the same contact seconds apart. *Fix:* small LRU (e.g. 32 entries) keyed by contact
  id, invalidated by bumping a generation counter in the `ContentObserver`.
- **P3 — Observer-triggered reloads can hot-loop.** Any contact change (e.g. an active
  Google sync writing hundreds of rows) schedules a full reload after 400 ms, each of
  which re-renders search. *Fix:* coalesce — skip observer refreshes within ~30 s of
  the last completed load unless forced.
- **P4 (low) — Full reload every 15 min** rebuilds all normalized search names.
  Incremental refresh via `CONTACT_LAST_UPDATED_TIMESTAMP` is possible but not worth
  it unless P3 shows up in practice.

## Security

- **S1 — Log hygiene.** `Log.w("Grove", "Cannot read contact details", it)` attaches the
  throwable; on some devices its message can embed a contact URI / lookup key.
  *Fix:* log the exception class only, never the throwable carrying a URI.
- **S2 — No permission rationale.** `requestContactAccess()` fires the system dialog
  with no in-app explanation of why a launcher wants contacts. *Fix:* short rationale
  dialog first ("Grove searches your contacts from search; names stay on this device").
  Also a Play-data-safety plus.
- **S3 — wa.me number disclosure (inherent).** Opening `https://wa.me/<digits>` hands
  the number to WhatsApp/Meta even if the user backs out without sending. The new
  gating (only offered for contacts with real WhatsApp sync data) already limits this
  to genuine WhatsApp contacts; just don't loosen it later.
- **S4 — Keep storage memory-only.** If anyone proposes a disk cache of contacts for
  speed, it must be encrypted (or better: cache only id+name hashes, never numbers).

## Suggested order for Codex

1. S2 rationale dialog (user trust, cheap).
2. S1 log scrub (cheap).
3. P1 separate executor for `details()` (fixes real latency).
4. P2 LRU for `details()` + P3 refresh coalescing (only if P1/P2 leave visible jank).
