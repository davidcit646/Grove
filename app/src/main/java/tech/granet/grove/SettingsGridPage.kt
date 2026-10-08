package tech.granet.grove

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import tech.granet.grove.ui.bodyText
import tech.granet.grove.ui.dp

/** Grid choices and preview are local; Apply publishes only the validated layout intent. */
internal class SettingsGridPage(private val activity: SettingsActivity, private val session: SettingsSession,
                               private val feedback: (CommandFeedback) -> Boolean) {
    fun render(home: Boolean, content: LinearLayout) {
        val current = session.repository.snapshot().config.let { if (home) it.homeGrid else it.drawerGrid }
        var columns = session.gridColumns ?: current?.columns ?: GridPolicy.columns(null, activity.resources.configuration.screenWidthDp)
        var rows = session.gridRows ?: current?.rows ?: 5
        val description = activity.bodyText("")
        content.addView(description)
        content.addView(activity.bodyText("Rows × columns set each page’s capacity. Extra items remain reachable; dense grids can scroll."))
        val preview = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; minimumHeight = activity.dp(160) }
        fun draw() {
            description.text = "$columns columns × $rows rows · ${columns * rows} items per page"
            preview.removeAllViews()
            repeat(rows) {
                val row = LinearLayout(activity)
                repeat(columns) {
                    row.addView(TextView(activity).apply {
                        text = "•"; gravity = Gravity.CENTER; setTextColor(ThemeColors.icon(activity))
                        background = GradientDrawable().apply { setColor(ThemeColors.iconSurface(activity)); cornerRadius = activity.dp(4).toFloat() }
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LinearLayout.LayoutParams(0, activity.dp(20), 1f).apply { setMargins(activity.dp(2), activity.dp(2), activity.dp(2), activity.dp(2)) })
                }
                preview.addView(row)
            }
        }
        fun slider(label: String, initial: Int, changed: (Int) -> Unit) {
            content.addView(activity.bodyText(label))
            content.addView(Slider(activity).apply {
                valueFrom = 1f; valueTo = 10f; stepSize = 1f; value = initial.toFloat(); contentDescription = label
                addOnChangeListener { _, value, fromUser -> if (fromUser) { changed(value.toInt()); draw() } }
            })
        }
        slider("Columns", columns) { columns = it; session.gridColumns = it }
        slider("Rows", rows) { rows = it; session.gridRows = it }
        content.addView(preview); draw()
        content.addView(MaterialButton(activity).apply { text = "Apply grid"; setOnClickListener {
            if (feedback(session.commands.grid(home, IconGrid(columns, rows)))) { session.gridColumns = null; session.gridRows = null }
        } })
        content.addView(MaterialButton(activity).apply { text = "Use automatic layout"; setOnClickListener {
            if (feedback(session.commands.grid(home, null))) { session.gridColumns = null; session.gridRows = null }
        } })
    }
}
