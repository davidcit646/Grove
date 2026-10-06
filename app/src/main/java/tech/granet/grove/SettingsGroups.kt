package tech.granet.grove

import android.widget.LinearLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.iconRow
import tech.granet.grove.ui.titleText

/** Presentation only: shared Material surfaces around existing setting rows and callbacks. */
internal object SettingsGroups {
    fun card(content: LinearLayout, title: String, icon: Int? = null): LinearLayout {
        val context = content.context
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        }
        val card = MaterialCardView(context).apply {
            radius = context.dp(20).toFloat()
            strokeWidth = 0
            cardElevation = 0f
            setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant))
            isClickable = false
            isFocusable = false
            addView(group)
        }
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(16) })
        val heading = if (icon == null) context.titleText(title) else
            context.iconRow(title, icon, minHeightDp = 48, onClick = {}).apply {
                isClickable = false; isFocusable = false
            }
        heading.isAccessibilityHeading = true
        group.addView(heading)
        return group
    }
}
