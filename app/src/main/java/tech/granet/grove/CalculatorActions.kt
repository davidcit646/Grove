package tech.granet.grove

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.util.Log
import tech.granet.grove.ui.message

/** Android selects a calculator; the local answer does not depend on an installed handler. */
internal class CalculatorActions(private val activity: Activity) {
    private var failureLogged = false
    fun open() {
        try {
            activity.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
        } catch (_: ActivityNotFoundException) {
            activity.message(activity.getString(R.string.calculator_missing))
        } catch (error: Exception) {
            if (!failureLogged) {
                failureLogged = true
                Log.w("Grove", "Calculator launch unavailable: ${error.javaClass.simpleName}")
            }
            activity.message(activity.getString(R.string.calculator_unavailable))
        }
    }
}
