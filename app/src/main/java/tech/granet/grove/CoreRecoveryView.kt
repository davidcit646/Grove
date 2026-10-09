package tech.granet.grove

import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.message

internal object CoreRecoveryView {
    fun render(activity: MainActivity, recovery: CoreRecoveryState, retry: () -> Unit) {
        with(activity) {
            root.animate().cancel()
            root.removeAllViews()
            root.setBackgroundColor(0xff182421.toInt())
            val panel = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(24), dp(24), dp(24))
            }
            panel.addView(TextView(this).apply {
                text = "Grove cannot load Home"
                textSize = 24f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            panel.addView(TextView(this).apply {
                text = recovery.detail
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            if (recovery.retryable) panel.addView(Button(this).apply {
                text = "Retry"
                setOnClickListener { retry() }
            })
            if (recovery.settingsEscape) panel.addView(Button(this).apply {
                text = "Android Home settings"
                setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    } catch (_: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (_: Exception) {
                            message("Android Settings is unavailable")
                        }
                    }
                }
            })
            root.addView(panel, LinearLayout.LayoutParams(-1, -1))
        }
    }
}
