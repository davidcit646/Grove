package tech.granet.grove

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
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
                prefs(context).edit().remove(token(kind)).remove(started(kind)).remove(pending(kind)).commit()
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
        val setting = ConfigStore(prefs(context)).load().search
        when (kind) { "files" -> IndexAccessPolicy.files(setting, Environment.isExternalStorageManager())
            else -> IndexAccessPolicy.contacts(setting, context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    } catch (_: Exception) { false }

    fun allowed(context: Context, kind: String, expected: String): Boolean =
        prefs(context).getString(token(kind), null) == expected && enabled(context, kind)

    @Synchronized fun begin(context: Context, kind: String, expected: String): Boolean {
        if (!allowed(context, kind, expected)) return false
        // This scan covers all notifications received before it starts.
        return prefs(context).edit().putString(started(kind), expected).putLong(lastStarted(kind), System.currentTimeMillis()).remove(pending(kind)).commit()
    }

    @Synchronized fun finished(context: Context, kind: String, expected: String, succeeded: Boolean = true) {
        val store = prefs(context)
        if (store.getString(token(kind), null) != expected) return
        val followUp = store.getString(pending(kind), null) == expected
        if (!store.edit().remove(token(kind)).remove(started(kind)).remove(pending(kind)).commit()) return
        if (succeeded) store.edit().remove(repair(kind)).commit()
        if (succeeded && followUp && enabled(context, kind)) {
            // Append one delayed scan after this worker; never cancel a scan to refresh it.
            schedule(context, kind, followUp = true, delayMillis = 30_000)
        }
    }

    fun enqueue(context: Context, kind: String, cause: IndexRefreshCause): Boolean {
        if (kind !in listOf("contacts", "files") || !enabled(context, kind)) { cancel(context, kind); return false }
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
        return try {
            val operation = WorkManager.getInstance(context).enqueueUniqueWork(name(kind), if (followUp) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
            scheduler.execute {
                try { operation.result.get(30, TimeUnit.SECONDS); failure(kind, null) }
                catch (error: Exception) {
                    synchronized(this) {
                        if (prefs(context).getString(token(kind), null) == id)
                            prefs(context).edit().remove(token(kind)).remove(workId(kind)).remove(started(kind)).remove(pending(kind)).commit()
                    }
                    failure(kind, "Scheduling failed")
                    Log.w("Grove", "Index enqueue failed for $kind", error)
                }
            }
            true
        } catch (error: Exception) {
            if (prefs(context).getString(token(kind), null) == id)
                prefs(context).edit().remove(token(kind)).remove(workId(kind)).remove(started(kind)).remove(pending(kind)).commit()
            Log.w("Grove", "Could not schedule $kind index: ${error.javaClass.simpleName}")
            false
        }
    }

    @Synchronized fun cancel(context: Context, kind: String) {
        // Invalidate before cancellation/clearing: a worker already finishing must fail closed.
        val active = prefs(context).contains(token(kind))
        val tracked = prefs(context).contains(workId(kind))
        val cache = java.io.File(context.filesDir, "grove-$kind-index.json").exists()
        if (!active && !tracked && !cache) return
        if (active || tracked) {
            prefs(context).edit().remove(token(kind)).remove(workId(kind)).remove(started(kind)).remove(pending(kind)).commit()
            try { WorkManager.getInstance(context).cancelUniqueWork(name(kind)) }
            catch (error: Exception) { Log.w("Grove", "Could not cancel $kind index: ${error.javaClass.simpleName}") }
        }
        if (cache) try { IndexCache.clear(context, kind) }
            catch (error: Exception) { Log.w("Grove", "Could not delete $kind index: ${error.javaClass.simpleName}") }
    }

    @Synchronized fun reconcile(context: Context, kind: String): Boolean {
        if (kind == "contacts") (context.applicationContext as GroveApp).contactChanges.reconcile()
        if (!enabled(context, kind)) { cancel(context, kind); return true }
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

internal class IndexWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val contactCancellation = android.os.CancellationSignal()
    override fun onStopped() {
        contactCancellation.cancel()
        super.onStopped()
    }
    override fun doWork(): Result {
        val kind = inputData.getString("kind") ?: return Result.failure()
        val token = inputData.getString("token") ?: return Result.failure()
        if (kind != "contacts" && kind != "files") return Result.failure()
        val context = applicationContext
        val allowed = { !isStopped && IndexWork.allowed(context, kind, token) }
        if (isStopped) return Result.retry()
        if (!allowed()) {
            IndexWork.finished(context, kind, token, false)
            return Result.success()
        }
        if (!IndexWork.begin(context, kind, token)) {
            if (allowed()) return Result.retry()
            IndexWork.finished(context, kind, token, false)
            return Result.success()
        }
        var retry = false
        var succeeded = true
        val outcome = try {
            val saved = if (kind == "files") {
                val scan = FileIndex.scan(Environment.getExternalStorageDirectory(), shouldContinue = allowed)
                allowed() && IndexCache.writeFiles(context, scan, allowed)
            } else {
                val changes = (context as GroveApp).contactChanges.changes.value
                val contacts = ContactIndex.load(context.contentResolver, allowed, contactCancellation)
                (allowed() && IndexCache.writeContacts(context, contacts, allowed)).also { saved ->
                    if (saved && changes != context.contactChanges.changes.value) IndexCache.invalidate("contacts")
                }
            }
            succeeded = saved
            if (!saved && allowed()) error("Index cache was not committed")
            Result.success() // Superseded is cancellation, not failure.
        } catch (error: Exception) {
            Log.w("Grove", "Background $kind index unavailable: ${error.javaClass.simpleName}")
            if (isStopped) {
                retry = true
                Result.retry()
            } else if (!allowed()) Result.success() else if (runAttemptCount < 2) {
                retry = true
                Result.retry()
            } else { succeeded = false; Result.failure() }
        }
        if (!retry) IndexWork.finished(context, kind, token, succeeded)
        return outcome
    }
}
