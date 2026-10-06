package tech.granet.grove

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.wallpaperLabel

/** Shared tile structure and list adapter; Activity supplies launch and interaction actions. */
internal class DrawerTiles(
    private val activity: AppCompatActivity,
    private val launch: (App) -> Unit,
    private val menu: (App) -> Unit,
    private val cellHeight: () -> Int? = { null },
) {
    sealed interface Item {
        data class Application(val app: App) : Item
        data class Folder(val folder: AppFolder) : Item
    }

    class Tile(val layout: LinearLayout, val icon: ImageView, val name: TextView, val badge: ImageView)

    fun create(height: Int? = null): Tile {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(4), activity.dp(10), activity.dp(4), activity.dp(10))
            minimumHeight = height ?: activity.dp(96)
            isFocusable = true
            isClickable = true
        }
        val icon = ImageView(activity).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val name = activity.wallpaperLabel("", 12f).apply { gravity = Gravity.CENTER; maxLines = 2; minLines = 2 }
        val frame = FrameLayout(activity)
        frame.addView(icon, FrameLayout.LayoutParams(activity.dp(48), activity.dp(48), Gravity.CENTER))
        val badge = ImageView(activity).apply {
            setPadding(activity.dp(7), activity.dp(7), activity.dp(7), activity.dp(7))
            imageTintList = ColorStateList.valueOf(ThemeColors.icon(activity))
            background = GradientDrawable().apply {
                setColor(ThemeColors.iconSurface(activity))
                cornerRadius = activity.dp(10).toFloat()
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(badge, FrameLayout.LayoutParams(activity.dp(30), activity.dp(30), Gravity.TOP or Gravity.END))
        layout.addView(frame, LinearLayout.LayoutParams(activity.dp(56), activity.dp(52)))
        layout.addView(name)
        return Tile(layout, icon, name, badge).also { layout.tag = it }
    }

    fun bind(tile: Tile, app: App) {
        tile.badge.visibility = View.GONE
        tile.icon.alpha = 1f
        tile.layout.contentDescription = app.label
        tile.name.text = app.label
        tile.icon.imageTintList = null
        tile.icon.tag = app.key
        tile.icon.setImageBitmap(AppIconStore[app.key])
        tile.layout.setOnClickListener { launch(app) }
        tile.layout.setOnLongClickListener { menu(app); true }
        tile.layout.setOnTouchListener(null)
        tile.layout.setOnDragListener(null)
    }

    inner class Adapter(private val bindItem: (Tile, Item) -> Unit) : BaseAdapter() {
        private var items = emptyList<Item>()
        fun submit(next: List<Item>) { items = next; notifyDataSetChanged() }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tile = (convertView?.tag as? Tile) ?: create(cellHeight())
            tile.layout.layoutParams = android.widget.AbsListView.LayoutParams(-1, cellHeight() ?: -2)
            bindItem(tile, items[position])
            return tile.layout
        }
    }
}
