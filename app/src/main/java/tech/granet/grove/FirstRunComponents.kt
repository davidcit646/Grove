package tech.granet.grove

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import tech.granet.grove.ui.dp

/** Shared visual primitives for setup pages; no configuration is committed here. */
internal class FirstRunComponents(private val context: Context) {
    fun color(attr: Int, fallback: Int) = MaterialColors.getColor(context, attr, fallback)
    val ink get() = color(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    val muted get() = color(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xff454545.toInt())
    val primary get() = color(com.google.android.material.R.attr.colorPrimary, 0xff315b47.toInt())
    val onAccent get() = color(com.google.android.material.R.attr.colorOnPrimaryContainer, 0xff183526.toInt())
    val accent get() = color(com.google.android.material.R.attr.colorPrimaryContainer, 0xffd8e8d9.toInt())
    val surface get() = color(com.google.android.material.R.attr.colorSurface, Color.WHITE)
    val canvas get() = color(com.google.android.material.R.attr.colorSurfaceContainerLow, 0xfffafafa.toInt())
    fun text(value: String, size: Float = 16f, bold: Boolean = false, color: Int = ink) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    fun icon(id: Int, tint: Int = primary, size: Int = 32) = ImageView(context).apply {
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setImageResource(id)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(context.dp(size), context.dp(size))
    }
    fun card(parent: LinearLayout, highlighted: Boolean = false, build: (LinearLayout) -> Unit) {
        val shell = MaterialCardView(context).apply {
            radius = context.dp(24).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(if (highlighted) accent else surface)
        }
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(20), context.dp(20), context.dp(20), context.dp(20))
        }
        shell.addView(body)
        build(body)
        parent.addView(shell, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(12) })
    }
    fun heading(parent: LinearLayout, title: String, summary: String) {
        parent.addView(text(title, 30f, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(8)
        })
        if (summary.isNotBlank()) parent.addView(text(summary, 16f, color = muted), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(12); bottomMargin = context.dp(8)
        })
    }
    fun choice(parent: LinearLayout, iconId: Int, title: String, detail: String,
                       enabled: Boolean, changed: (Boolean) -> Unit) {
        card(parent) { box ->
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(icon(iconId))
            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 17f, true))
                if (detail.isNotBlank()) addView(text(detail, 14f, color = muted))
            }
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(14) })
            val switch = MaterialSwitch(context).apply {
                isChecked = enabled
                contentDescription = title
                setOnCheckedChangeListener { _, checked -> changed(checked) }
            }
            row.addView(switch, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
            box.addView(row)
            box.setOnClickListener { switch.isChecked = !switch.isChecked }
        }
    }
    fun action(parent: LinearLayout, label: String, click: () -> Unit) {
        parent.addView(MaterialButton(context).apply {
            text = label
            textSize = 16f
            minimumHeight = context.dp(54)
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(16) })
    }


}
