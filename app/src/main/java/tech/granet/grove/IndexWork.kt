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
    private fun enabled(context: Context, kind: String): Boolean = runCatching {
        val setting = ConfigStore(prefs(context)).load().search
        when (kind) { "files" -> setting.fileIndexing && Environment.isExternalStorageManager()
            else -> setting.contactIndexing && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED }
    }.getOrDefault(false)

    fun allowed(context: Context, kind: String, expected: String): Boolean =
        prefs(context).getString(token(kind), null) == expected && enabled(context, kind)

    fun finished(context: Context, kind: String, expected: String) {
        if (prefs(context).getString(token(kind), null) == expected)
            prefs(context).edit().remove(token(kind)).commit()
    }

    fun enqueue(context: Context, kind: String) {
        if (!enabled(context, kind)) { cancel(context, kind); return }
        val id = UUID.randomUUID().toString()
        if (!prefs(context).edit().putString(token(kind), id).commit()) return
        val request = OneTimeWorkRequestBuilder<IndexWorker>()
            .setInputData(androidx.work.workDataOf("kind" to kind, "token" to id))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name(kind), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context, kind: String) {
        // Invalidate before cancellation/clearing: a worker already finishing must fail closed.
        val active = prefs(context).contains(token(kind))
        val cache = java.io.File(context.filesDir, "grove-$kind-index.json").exists()
        if (!active && !cache) return
        if (active) {
            prefs(context).edit().remove(token(kind)).commit()
            WorkManager.getInstance(context).cancelUniqueWork(name(kind))
        }
        if (cache) IndexCache.clear(context, kind)
    }

    fun reconcile(context: Context, kind: String) {
        if (!enabled(context, kind)) { cancel(context, kind); return }
        // Reschedule only absent/stale caches; events and explicit Retry enqueue directly.
        val file = java.io.File(context.filesDir, "grove-$kind-index.json")
        val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() >
            (if (kind == "files") 24L * 60 * 60_000 else 15L * 60_000)
        if (stale && prefs(context).getString(token(kind), null) == null) enqueue(context, kind)
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
        if (!allowed()) {
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
            if (!allowed()) Result.success() else if (runAttemptCount < 2) {
                retry = true
                Result.retry()
            } else Result.failure()
        }
        if (!retry) IndexWork.finished(context, kind, token)
        return outcome
    }
}
