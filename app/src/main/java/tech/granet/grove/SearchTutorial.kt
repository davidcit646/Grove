package tech.granet.grove

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import tech.granet.grove.ui.dp

/** Full-screen Material You feature guide. Samples are static; this surface owns only views/motion. */
internal class SearchTutorial(original: Context, private val host: FrameLayout, private val underlay: View,
    private val state: SearchTutorialState, private val availability: SearchTutorialAvailability,
    private val forward: () -> Unit, private val back: () -> Unit) {
    private val context = try { DynamicColors.wrapContextIfAvailable(original) }
        catch (error: Exception) { android.util.Log.w("Grove", "Search tutorial colors unavailable", error); original }
    private val activity = original as? android.app.Activity
    private val ui = FirstRunComponents(context)
    private val motion = FirstRunMotion()
    val busy get() = motion.busy
    private var shell: FrameLayout? = null
    private lateinit var body: FrameLayout
    private var current: View? = null
    private lateinit var counter: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var previous: MaterialButton
    private lateinit var next: MaterialButton
    private var originalBars: Int? = null
    fun show() {
        if (shell != null) return
        val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        originalBars = activity?.window?.insetsController?.systemBarsAppearance
        activity?.window?.insetsController?.setSystemBarsAppearance(if (Color.luminance(ui.canvas) > .5) flags else 0, flags)
        val overlay = TutorialSwipeHost(context, { busy }) { if (it) forward() else back() }.apply {
            isClickable = true; isFocusableInTouchMode = true; setBackgroundColor(ui.canvas)
        }
        shell = overlay
        underlay.visibility = View.INVISIBLE
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(24), context.dp(20), context.dp(24), context.dp(20))
        }
        overlay.addView(panel, FrameLayout.LayoutParams(-1, -1))
        overlay.setOnApplyWindowInsetsListener { _, insets ->
            val edges = insets.getInsets(WindowInsets.Type.systemBars())
            panel.setPadding(context.dp(24) + edges.left, context.dp(20) + edges.top,
                context.dp(24) + edges.right, context.dp(20) + edges.bottom)
            insets
        }
        counter = centered(context.getString(R.string.search_tutorial_title), 16f, true)
        panel.addView(counter, LinearLayout.LayoutParams(-1, -2))
        progress = LinearProgressIndicator(context).apply { max = 3; trackColor = ui.accent; setIndicatorColor(ui.primary) }
        panel.addView(progress, LinearLayout.LayoutParams(-1, context.dp(5)).apply { topMargin = context.dp(12) })
        body = FrameLayout(context)
        panel.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        val navigation = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        previous = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setIconResource(R.drawable.ic_setup_back); minimumHeight = context.dp(52)
            setOnClickListener { if (!busy) back() }
        }
        next = MaterialButton(context).apply {
            setIconResource(R.drawable.ic_setup_next); iconGravity = MaterialButton.ICON_GRAVITY_END
            minimumHeight = context.dp(52); setOnClickListener { if (!busy) forward() }
        }
        navigation.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(next, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(12) })
        panel.addView(navigation)
        host.addView(overlay, FrameLayout.LayoutParams(-1, -1))
        page(null); overlay.requestApplyInsets(); overlay.requestFocus()
    }
    private fun centered(value: String, size: Float, bold: Boolean = false) = ui.text(value, size, bold).apply { gravity = Gravity.CENTER }
    private fun feature(parent: LinearLayout, icon: Int, title: Int, summary: Int, example: Int?, enabled: Boolean, access: Boolean = true) {
        ui.card(parent, true) { card ->
            card.gravity = Gravity.CENTER
            card.addView(ui.icon(icon, ui.onAccent, 40))
            card.addView(centered(context.getString(title), 22f, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
            card.addView(centered(context.getString(summary), 16f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(6) })
            example?.let { card.addView(centered(context.getString(it), 18f, true), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) }) }
            if (!enabled || !access) card.addView(centered(context.getString(if (!enabled)
                R.string.search_tutorial_disabled else R.string.search_tutorial_access), 14f),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
        }
    }
    fun page(forward: Boolean?) {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(0, context.dp(16), 0, context.dp(16))
        }
        when (state.page) {
            0 -> {
                feature(content, R.drawable.ic_calculate, R.string.search_tutorial_calculator, R.string.search_tutorial_calculator_detail,
                    R.string.search_tutorial_calculator_example, availability.calculator)
                feature(content, R.drawable.ic_setup_apps, R.string.search_tutorial_apps, R.string.search_tutorial_apps_detail, null, true)
            }
            1 -> {
                feature(content, R.drawable.ic_setup_person, R.string.search_tutorial_contacts, R.string.search_tutorial_contacts_detail,
                    null, availability.contacts, availability.contactAccess)
                feature(content, R.drawable.ic_setup_folder, R.string.search_tutorial_files, R.string.search_tutorial_files_detail,
                    null, availability.files, availability.fileAccess)
            }
            else -> {
                feature(content, R.drawable.ic_setup_settings, R.string.search_tutorial_grove, R.string.search_tutorial_grove_detail, null, availability.groveSettings)
                feature(content, R.drawable.ic_settings_wifi, R.string.search_tutorial_android, R.string.search_tutorial_android_detail, null, availability.androidSettings)
            }
        }
        val incoming = ScrollView(context).apply { isFillViewport = true; addView(content) }
        val outgoing = current; current = incoming
        body.addView(incoming, FrameLayout.LayoutParams(-1, -1))
        navigation(forward == null)
        if (forward == null) { outgoing?.let(body::removeView); navigation(true) }
        else motion.slide(body, outgoing, incoming, forward) { navigation(true) }
    }
    private fun navigation(enabled: Boolean) {
        counter.text = context.getString(R.string.search_tutorial_progress, state.page + 1)
        progress.setProgressCompat(state.page + 1, false)
        previous.setText(if (state.page == 0) R.string.search_tutorial_close else R.string.search_tutorial_back)
        next.setText(if (state.page == 2) R.string.search_tutorial_finish else R.string.search_tutorial_next)
        previous.isEnabled = enabled; next.isEnabled = enabled
    }
    fun settle() = motion.finish()
    fun destroy() {
        settle(); shell?.let(host::removeView); shell = null; current = null
        underlay.visibility = View.VISIBLE
        originalBars?.let { activity?.window?.insetsController?.setSystemBarsAppearance(it,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS) }
        originalBars = null
    }
}
