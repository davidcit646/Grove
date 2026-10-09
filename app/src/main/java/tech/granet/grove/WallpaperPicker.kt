package tech.granet.grove

import android.app.WallpaperManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.text.util.Linkify
import android.text.method.LinkMovementMethod
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.dp

/** Responsive wallpaper library. Preview work is generation-owned and selection commits only after Android apply. */
internal class WallpaperPicker(
    private val activity: MainActivity,
    private val controller: WallpaperController,
    initialIndex: Int,
    private val selected: (Int, Int) -> Unit,
    private val chooseCustom: () -> Unit,
) {
    private val count = WallpaperArt.sources.size
    private var index = initialIndex.takeIf { WallpaperArt.source(it) != null } ?: 0
    @Volatile private var generation = 0
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
            setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(12))
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(activity).apply {
            text = "Wallpapers"
            textSize = 23f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemeColors.icon(activity))
        }, LinearLayout.LayoutParams(0, activity.dp(52), 1f))
        header.addView(iconButton(R.drawable.ic_close, "Close wallpaper picker").apply {
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)))
        column.addView(header)

        val details = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val detailsScroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(details)
        }
        column.addView(detailsScroll, LinearLayout.LayoutParams(-1, 0, 1f))

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
        val previewHeight = (activity.resources.displayMetrics.heightPixels * 0.34f).toInt()
            .coerceIn(activity.dp(140), activity.dp(360))
        details.addView(preview, LinearLayout.LayoutParams(-1, previewHeight))

        title = TextView(activity).apply {
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ThemeColors.icon(activity))
            setPadding(0, activity.dp(12), 0, 0)
        }
        details.addView(title)
        subtitle = TextView(activity).apply {
            textSize = 13f
            setTextColor(ThemeColors.icon(activity))
            setPadding(0, activity.dp(4), 0, 0)
        }
        details.addView(subtitle)
        status = TextView(activity).apply {
            textSize = 14f
            setTextColor(ThemeColors.icon(activity))
            setPadding(0, activity.dp(8), 0, activity.dp(4))
        }
        details.addView(status)
        retry = MaterialButton(activity).apply {
            text = "Retry preview"
            visibility = View.GONE
            setOnClickListener { load() }
        }
        details.addView(retry)

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
        column.addView(MaterialButton(activity).apply {
            text = "Choose photo or file"
            contentDescription = "Choose a custom wallpaper image"
            setOnClickListener { dialog.dismiss(); chooseCustom() }
        }, LinearLayout.LayoutParams(-1, -2))
        column.addView(MaterialButton(activity).apply {
            text = "Wallpaper credits and licenses"
            setOnClickListener { showCredits() }
        }, LinearLayout.LayoutParams(-1, -2))

        dialog = MaterialAlertDialogBuilder(activity).setView(column).create()
        dialog.setOnDismissListener {
            generation++
            preview.setImageDrawable(null)
            bitmap?.recycle()
            bitmap = null
        }
        dialog.show()
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.94f).toInt(),
            (activity.resources.displayMetrics.heightPixels * 0.88f).toInt(),
        )
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
        val source = WallpaperArt.source(index) ?: return
        val request = ++generation
        ready = false
        preview.setImageDrawable(null)
        bitmap?.recycle()
        bitmap = null
        title.text = source.title
        subtitle.text = "${index + 1} of $count · ${source.author} · ${source.license}"
        status.text = when (source.kind) {
            WallpaperKind.CUSTOM -> "Loading your selected local image…"
            WallpaperKind.COMMONS -> "Loading preview…"
            else -> "Preparing preview…"
        }
        preview.contentDescription = "Preview of ${source.title}. Loading."
        retry.visibility = View.GONE
        previous.isEnabled = index > 0
        next.isEnabled = index < count - 1
        controller.preview(index, current = { request == generation && !activity.isDestroyed }) { image ->
            if (request != generation || !dialog.isShowing) { image?.recycle(); return@preview }
            if (image == null) {
                val e = GroveErrorRegistry.WALLPAPER_PREVIEW
                status.text = if (source.kind == WallpaperKind.CUSTOM)
                    "No custom image is available. Choose a photo or file below."
                else "Code ${e.code} · ${e.gws}\nCould not load this wallpaper. Retry without leaving the library."
                retry.visibility = if (source.kind == WallpaperKind.CUSTOM) View.GONE else View.VISIBLE
                preview.contentDescription = "Preview unavailable for ${source.title}"
            } else {
                bitmap = image
                preview.setImageBitmap(image)
                ready = true
                status.text = "Tap the preview to choose Home, Lock, or Both. Swipe to browse."
                preview.contentDescription = "Preview of ${source.title}. Tap to choose a screen."
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
        val text = WallpaperArt.sources.joinToString("\n\n") { source ->
            buildString {
                append(source.title).append(" — ").append(source.author)
                append("\n").append(source.license)
                source.licenseUrl?.let { append("\n").append(it) }
                source.changes?.let { append("\n").append(it) }
                source.sourcePage?.let { append("\n").append(it) }
            }
        }
        val body = TextView(activity).apply {
            this.text = text
            textSize = 14f
            setTextColor(ThemeColors.icon(activity))
            setPadding(activity.dp(20), activity.dp(12), activity.dp(20), activity.dp(20))
            setTextIsSelectable(true)
            Linkify.addLinks(this, Linkify.WEB_URLS)
            movementMethod = LinkMovementMethod.getInstance()
        }
        MaterialAlertDialogBuilder(activity)
            .setTitle("Wallpaper sources")
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Done", null)
            .show()
    }
}
