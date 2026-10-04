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
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import tech.granet.grove.ui.dp

/** Full-screen, replayable setup. Answers are applied together on Finish. */
internal class FirstRunSetup(
    private val context: Context,
    private val host: FrameLayout,
    private val initial: Config,
    private val apps: List<Pair<String, String>>,
    private val hasContacts: () -> Boolean,
    private val hasFiles: () -> Boolean,
    private val contactState: () -> SearchSourceState,
    private val fileState: () -> SearchSourceState,
    private val requestContacts: () -> Unit,
    private val requestFiles: () -> Unit,
    private val finish: (Config) -> Unit,
    private val skip: () -> Unit,
) {
    private val state = FirstRunState(initial, apps.map { it.first })
    private var overlay: View? = null
    private var originalStatusBars: Int? = null

    private val ui = FirstRunComponents(context)
    private val pages = FirstRunPages(context, state, apps, hasContacts, hasFiles,
        contactState, fileState, requestContacts, requestFiles, ::render)

    fun show() = render()
    fun refreshPermissions() { if ((state.page == 6 || state.page == 7) && overlay != null) render() }
    fun back() {
        if (state.back()) render()
        else { close(); skip() }
    }
    private fun close() {
        overlay?.let(host::removeView)
        overlay = null
        originalStatusBars?.let { original ->
            (context as? android.app.Activity)?.window?.insetsController?.setSystemBarsAppearance(
                original, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        }
        originalStatusBars = null
    }

    private fun render() {
        overlay?.let(host::removeView)
        val barsController = (context as? android.app.Activity)?.window?.insetsController
        if (originalStatusBars == null) originalStatusBars = barsController?.systemBarsAppearance ?: 0
        barsController?.setSystemBarsAppearance(
            if (Color.luminance(ui.canvas) > .5) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        val screen = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusableInTouchMode = true
            setBackgroundColor(ui.canvas)
            setPadding(context.dp(20), context.dp(12), context.dp(20), context.dp(12))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(context.dp(20), bars.top + context.dp(12), context.dp(20), bars.bottom + context.dp(12))
                insets
            }
        }
        overlay = screen
        host.addView(screen, FrameLayout.LayoutParams(-1, -1))
        screen.requestApplyInsets()
        val visible = state.pages()
        val current = visible.indexOf(state.page) + 1
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ui.text("GROVE", 15f, true, ui.primary).apply { letterSpacing = .14f },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ui.text("$current / ${visible.size}", 14f, true, ui.muted))
        screen.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(14) })
        screen.addView(LinearProgressIndicator(context).apply {
            max = visible.size
            setProgressCompat(current, false)
            trackColor = ui.accent
            setIndicatorColor(ui.primary)
        }, LinearLayout.LayoutParams(-1, context.dp(5)))
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (state.page in 0..4) gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(22), 0, context.dp(24))
        }
        screen.addView(ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        },
            LinearLayout.LayoutParams(-1, 0, 1f))

        pages.render(state.page, content)
        val navigation = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        navigation.addView(MaterialButton(context, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = if (state.page == 0) "Skip setup" else "Back"
            textSize = 16f
            minimumHeight = context.dp(52)
            setOnClickListener { back() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(MaterialButton(context).apply {
            text = if (state.page == 7) "Finish" else "Next"
            textSize = 16f
            minimumHeight = context.dp(52)
            setOnClickListener {
                val completed = state.next(hasContacts(), hasFiles())
                if (completed == null) render()
                else { close(); finish(completed) }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(12) })
        screen.addView(navigation, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(8)
        })
    }
}
