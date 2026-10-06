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
    val failure = androidx.lifecycle.MutableLiveData<String?>(null)
    val changes = androidx.lifecycle.MutableLiveData(0L)
    private val handler = Handler(Looper.getMainLooper())
    private var observing = false
    private fun settings() = (context.applicationContext as GroveApp).settingsRepository.snapshot().config.search
    private fun eligible() = runCatching {
        settings().contacts && context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    private val changed = Runnable {
        if (eligible()) {
            IndexCache.invalidate("contacts")
            changes.value = (changes.value ?: 0L) + 1L
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
                .onSuccess { observing = true; failure.value = null }
                .onFailure {
                    failure.value = "Contact change monitoring unavailable"
                    android.util.Log.w("Grove", "Contact observer registration unavailable: ${it.javaClass.simpleName}")
                }
        } else if (!eligible() && observing) {
            handler.removeCallbacks(changed)
            runCatching { context.contentResolver.unregisterContentObserver(observer) }.onFailure {
                android.util.Log.w("Grove", "Contact observer cleanup unavailable: ${it.javaClass.simpleName}")
            }
            observing = false
            changes.value = (changes.value ?: 0L) + 1L
        }
    }
}
