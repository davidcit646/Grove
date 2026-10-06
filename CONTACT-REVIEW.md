# Grove contact search review

Current implementation: PR #92, production/test source `6cafc09247cbc3f96eb1a21a1ebc4bfb025bd130`. [Android CI](https://github.com/davidcit646/Grove/actions/runs/37409473807) passed eight Rust tests, the JVM suite (173 test methods), debug APK assembly, lint and the missing-signing negative gate. Signed release steps were skipped. Device acceptance remains pending. This replaces the older memory-only/MainActivity review; source names below refer to current function bodies, not historical line numbers. GROVE-STATUS.md is unchanged.

## Ownership and calls

| Owner | Authoritative input | Calls and consumers | Failure boundary |
| --- | --- | --- | --- |
| SettingsRepository in GroveApp | Confirmed persisted Config | SettingsCommands and ConfigController publish revisioned settings; IndexWork and ContactChanges read the shared snapshot | Failed persistence cannot change active settings; Android grants remain separate truth |
| ContactChanges in GroveApp | Contact search enabled + current READ_CONTACTS grant | Main/Settings reconcile its single application-context observer; provider events invalidate live queries and cache freshness; eligible indexing requests PROVIDER_CHANGE | No Activity retained; indexing off does not disable permitted live invalidation; observer failure is visible in Settings |
| IndexWork / IndexRecoveryPolicy | Current settings/grant + per-source persisted token + actual WorkManager state | Typed MANUAL/PROVIDER_CHANGE/STALE_CACHE/REPAIR requests; serial off-thread reconciliation; Worker begin/finished; Operation.result completion | Missing/terminal work releases only its matching reservation; enqueue failure is scoped; active work coalesces to at most one follow-up |
| IndexWorker / ContactIndex.load | Permission/settings/token checked throughout scan | Contacts Provider aggregate rows → bounded Contact records → atomic IndexCache commit | Canceled/superseded work cannot commit; retry is bounded; provider null/throw is unavailable rather than empty |
| IndexCache / IndexMetadata | Validated private JSON or confirmed atomic write | Cache generations feed SearchSources independently of next job ID; metadata feeds both UIs | UNKNOWN/ABSENT/CORRUPT/AVAILABLE, partial coverage and freshness stay explicit; metadata access has a separate lock from snapshot I/O |
| SearchSources | Eligible committed cache generation | One worker load per generation → prepared immutable search snapshot → redraw | Current load generation, cache generation, settings/grant and host lifetime gate UI publication; invalid cache retains permitted live fallback |
| SearchController | Current query generation + eligible cache/live source | Debounce → existing fuzzy ranking → SearchSourceState → SearchScreen | Live scan has 2.5-second cancellation deadline and 50,000 raw-row budget; exhausted scan is Partial, valid empty is Ready(0), external cancellation cannot publish |
| ContactIndex.details / ContactActions | Cached lookup URI resolved to current contact identity + current grant/source/package | Current Data rows → menu → fresh detail comparison → dial/message/channel/view/edit intent | Deleted or changed contact requires reopening menu; permission is checked again at launch; details/actions run off the UI thread |
| LauncherPackageEvents / catalogue | External package/component events | Reload application catalogue only; ignore Grove's own component events | No contact-index dependency; WorkManager receiver changes cannot request contact work or catalogue reload |

## Fixed findings

| Issue | Correction |
| --- | --- |
| #93 | Removed generic package callbacks from contact scheduling and filtered own-package catalogue events |
| #94 | Required explicit refresh causes; only provider changes inherit the provider delay |
| #95 | Reconciled orphan reservations against WorkManager and observed asynchronous enqueue completion |
| #96 | Published actual cache commits independently of a queued successor's job ID; rejected superseded snapshot reads |
| #97 | Reconciled both sources after Settings permission results and system return/resume |
| #98 | Persisted one automatic repair attempt; success/manual retry resets it; running repairs retain coalesced provider follow-up |
| #99 | Resolved lookup identity before details and revalidated details before actions |
| #100 | Moved contact observation to the process owner and separated live invalidation from indexing |
| #101 | Shared validated metadata and current TTL/provider invalidation between Search and Settings |
| #102 | Bounded live scan time/raw rows while preserving fuzzy ranking and cancellation/empty/partial distinctions |

The second device recording showed repeated Running/Idle contact jobs with a saved cache still present. The source contained a feedback edge from Grove's own WorkManager component package changes back to contact scheduling, bypassing the provider throttle. That edge is removed. The recording alone did not establish a crash or identify every runtime callback; actual idle stability requires the device recheck.

## Cache and scheduling invariants

Contact caches contain only aggregate ID, lookup key, display name, writtenAt and coverage. Numbers/channels are queried on demand and are not persisted in Config or the index. Caches are device-private, disposable and excluded from backup. Indexing/search disable or permission loss cancels protected work and clears the relevant cache during capability reconciliation. Unrelated app search/Home remain available.

Cache freshness is evaluated when used: 15 minutes for contacts, 24 hours for files, rejecting future timestamps and provider-invalidated snapshots. Hitting a count bound remains partial. A provider event during a scan keeps its published snapshot invalid until the coalesced successor refreshes it. Work status is displayed separately from saved-cache status. Idle does not mean absent, and a queued refresh does not hide a committed snapshot.

Automatic repair is bounded across process recreation. Repeated corruption does not rebuild indefinitely; manual Retry permits recovery. Scheduling acceptance means a request was received, not that WorkManager has confirmed enqueue or a cache has been written. Off-thread operation completion and scoped failure state supply those distinctions.

Live deadlines use Android CancellationSignal; an OEM provider that ignores cancellation cannot be forcibly terminated safely. The deadline is a cancellation request, not proof of a hard wall-clock guarantee on every device.

## Verification boundary

ContactLifecycleTest covers own-component feedback isolation, external catalogue events, orphan/terminal/superseded reservations, repair policy with running follow-up, raw-row/deadline budgets, cache TTL/clock reversal/invalidation and explicit causes. IndexRefreshRequestsTest covers queued/running notification bursts, one deferred follow-up and scheduling/marker failure. Existing access, publication, ranking and action helper tests remain in the suite.

Actual WorkManager persistence/failure, observer registration/lifetime, provider cancellation and Android contact aggregation need instrumented/device evidence. See TESTING.md → Contact-path fixes #93–#102. Issues remain open and PR #92 remains draft pending acceptance/integration.
