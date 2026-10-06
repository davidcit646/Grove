package tech.granet.grove

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract

/** One process-owned observer. Live query invalidation does not require indexing. */
internal class ContactChanges(private val context: Context) {
    val changes = androidx.lifecycle.MutableLiveData(0L)
    private val handler = Handler(Looper.getMainLooper())
    private var observing = false
    private fun settings() = (context.applicationContext as GroveApp).settingsRepository.snapshot().config.search
    private fun eligible() = runCatching {
        settings().contacts && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    private val changed = Runnable {
        if (eligible()) {
            changes.value = (changes.value ?: 0L) + 1L
            IndexCache.invalidate("contacts")
            if (settings().contactIndexing) IndexWork.enqueue(context, "contacts", IndexRefreshCause.PROVIDER_CHANGE)
        }
    }
    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(changed)
            handler.postDelayed(changed, 400L)
        }
    }
    fun reconcile() {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { reconcile() }; return }
        if (eligible() && !observing) {
            runCatching { context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer) }
                .onSuccess { observing = true }
        } else if (!eligible() && observing) {
            handler.removeCallbacks(changed)
            runCatching { context.contentResolver.unregisterContentObserver(observer) }
            observing = false
            changes.value = (changes.value ?: 0L) + 1L
        }
    }
}
