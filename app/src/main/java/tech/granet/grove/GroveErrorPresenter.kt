package tech.granet.grove

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.message

internal object GroveErrorPresenter {
    fun show(activity: Activity, error: GroveError, retry: (() -> Unit)? = null) {
        val body = "${error.feature} · ${error.severity.label}\n${error.codeLine()}\n\n${error.summary}"
        val route = GroveErrorRouting.route(error, retry != null)
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle("Grove problem")
            .setMessage(body)
            .setNegativeButton(route.dismissLabel, null)
        if (retry != null) builder.setPositiveButton(route.actionLabel) { _, _ -> retry() }
        builder.setNeutralButton("Report") { _, _ ->
            if (CrashReporter.reportUserRequested(activity, error, null)) {
                activity.message("Problem report saved for your review")
                CrashReporter.reviewPending(activity)
            } else {
                activity.message("Could not save the problem report")
            }
        }
        builder.show()
    }
}
