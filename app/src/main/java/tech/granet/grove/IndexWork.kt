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
    private fun prefs(context: Context) = context.getSharedPreferences("grove", Context.MODE_PRIVATE)
    fun name(kind: String) = "grove-$kind-index"
    private fun token(kind: String) = "index-token-$kind"
    private fun started(kind: String) = "index-started-$kind"
    private fun pending(kind: String) = "index-refresh-pending-$kind"
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
        return prefs(context).edit().putString(started(kind), expected).remove(pending(kind)).commit()
    }

    @Synchronized fun finished(context: Context, kind: String, expected: String) {
        val store = prefs(context)
        if (store.getString(token(kind), null) != expected) return
        val followUp = store.getString(pending(kind), null) == expected
        if (!store.edit().remove(token(kind)).remove(started(kind)).remove(pending(kind)).commit()) return
        if (followUp && enabled(context, kind)) {
            // Append one delayed scan after this worker; never cancel a scan to refresh it.
            schedule(context, kind, followUp = true)
        }
    }

    @Synchronized fun enqueue(context: Context, kind: String): Boolean {
        if (!enabled(context, kind)) { cancel(context, kind); return false }
        val store = prefs(context)
        val active = store.getString(token(kind), null)
        return IndexRefreshRequests.request(active, store.getString(started(kind), null), store.getString(pending(kind), null),
            schedule = { schedule(context, kind, followUp = false) },
            defer = { store.edit().putString(pending(kind), active).commit() })
    }

    private fun schedule(context: Context, kind: String, followUp: Boolean): Boolean {
        val id = UUID.randomUUID().toString()
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(androidx.work.workDataOf("kind" to kind, "token" to id))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .setInitialDelay(if (followUp) 30 else 0, TimeUnit.SECONDS)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        if (!prefs(context).edit().putString(token(kind), id)
                .putString(workId(kind), request.id.toString()).commit()) return false
        return try {
            WorkManager.getInstance(context).enqueueUniqueWork(name(kind), if (followUp) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, request)
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
        if (!enabled(context, kind)) { cancel(context, kind); return true }
        // Reschedule only absent/stale caches; events and explicit Retry enqueue directly.
        val file = java.io.File(context.filesDir, "grove-$kind-index.json")
        val age = System.currentTimeMillis() - file.lastModified()
        val stale = !file.exists() || age < 0 || age >
            (if (kind == "files") 24L * 60 * 60_000 else 15L * 60_000)
        return if (stale && prefs(context).getString(token(kind), null) == null) enqueue(context, kind) else true
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
            IndexWork.finished(context, kind, token)
            return Result.success()
        }
        if (!IndexWork.begin(context, kind, token)) {
            if (allowed()) return Result.retry()
            IndexWork.finished(context, kind, token)
            return Result.success()
        }
        var retry = false
        val outcome = try {
            val saved = if (kind == "files") {
                val scan = FileIndex.scan(Environment.getExternalStorageDirectory(), shouldContinue = allowed)
                allowed() && IndexCache.writeFiles(context, scan, allowed)
            } else {
                val contacts = ContactIndex.load(context.contentResolver, allowed, contactCancellation)
                allowed() && IndexCache.writeContacts(context, contacts, allowed)
            }
            if (saved) Result.success() else Result.success() // Superseded is cancellation, not failure.
        } catch (error: Exception) {
            Log.w("Grove", "Background $kind index unavailable: ${error.javaClass.simpleName}")
            if (isStopped) {
                retry = true
                Result.retry()
            } else if (!allowed()) Result.success() else if (runAttemptCount < 2) {
                retry = true
                Result.retry()
            } else Result.failure()
        }
        if (!retry) IndexWork.finished(context, kind, token)
        return outcome
    }
}
