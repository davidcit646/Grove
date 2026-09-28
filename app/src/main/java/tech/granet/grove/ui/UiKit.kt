package tech.granet.grove.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import tech.granet.grove.R
import tech.granet.grove.ThemeColors

/**
 * Grove's reusable UI kit — the "CSS library". Every dimension is dp, every font size
 * sp, every color comes from [ThemeColors]. Screens compose these instead of
 * hand-building views, so the whole launcher shares one look and one set of metrics.
 */

/** Density-independent pixels. */
fun Context.dp(n: Int): Int = (n * resources.displayMetrics.density).toInt()

/** Short user-facing message. */
fun Context.message(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

/**
 * Standard label: white text with comfortable vertical padding.
 * [horizontalPaddingDp] is 0 in lists and 12 inside padded cards.
 */
fun Context.label(text: String, size: Float = 16f, horizontalPaddingDp: Int = 0): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        val h = dp(horizontalPaddingDp)
        setPadding(h, dp(8), h, dp(8))
    }

/** Section title: bold, in the theme's icon color. */
fun Context.titleText(text: String, topPaddingDp: Int = 0): TextView = TextView(this).apply {
    this.text = text
    textSize = 19f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(ThemeColors.icon(this@titleText))
    setPadding(0, dp(topPaddingDp), 0, dp(10))
}

/** Secondary body copy. */
fun Context.bodyText(text: String, size: Float = 13f): TextView = TextView(this).apply {
    this.text = text
    textSize = size
    setPadding(0, 0, 0, dp(8))
}

/** Warning copy in the standard danger red. */
fun Context.warningText(text: String): TextView = bodyText(text).apply {
    setTextColor(0xff9b4138.toInt())
    setPadding(0, dp(4), 0, dp(10))
}

data class MenuRow(val label: String, val icon: Int, val action: () -> Unit)

/**
 * Tappable icon row: icon + title + optional subtitle. The workhorse list row behind
 * search results and menus. [titleColor] null leaves the theme default in place.
 */
fun Context.iconRow(
    title: String,
    iconId: Int,
    subtitle: String? = null,
    bitmap: Bitmap? = null,
    iconTag: Any? = null,
    titleColor: Int? = Color.WHITE,
    titleSp: Float = 17f,
    subtitleSp: Float = 12f,
    minHeightDp: Int = 56,
    iconDp: Int = 32,
    iconMarginEndDp: Int = 16,
    horizontalPaddingDp: Int = 12,
    verticalPaddingDp: Int = 4,
    contentDescription: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): View = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    minimumHeight = dp(minHeightDp)
    setPadding(dp(horizontalPaddingDp), dp(verticalPaddingDp), dp(horizontalPaddingDp), dp(verticalPaddingDp))
    if (contentDescription != null) this.contentDescription = contentDescription
    addView(ImageView(context).apply {
        tag = iconTag
        if (bitmap != null) setImageBitmap(bitmap)
        else {
            setImageResource(iconId)
            imageTintList = ColorStateList.valueOf(ThemeColors.icon(context))
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }, LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)).apply { marginEnd = dp(iconMarginEndDp) })
    val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    texts.addView(TextView(context).apply {
        text = title
        textSize = titleSp
        if (titleColor != null) setTextColor(titleColor)
    })
    if (subtitle != null) texts.addView(TextView(context).apply {
        text = subtitle
        textSize = subtitleSp
        if (titleColor != null) setTextColor(titleColor)
        alpha = 0.75f
    })
    addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
    setOnClickListener { onClick() }
    isLongClickable = onLongClick != null
    setOnLongClickListener { onLongClick?.invoke(); onLongClick != null }
}

/** Action menu: title, scrollable icon rows, Cancel. Tapping a row dismisses then acts. */
fun Context.menuDialog(title: String, rows: List<MenuRow>): AlertDialog {
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, dp(8))
    }
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(ScrollView(this).apply {
            addView(list)
            layoutParams = ViewGroup.LayoutParams(-1, -2)
        })
        .setNegativeButton("Cancel", null)
        .create()
    rows.forEach { row ->
        list.addView(iconRow(row.label, row.icon,
            titleColor = null, titleSp = 16f,
            minHeightDp = 64, iconDp = 24, iconMarginEndDp = 24,
            horizontalPaddingDp = 24, verticalPaddingDp = 12,
            contentDescription = row.label,
            onClick = { dialog.dismiss(); row.action() }
        ).apply { isFocusable = true }, LinearLayout.LayoutParams(-1, -2))
    }
    dialog.show()
    return dialog
}

/** Simple title + message + one button. */
fun Context.infoDialog(title: String, message: String, button: String = "OK"): AlertDialog =
    MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
        .setPositiveButton(button, null).show()

/** Title + message + Cancel / confirm. */
fun Context.confirmDialog(title: String, message: String, positive: String, onConfirm: () -> Unit): AlertDialog =
    MaterialAlertDialogBuilder(this).setTitle(title).setMessage(message)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(positive) { _, _ -> onConfirm() }.show()

/** Plain text list picker; [onPick] receives the tapped index. */
fun Context.listDialog(title: String, items: List<String>, negative: String? = null, onPick: (Int) -> Unit): AlertDialog =
    MaterialAlertDialogBuilder(this).setTitle(title)
        .setItems(items.toTypedArray()) { _, index -> onPick(index) }
        .apply { if (negative != null) setNegativeButton(negative, null) }.show()

/** Title + scrolling custom content + a Done-style button. */
fun Context.scrollDialog(title: String, content: View, positive: String = "Done"): AlertDialog =
    MaterialAlertDialogBuilder(this).setTitle(title)
        .setView(ScrollView(this).apply { addView(content) })
        .setPositiveButton(positive, null).show()

/** Single-line text field with Cancel / save. */
fun Context.inputDialog(
    title: String,
    initial: String,
    hint: String = "",
    inputType: Int = InputType.TYPE_CLASS_TEXT,
    onSave: (String) -> Unit,
): AlertDialog {
    val edit = EditText(this).apply {
        setText(initial)
        this.hint = hint
        this.inputType = inputType
        setSingleLine(true)
        setSelection(text.length)
    }
    val padded = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(8), dp(20), dp(4))
        addView(edit, LinearLayout.LayoutParams(-1, -2))
    }
    return MaterialAlertDialogBuilder(this).setTitle(title).setView(padded)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Save") { _, _ -> onSave(edit.text.toString()) }.show()
}

/** Text button with a leading icon, tinted to the theme (settings-screen style). */
fun Context.settingsButton(text: String, iconId: Int, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).apply {
        this.text = text
        setIconResource(iconId)
        iconTint = ColorStateList.valueOf(ThemeColors.icon(this@settingsButton))
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        setOnClickListener { onClick() }
    }

/** Full-width switch row. */
fun Context.toggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit): SwitchMaterial =
    SwitchMaterial(this).apply {
        this.text = text
        isChecked = checked
        setPadding(0, dp(4), 0, dp(4))
        setOnCheckedChangeListener { _, value -> onChange(value) }
    }

/**
 * Appends a settings section (divider + bold title) to this vertical column.
 * The first section gets no divider and slightly more top padding.
 */
fun LinearLayout.addSection(title: String) {
    val first = childCount == 0
    if (!first) {
        val ink = ThemeColors.icon(context)
        addView(View(context).apply {
            setBackgroundColor(Color.argb(64, Color.red(ink), Color.green(ink), Color.blue(ink)))
        }, LinearLayout.LayoutParams(-1, context.dp(1)).apply {
            topMargin = context.dp(22)
            bottomMargin = context.dp(14)
        })
    }
    addView(context.titleText(title, topPaddingDp = if (first) 12 else 0))
}
