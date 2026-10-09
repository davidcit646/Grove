package tech.granet.grove

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Persistent per-source work; the token prevents an older canceled job from committing. */
internal object IndexWork {
    private val scheduler = java.util.concurrent.Executors.newSingleThreadExecutor()
    val failures = androidx.lifecycle.MutableLiveData<Map<String, String>>(emptyMap())
    private val errors = mutableMapOf<String, String>()
    @Synchronized private fun failure(kind: String, message: String?) {
        if (message == null) errors.remove(kind) else errors[kind] = message
        failures.postValue(errors.toMap())
    }
    private fun recover(context: Context, kind: String) {
        val expected = prefs(context).getString(token(kind), null) ?: return
        val id = currentWorkId(context, kind)
        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(name(kind)).get(30, TimeUnit.SECONDS)
        val state = infos.firstOrNull { it.id.toString() == id }?.state
        synchronized(this) {
            if (IndexRecoveryPolicy.release(expected, prefs(context).getString(token(kind), null), state != null, state?.isFinished == true)) {
                if (!prefs(context).edit().remove(token(kind)).remove(started(kind)).remove(pending(kind)).commit())
                    failure(kind, "Work recovery could not be saved; retry")
            }
        }
    }
    private fun prefs(context: Context) = context.getSharedPreferences("grove", Context.MODE_PRIVATE)
    fun name(kind: String) = "grove-$kind-index"
    private fun token(kind: String) = "index-token-$kind"
    private fun started(kind: String) = "index-started-$kind"
    private fun lastStarted(kind: String) = "index-last-started-$kind"
    private fun pending(kind: String) = "index-refresh-pending-$kind"
    private fun repair(kind: String) = "index-repair-attempted-$kind"
    private fun workId(kind: String) = "index-work-id-$kind"
    fun currentWorkId(context: Context, kind: String): String? = prefs(context).getString(workId(kind), null)
    private fun enabled(context: Context, kind: String): Boolean = try {
        val setting = (context.applicationContext as GroveApp).settingsRepository.snapshot().config.search
        when (kind) { "files" -> IndexAccessPolicy.files(setting, Environment.isExternalStorageManager())
            else -> IndexAccessPolicy.contacts(setting, context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    } catch (_: Exception) { false }

    private val revocation = IndexRevocationGate(IndexCache.commitLock)

    fun allowed(context: Context, kind: String, expected: String): Boolean =
        revocation.allowed(kind) && prefs(context).getString(token(kind), null) == expected && enabled(context, kind)

    @Synchronized fun begin(context: Context, kind: String, expected: String): Boolean {
        if (!allowed(context, kind, expected)) return false
        // This scan covers all notifications received before it starts.
        return prefs(context).edit().putString(started(kind), expected).putLong(lastStarted(kind), System.currentTimeMillis()).remove(pending(kind)).commit()
    }

    @Synchronized fun finished(context: Context, kind: String, expected: String, succeeded: Boolean = true) {
        val store = prefs(context)
        if (store.getString(token(kind), null) != expected) return
        val followUp = store.getString(pending(kind), null) == expected
        if (!store.edit().remove(token(kind)).remove(started(kind)).remove(pending(kind))
                .apply { if (succeeded) remove(repair(kind)) }.commit()) {
            failure(kind, "Work completion could not be saved; retry")
            return
        }
        if (succeeded && followUp && enabled(context, kind)) {
            // Append one delayed scan after this worker; never cancel a scan to refresh it.
            schedule(context, kind, followUp = true, delayMillis = 30_000)
        }
    }

    fun enqueue(context: Context, kind: String, cause: IndexRefreshCause): Boolean {
        require(kind in listOf("contacts", "files")) { "Unknown index" }
        if (!enabled(context, kind)) { cancel(context, kind); return false }
        val app = context.applicationContext
        scheduler.execute {
            try {
                recover(app, kind)
                synchronized(this) { if (!enqueueRecovered(app, kind, cause)) failure(kind, "Refresh unavailable; retry manually") }
            } catch (error: Exception) {
                failure(kind, "Scheduling unavailable")
                Log.w("Grove", "Could not reconcile $kind work", error)
            }
        }
        return true // Accepted by our scheduler; WorkManager completion is observed separately.
    }

    private fun enqueueRecovered(context: Context, kind: String, cause: IndexRefreshCause): Boolean {
        if (!enabled(context, kind)) { cancel(context, kind); return false }
        val store = prefs(context)
        if (cause == IndexRefreshCause.MANUAL && !store.edit().remove(repair(kind)).commit()) return false
        val active = store.getString(token(kind), null)
        return IndexRefreshRequests.request(active, store.getString(started(kind), null), store.getString(pending(kind), null),
            schedule = {
                if (!IndexRecoveryPolicy.repairAllowed(cause, store.getBoolean(repair(kind), false))) {
                    failure(kind, "Automatic repair exhausted; retry manually")
                    false
                } else if (cause == IndexRefreshCause.REPAIR && !store.edit().putBoolean(repair(kind), true).commit()) false
                else schedule(context, kind, followUp = false, delayMillis = IndexRefreshRequests.delayMillis(cause, store.getLong(lastStarted(kind), 0), System.currentTimeMillis()))
            },
            defer = { store.edit().putString(pending(kind), active).commit() })
    }

    private fun schedule(context: Context, kind: String, followUp: Boolean, delayMillis: Long): Boolean {
        val id = UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(androidx.work.workDataOf("kind" to kind, "token" to id))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        if (!prefs(context).edit().putString(token(kind), id)
                .putString(workId(kind), request.id.toString()).commit()) return false
        revocation.renew(kind)
        return try {
            val operation = WorkManager.getInstance(context).enqueueUniqueWork(name(kind), if (followUp) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
            scheduler.execute {
                try { operation.result.get(30, TimeUnit.SECONDS); failure(kind, null) }
                catch (error: Exception) {
                    synchronized(this) {
                        if (prefs(context).getString(token(kind), null) == id)
                            if (!prefs(context).edit().remove(token(kind)).remove(workId(kind)).remove(started(kind)).remove(pending(kind)).commit())
                                failure(kind, "Failed work cleanup could not be saved; retry")
                    }
                    failure(kind, "Scheduling failed")
                    Log.w("Grove", "Index enqueue failed for $kind", error)
                }
            }
            true
        } catch (error: Exception) {
            if (prefs(context).getString(token(kind), null) == id)
                if (!prefs(context).edit().remove(token(kind)).remove(workId(kind)).remove(started(kind)).remove(pending(kind)).commit())
                                failure(kind, "Failed work cleanup could not be saved; retry")
            Log.w("Grove", "Could not schedule $kind index: ${error.javaClass.simpleName}")
            false
        }
    }

    @Synchronized fun cancel(context: Context, kind: String): Boolean {
        require(kind == "contacts" || kind == "files") { "Unknown index" }
        val store = prefs(context)
        val hadWork = store.contains(token(kind)) || store.contains(workId(kind))
        return try {
            revocation.cancel(kind, persist = {
                val idle = listOf(token(kind), workId(kind), started(kind), pending(kind)).none(store::contains)
                val confirmed = idle || store.edit().remove(token(kind)).remove(workId(kind))
                    .remove(started(kind)).remove(pending(kind)).commit()
                if (!confirmed) failure(kind, "Cancellation could not be saved; protected work is blocked. Retry.")
                confirmed
            }, cleanup = {
                // Durable token removal already prevents every old worker from publishing.
                // WorkManager cancellation is asynchronous; it does not authorize cache writes.
                if (hadWork) WorkManager.getInstance(context).cancelUniqueWork(name(kind))
                val cleared = !IndexCache.exists(context, kind) || IndexCache.clear(context, kind)
                failure(kind, if (cleared) null else "Index deletion failed; retry")
                cleared
            })
        } catch (error: Exception) {
            failure(kind, "Cancellation cleanup unavailable; retry")
            Log.w("Grove", "Index cancellation unavailable: ${error.javaClass.simpleName}")
            false
        }
    }

    @Synchronized fun reconcile(context: Context, kind: String): Boolean {
        if (kind == "contacts") (context.applicationContext as GroveApp).contactChanges.reconcile()
        if (!enabled(context, kind)) return cancel(context, kind)
        val app = context.applicationContext
        scheduler.execute {
            try {
                recover(app, kind)
                val metadata = IndexCache.inspect(app, kind)
                if (!metadata.fresh(kind)) synchronized(this) {
                    if (prefs(app).getString(token(kind), null) == null) enqueueRecovered(app, kind, if (metadata.validity == IndexValidity.CORRUPT) IndexRefreshCause.REPAIR else IndexRefreshCause.STALE_CACHE)
                }
            } catch (error: Exception) { failure(kind, "Scheduling unavailable") }
        }
        return true
    }
}
