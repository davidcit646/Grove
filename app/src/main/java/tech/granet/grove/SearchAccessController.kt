package tech.granet.grove

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.message

/** Owns current Android access checks and the user-facing permission handoff for protected search sources. */
internal class SearchAccessController(
    private val activity: MainActivity,
    private val reconcile: () -> Unit,
) {
    fun hasContacts(): Boolean =
        activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun hasFiles(): Boolean = Environment.isExternalStorageManager()

    fun requestContacts() {
        activity.requestContacts.launch(Manifest.permission.READ_CONTACTS)
    }

    fun explainContacts() {
        if (hasContacts()) {
            reconcile()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Contact search access")
            .setMessage("Grove reads contact names from Android for on-device search. If Contact indexing is on, names and lookup IDs are saved in Grove's private on-device cache; phone numbers are read only when you choose an action. Grove does not upload them. Android keeps the permission until you revoke it in system settings.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Continue to Android") { _, _ -> requestContacts() }
            .show()
    }

    fun requestFiles() {
        runCatching {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
        }.onFailure {
            activity.message("Open Android settings to allow shared storage search")
        }
    }

    fun explainFiles() {
        if (hasFiles()) {
            reconcile()
            return
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Shared-storage file search access")
            .setMessage("Android's All files access grants Grove broad access to shared storage, but not app-private data or system partitions. Grove searches names and paths without reading contents or uploading them. If File indexing is on, names and paths are saved in Grove's private on-device cache. Android keeps the permission until you revoke it in system settings.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open Android settings") { _, _ -> requestFiles() }
            .show()
    }
}
