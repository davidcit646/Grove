package tech.granet.grove

import android.app.WallpaperManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.dp

/** A preview carousel; wallpaper changes only after the destination is chosen. */
internal class WallpaperPicker(
    private val activity: MainActivity,
    private val controller: WallpaperController,
    initialIndex: Int,
    private val selected: (Int, Int) -> Unit,
) {
    private val count = WallpaperArt.commons.size + 3
    private var index = initialIndex.coerceIn(0, count - 1)
    private var generation = 0
    private var bitmap: Bitmap? = null
    private var ready = false
    private lateinit var dialog: AlertDialog
    private lateinit var preview: ImageView
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var status: TextView
    private lateinit var retry: MaterialButton
    private lateinit var previous: MaterialButton
    private lateinit var next: MaterialButton

    fun show() {
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(activity).apply {
            text = "Wallpapers"
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemeColors.icon(activity))
        }, LinearLayout.LayoutParams(0, activity.dp(56), 1f).apply { gravity = Gravity.CENTER_VERTICAL })
        val close = iconButton(R.drawable.ic_close, "Close wallpaper picker").apply {
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(close, LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)))
        column.addView(header)

        preview = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(ThemeColors.iconSurface(activity))
            isClickable = true
            isFocusable = true
            setOnClickListener { chooseDestination() }
            var downX = 0f
            var downY = 0f
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; true }
                    MotionEvent.ACTION_UP -> {
                        val delta = event.x - downX
                        if (kotlin.math.abs(delta) > activity.dp(55) &&
                            kotlin.math.abs(delta) > kotlin.math.abs(event.y - downY)) {
                            move(if (delta < 0) 1 else -1)
                        } else view.performClick()
                        true
                    }
                    else -> true
                }
            }
        }
        val height = (activity.resources.displayMetrics.heightPixels * 0.54f).toInt()
            .coerceIn(activity.dp(260), activity.dp(590))
        column.addView(preview, LinearLayout.LayoutParams(-1, height))

        title = TextView(activity).apply {
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemeColors.icon(activity))
            setPadding(0, activity.dp(14), 0, 0)
        }
        column.addView(title)
        subtitle = TextView(activity).apply {
            textSize = 13f
            setTextColor(ThemeColors.icon(activity))
            setPadding(0, activity.dp(4), 0, 0)
        }
        column.addView(subtitle)
        status = TextView(activity).apply {
            textSize = 14f
            setTextColor(ThemeColors.icon(activity))
            setPadding(0, activity.dp(8), 0, activity.dp(4))
        }
        column.addView(status)
        retry = MaterialButton(activity).apply {
            text = "Retry preview"
            visibility = View.GONE
            setOnClickListener { load() }
        }
        column.addView(retry)
        val navigation = LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(6), 0, 0)
        }
        previous = iconButton(R.drawable.ic_chevron_left, "Previous wallpaper").apply {
            text = "Previous"
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setOnClickListener { move(-1) }
        }
        next = iconButton(R.drawable.ic_chevron_right, "Next wallpaper").apply {
            text = "Next"
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
            setOnClickListener { move(1) }
        }
        navigation.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(next, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = activity.dp(8) })
        column.addView(navigation)
        val credits = MaterialButton(activity).apply {
            text = "Wallpaper credits and licenses"
            setOnClickListener { showCredits() }
        }
        column.addView(credits, LinearLayout.LayoutParams(-1, -2))
        dialog = MaterialAlertDialogBuilder(activity).setView(column).create()
        dialog.setOnDismissListener {
            generation++
            preview.setImageDrawable(null)
            bitmap?.recycle()
            bitmap = null
        }
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.94f).toInt(), -2)
        load()
    }

    private fun iconButton(icon: Int, description: String) = MaterialButton(activity).apply {
        setIconResource(icon)
        iconTint = ColorStateList.valueOf(ThemeColors.icon(activity))
        contentDescription = description
        minWidth = 0
        minimumWidth = 0
        setPadding(activity.dp(8), 0, activity.dp(8), 0)
    }

    private fun move(direction: Int) {
        val destination = (index + direction).coerceIn(0, count - 1)
        if (destination != index) { index = destination; load() }
    }

    private fun load() {
        val request = ++generation
        ready = false
        preview.setImageDrawable(null)
        bitmap?.recycle()
        bitmap = null
        val item = WallpaperArt.commons.getOrNull(index - 3)
        title.text = if (item == null) listOf("Fern · abstract", "Ember · mountain", "Dusk · mountain")[index]
            else "${item.color} · ${item.title}"
        subtitle.text = if (item == null) "${index + 1} of $count · Grove original"
            else "${index + 1} of $count · ${item.author} · ${item.license}"
        status.text = if (item == null) "Preparing preview…" else "Loading preview…"
        preview.contentDescription = "Preview of ${title.text}. Loading."
        retry.visibility = View.GONE
        previous.isEnabled = index > 0
        next.isEnabled = index < count - 1
        controller.preview(index) { image ->
            if (request != generation || !dialog.isShowing) { image?.recycle(); return@preview }
            if (image == null) {
                status.text = "Could not load this wallpaper. Check your connection and retry."
                retry.visibility = View.VISIBLE
                preview.contentDescription = "Preview unavailable for ${title.text}"
            } else {
                bitmap = image
                preview.setImageBitmap(image)
                ready = true
                status.text = "Tap the preview to choose where to set it. Swipe to browse."
                preview.contentDescription = "Preview of ${title.text}. Tap to choose a screen."
            }
        }
    }

    private fun chooseDestination() {
        if (!ready) return
        val chosenIndex = index
        MaterialAlertDialogBuilder(activity)
            .setTitle("Set ${title.text}")
            .setItems(arrayOf("Home screen", "Lock screen", "Both screens")) { _, choice ->
                val flags = when (choice) {
                    0 -> WallpaperManager.FLAG_SYSTEM
                    1 -> WallpaperManager.FLAG_LOCK
                    else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                dialog.dismiss()
                selected(chosenIndex, flags)
            }
            .setNegativeButton("Back", null)
            .show()
    }

    private fun showCredits() {
        val credits = WallpaperArt.commons.joinToString("\n\n") {
            "${it.color}: ${it.title} — ${it.author}\n${it.sourcePage}\n${it.license}"
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Wallpaper sources")
            .setMessage("Images hosted by Wikimedia Commons. Open each source for its license and terms.\n\n$credits")
            .setPositiveButton("Done", null)
            .show()
    }
}
