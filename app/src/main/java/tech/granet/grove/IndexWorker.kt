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
