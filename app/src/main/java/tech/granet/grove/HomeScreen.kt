package tech.granet.grove

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import tech.granet.grove.ui.dp

/** Places home controls, pins, and widgets according to the saved layout. */
internal class HomeScreen(private val context: Context) {

    fun render(root: LinearLayout, config: HomeScreenSettings,
               button: (String, () -> Unit) -> View,
               search: () -> Unit, drawer: () -> Unit,
               openClock: () -> Unit, openCalendar: () -> Unit,
               pins: (LinearLayout) -> Unit, widgets: (LinearLayout) -> Unit): LinearLayout {
        if (config.showClock) {
            root.addView(TextClock(context).apply {
                format12Hour = "h:mm"; format24Hour = "HH:mm"; textSize = 58f; setTextColor(Color.WHITE)
                if (config.tapClockOpensClock) {
                    contentDescription = "Open Clock"
                    setOnClickListener { openClock() }
                }
            })
            root.addView(TextClock(context).apply {
                format12Hour = "EEEE, MMMM d"; format24Hour = "EEEE, MMMM d"
                textSize = 16f; setTextColor(Color.WHITE); setPadding(0, context.dp(8), 0, context.dp(8))
                contentDescription = "Open Calendar"
                setOnClickListener { openCalendar() }
            })
        }
        if (config.showSearchButton) root.addView(button("Search", search))
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(context).apply { isFillViewport = true; addView(body) },
            LinearLayout.LayoutParams(-1, 0, 1f))
        if (config.showPinnedApps && !config.pinnedAppsAtBottom) pins(body)
        widgets(body)
        if (config.showPinnedApps && config.pinnedAppsAtBottom) {
            body.addView(View(context), LinearLayout.LayoutParams(1, 0, 1f))
            pins(body)
        }
        if (config.showAppsButton) root.addView(button("All apps", drawer))
        return body
    }
}
