package tech.granet.grove

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.message

/** Android access/disclosure adapter. Owns no query worker or index state. */
internal class SearchAccessController(private val activity: MainActivity) {
    fun hasContactAccess(): Boolean = with(activity) {
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    fun requestContactAccess() {
        with(activity) { requestContacts.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    fun explainContactAccess() {
        with(activity) {
            if (hasContactAccess()) { activity.searchController.sources.reconcile(); return }
            MaterialAlertDialogBuilder(this)
                .setTitle("Contact search access")
                .setMessage("Grove reads contact names from Android for on-device search. If Contact indexing is on, names and lookup IDs are saved in Grove's private on-device cache; phone numbers are read only when you choose an action. Grove does not upload them. Android keeps the permission until you revoke it in system settings.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Continue to Android") { _, _ -> requestContactAccess() }
                .show()
        }
    }

    fun requestFileAccess() {
        with(activity) {
            runCatching {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            }.onFailure { message("Open Android settings to allow shared storage search") }
        }
    }

    fun explainFileAccess() {
        with(activity) {
            if (Environment.isExternalStorageManager()) { activity.searchController.sources.reconcile(); return }
            MaterialAlertDialogBuilder(this)
                .setTitle("Shared-storage file search access")
                .setMessage("Android's All files access grants Grove broad access to shared storage, but not app-private data or system partitions. Grove searches names and paths without reading contents or uploading them. If File indexing is on, names and paths are saved in Grove's private on-device cache. Android keeps the permission until you revoke it in system settings.")
                .setNegativeButton("Not now", null)
                .setPositiveButton("Open Android settings") { _, _ -> requestFileAccess() }
                .show()
        }
    }

}
