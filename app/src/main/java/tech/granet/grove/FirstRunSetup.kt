package tech.granet.grove

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import tech.granet.grove.ui.dp

/** Stable shell; page answers are provisional until a successful Finish commit. */
internal class FirstRunSetup(
    originalContext: Context,
    private val host: FrameLayout,
    private val underlay: View,
    private val initial: Config,
    private val apps: List<Pair<String, String>>,
    private val hasContacts: () -> Boolean,
    private val hasFiles: () -> Boolean,
    private val requestContacts: () -> Unit,
    private val requestFiles: () -> Unit,
    private val freshInstall: Boolean,
    private val restored: Bundle?,
    private val loadBackdrop: ((Bitmap?) -> Unit) -> Unit,
    private val finish: (Config) -> Unit,
    private val skip: () -> Unit,
) {
    private val context = try { DynamicColors.wrapContextIfAvailable(originalContext) }
        catch (error: Exception) {
            Log.w("Grove", "Setup dynamic colors unavailable; using theme", error)
            originalContext
        }
    private val activity = originalContext as? android.app.Activity
    private val state = FirstRunState(initial, apps.map { it.first })
    private val ui = FirstRunComponents(context)
    private val motion = FirstRunMotion()
    private var overlay: FrameLayout? = null
    private var pane: LinearLayout? = null
    private var pageHost: FrameLayout? = null
    private var pageView: View? = null
    private var backdrop: Bitmap? = null
    private var backdropView: ImageView? = null
    private var originalStatusBars: Int? = null
    private var fadePlayed = restored != null
    private lateinit var counter: android.widget.TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var previous: MaterialButton
    private lateinit var next: MaterialButton
    private val pages = FirstRunPages(context, state, apps, hasContacts, hasFiles,
        requestContacts, requestFiles, ::refreshPage)

    init {
        if (restored != null) try {
            val config = ConfigStore.parse(requireNotNull(restored.getString("answers")))
            state.restore(config, restored.getInt("page"), restored.getBoolean("up"),
                restored.getBoolean("down"), restored.getBoolean("hold"))
        } catch (error: Exception) { Log.w("Grove", "Setup snapshot unavailable; retaining saved settings", error) }
    }

    fun saveState(): Bundle = Bundle().apply {
        putString("answers", state.snapshot().json()); putInt("page", state.page)
        putBoolean("up", state.practicedUp); putBoolean("down", state.practicedDown)
        putBoolean("hold", state.practicedHold)
    }

    fun show() {
        if (overlay != null) { refreshPage(); return }
        val bars = activity?.window?.insetsController
        originalStatusBars = bars?.systemBarsAppearance
        bars?.setSystemBarsAppearance(if (Color.luminance(ui.canvas) > .5)
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        val shell = object : FrameLayout(context) {
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
                motion.busy || super.onInterceptTouchEvent(event)
        }.apply { isClickable = true }
        overlay = shell
        underlay.visibility = View.INVISIBLE
        if (freshInstall) {
            // A cheap Fern palette is the fallback while optional artwork prepares off-thread.
            shell.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xff9ab095.toInt(), 0xff416e60.toInt(), 0xff142f30.toInt()))
            val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
            backdropView = image
            shell.addView(image, FrameLayout.LayoutParams(-1, -1))
            loadBackdrop { bitmap ->
                if (overlay !== shell || activity?.isDestroyed == true) bitmap?.recycle()
                else { backdrop = bitmap; image.setImageBitmap(bitmap) }
            }
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true; isFocusableInTouchMode = true
            background = GradientDrawable().apply { setColor(ui.canvas); cornerRadius = context.dp(28).toFloat() }
            setPadding(context.dp(20), context.dp(16), context.dp(20), context.dp(16))
        }
        pane = panel
        shell.addView(panel, FrameLayout.LayoutParams(-1, -1).apply { setMargins(context.dp(16), context.dp(16), context.dp(16), context.dp(16)) })
        shell.setOnApplyWindowInsetsListener { _, insets ->
            val edges = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            val left = context.dp(16) + edges.left; val top = context.dp(16) + edges.top
            val right = context.dp(16) + edges.right; val bottom = context.dp(16) + edges.bottom
            val current = panel.layoutParams as FrameLayout.LayoutParams
            if (current.leftMargin != left || current.topMargin != top ||
                current.rightMargin != right || current.bottomMargin != bottom)
                panel.layoutParams = FrameLayout.LayoutParams(-1, -1).apply { setMargins(left, top, right, bottom) }
            insets
        }
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ui.text("GROVE", 15f, true, ui.primary).apply { letterSpacing = .14f }, LinearLayout.LayoutParams(0, -2, 1f))
        counter = ui.text("", 14f, true, ui.muted); header.addView(counter)
        panel.addView(header)
        progress = LinearProgressIndicator(context).apply { trackColor = ui.accent; setIndicatorColor(ui.primary) }
        panel.addView(progress, LinearLayout.LayoutParams(-1, context.dp(5)).apply { topMargin = context.dp(12) })
        pageHost = FrameLayout(context).also { panel.addView(it, LinearLayout.LayoutParams(-1, 0, 1f)) }
        val navigation = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        previous = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setIconResource(R.drawable.ic_setup_back); minimumHeight = context.dp(52)
            setOnClickListener { back() }
        }
        next = MaterialButton(context).apply {
            setIconResource(R.drawable.ic_setup_next); iconGravity = MaterialButton.ICON_GRAVITY_END
            minimumHeight = context.dp(52)
            setOnClickListener {
                if (!motion.busy) {
                    val result = state.next(hasContacts(), hasFiles())
                    if (result == null) displayPage(forward = true) else { close(); finish(result) }
                }
            }
        }
        navigation.addView(previous, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(next, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(12) })
        panel.addView(navigation)
        displayPage()
        if (!fadePlayed) panel.alpha = 0f
        host.addView(shell, FrameLayout.LayoutParams(-1, -1)); shell.requestApplyInsets()
        if (!fadePlayed) {
            fadePlayed = true
            updateNavigation(enabled = false)
            motion.fade(panel) { updateNavigation() }
        }
    }

    fun refreshPermissions() {
        settleMotion()
        if (state.page == 6 || state.page == 7) refreshPage()
    }
    fun settleMotion() { motion.finish(); pane?.alpha = 1f }
    fun back() {
        if (motion.busy) return
        if (state.back()) displayPage(forward = false) else { close(); skip() }
    }
    private fun refreshPage() { settleMotion(); displayPage() }
    private fun displayPage(forward: Boolean? = null) {
        val target = pageHost ?: return
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (state.page in 0..4) gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(20), 0, context.dp(20))
        }
        pages.render(state.page, content)
        val incoming = ScrollView(context).apply { isFillViewport = true; addView(content) }
        val outgoing = pageView
        pageView = incoming
        target.addView(incoming, FrameLayout.LayoutParams(-1, -1))
        updateNavigation(enabled = forward == null)
        if (forward == null) { outgoing?.let(target::removeView); updateNavigation() }
        else motion.slide(target, outgoing, incoming, forward) { updateNavigation() }
    }
    private fun updateNavigation(enabled: Boolean = true) {
        val visible = state.pages(); val current = visible.indexOf(state.page) + 1
        counter.text = "$current / ${visible.size}"
        progress.max = visible.size; progress.setProgressCompat(current, false)
        previous.text = if (state.page == 0) "Skip" else "Back"
        next.text = if (state.page == 7) "Finish" else "Next"
        previous.isEnabled = enabled; next.isEnabled = enabled
    }
    private fun close() {
        settleMotion()
        overlay?.let(host::removeView); overlay = null
        pageHost = null; pageView = null; pane = null
        underlay.visibility = View.VISIBLE
        backdropView?.setImageDrawable(null); backdropView = null
        backdrop?.recycle(); backdrop = null
        originalStatusBars?.let { activity?.window?.insetsController?.setSystemBarsAppearance(it,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS) }
        originalStatusBars = null
    }
    fun destroy() = close()
}
