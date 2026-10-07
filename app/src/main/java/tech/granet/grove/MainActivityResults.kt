package tech.granet.grove

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import tech.granet.grove.ui.message

/** Lifecycle-safe external result registrations for the HOME activity. */
internal class MainActivityResults(private val activity: MainActivity) {
    val requestContacts = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        activity.searchController.reconcileAccess()
        activity.setupController.firstRunSetup?.refreshPermissions()
    }

    val chooseWallpaperImage: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) activity.wallpaperPresentationController.importCustom(uri)
        }

    val uninstallNext =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            activity.catalogController.loadApps()
            if (result.resultCode == Activity.RESULT_OK) {
                activity.actionController.launchNextUninstall(activity.actionController.uninstallBatch.accepted())
            } else {
                val remaining = activity.actionController.uninstallBatch.cancel()
                if (remaining > 0) activity.message("Remaining uninstalls canceled")
            }
        }

    val export: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) activity.configController.exportDocument(uri)
        }

    val importConfig: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) activity.configController.importDocument(uri)
        }

    val bindWidget =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) activity.widgetFlow.configure()
            else activity.widgetFlow.cancel()
        }

    val configureWidget =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                activity.widgetFlow.finish()
            } else {
                Log.w(
                    "Grove",
                    "Widget configuration returned ${result.resultCode} for id ${activity.widgets.pending}",
                )
                activity.widgetFlow.cancel()
            }
        }

    val chooseHome =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
}
