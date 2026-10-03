package tech.granet.grove

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
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

    private fun color(attr: Int, fallback: Int) = MaterialColors.getColor(context, attr, fallback)
    private val ink get() = color(com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private val muted get() = color(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xff454545.toInt())
    private val primary get() = color(com.google.android.material.R.attr.colorPrimary, 0xff315b47.toInt())
    private val onAccent get() = color(com.google.android.material.R.attr.colorOnPrimaryContainer, 0xff183526.toInt())
    private val accent get() = color(com.google.android.material.R.attr.colorPrimaryContainer, 0xffd8e8d9.toInt())
    private val surface get() = color(com.google.android.material.R.attr.colorSurface, Color.WHITE)
    private val canvas get() = color(com.google.android.material.R.attr.colorSurfaceContainerLow, 0xfffafafa.toInt())
    private fun text(value: String, size: Float = 16f, bold: Boolean = false, color: Int = ink) =
        TextView(context).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    private fun icon(id: Int, tint: Int = primary, size: Int = 32) = ImageView(context).apply {
        setImageResource(id)
        imageTintList = ColorStateList.valueOf(tint)
        layoutParams = LinearLayout.LayoutParams(context.dp(size), context.dp(size))
    }
    private fun card(parent: LinearLayout, highlighted: Boolean = false, build: (LinearLayout) -> Unit) {
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
    private fun heading(parent: LinearLayout, eyebrow: String, title: String, summary: String) {
        parent.addView(text(eyebrow.uppercase(), 12f, true, primary).apply { letterSpacing = .12f })
        parent.addView(text(title, 30f, true), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(8)
        })
        parent.addView(text(summary, 16f, color = muted), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = context.dp(12); bottomMargin = context.dp(8)
        })
    }
    private fun feature(parent: LinearLayout, iconId: Int, title: String, body: String, highlighted: Boolean = false) {
        card(parent, highlighted) { box ->
            box.addView(icon(iconId, if (highlighted) onAccent else primary))
            box.addView(text(title, 18f, true, if (highlighted) onAccent else ink),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(14) })
            box.addView(text(body, 15f, color = if (highlighted) onAccent else muted),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(7) })
        }
    }
    private fun choice(parent: LinearLayout, iconId: Int, title: String, detail: String,
                       enabled: Boolean, changed: (Boolean) -> Unit) {
        card(parent) { box ->
            val row = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            row.addView(icon(iconId))
            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(title, 17f, true))
                addView(text(detail, 14f, color = muted))
            }
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(14) })
            val switch = SwitchMaterial(context).apply {
                isChecked = enabled
                contentDescription = title
                setOnCheckedChangeListener { _, checked -> changed(checked) }
            }
            row.addView(switch, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(8) })
            box.addView(row)
            box.setOnClickListener { switch.isChecked = !switch.isChecked }
        }
    }
    private fun action(parent: LinearLayout, label: String, click: () -> Unit) {
        parent.addView(MaterialButton(context).apply {
            text = label
            textSize = 16f
            minimumHeight = context.dp(54)
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(16) })
    }

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
            if (Color.luminance(canvas) > .5) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        val screen = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusableInTouchMode = true
            setBackgroundColor(canvas)
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
        header.addView(text("GROVE", 15f, true, primary).apply { letterSpacing = .14f },
            LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(text("$current / ${visible.size}", 14f, true, muted))
        screen.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(14) })
        screen.addView(LinearProgressIndicator(context).apply {
            max = visible.size
            setProgressCompat(current, false)
            trackColor = accent
            setIndicatorColor(primary)
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

        when (state.page) {
            0 -> {
                heading(content, "Welcome", "Make Home yours.",
                    "A calmer state.home screen, set up your way. This takes just a minute.")
                card(content, true) { box ->
                    box.minimumHeight = context.dp(270)
                    box.addView(icon(R.drawable.ic_grove, onAccent, 64))
                    box.addView(text("Your phone. Your pace.", 24f, true, onAccent),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(24) })
                    box.addView(text("Choose how to move around, what appears on Home, and what Grove can search.",
                        16f, color = onAccent), LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = context.dp(10)
                    })
                }
                feature(content, R.drawable.ic_settings, "You stay in control",
                    "Choose Grove as your Home app at the end. You can change every choice later in Launcher settings.")
            }
            1 -> {
                heading(content, "Navigation", "Make it a gesture.",
                    "Choose the swipes you want on Home. The on-screen buttons still work.")
                choice(content, R.drawable.ic_search, "Swipe down", "Open Search",
                    state.gestures.swipeDownSearch) { state.gestures = state.gestures.copy(swipeDownSearch = it) }
                choice(content, R.drawable.ic_grid, "Swipe up", "Open all apps",
                    state.gestures.swipeUpAppDrawer) { state.gestures = state.gestures.copy(swipeUpAppDrawer = it) }
                feature(content, R.drawable.ic_info, "Prefer buttons?",
                    "Turn both swipes off and we'll skip the practice step.")
            }
            2 -> {
                heading(content, "Practice", "Try your swipes.",
                    "Swipe inside the card. You can continue without practicing.")
                card(content, true) { box ->
                    val status = text("", 16f, true, onAccent)
                    fun update() {
                        status.text = buildList {
                            if (state.gestures.swipeUpAppDrawer) add("↑  All apps  ·  ${if (state.practicedUp) "Done" else "Try it"}")
                            if (state.gestures.swipeDownSearch) add("↓  Search  ·  ${if (state.practicedDown) "Done" else "Try it"}")
                        }.joinToString("\n")
                    }
                    update()
                    box.addView(status)
                    box.addView(text("Swipe here", 24f, true, onAccent).apply {
                        gravity = Gravity.CENTER
                        contentDescription = "Practice the enabled Home swipes. Next skips practice."
                        background = GradientDrawable().apply {
                            setColor(surface)
                            cornerRadius = context.dp(20).toFloat()
                        }
                        var startY = 0f
                        isClickable = true
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> {
                                    view.parent.requestDisallowInterceptTouchEvent(true)
                                    startY = event.y
                                    true
                                }
                                MotionEvent.ACTION_UP -> {
                                    if (event.y - startY > context.dp(55) && state.gestures.swipeDownSearch) state.practicedDown = true
                                    if (event.y - startY < -context.dp(55) && state.gestures.swipeUpAppDrawer) state.practicedUp = true
                                    update()
                                    view.performClick()
                                    true
                                }
                                else -> true
                            }
                        }
                    }, LinearLayout.LayoutParams(-1, context.dp(220)).apply { topMargin = context.dp(18) })
                }
            }
            3 -> {
                heading(content, "Quick access", "Settings are a hold away.",
                    "Press and hold empty Home space to open Grove settings, even if you hide the buttons.")
                card(content, true) { box ->
                    box.minimumHeight = context.dp(220)
                    box.addView(icon(R.drawable.ic_settings, onAccent, 48))
                    val feedback = text(if (state.practicedHold) "You got it!" else "Try holding this card",
                        20f, true, onAccent)
                    box.addView(feedback, LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = context.dp(24)
                    })
                    box.addView(text("Press and hold anywhere in this space.", 15f, color = onAccent),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(8) })
                    box.contentDescription = "Practice holding empty Home space to open launcher settings"
                    box.isLongClickable = true
                    box.setOnLongClickListener {
                        state.practicedHold = true
                        feedback.text = "You got it!"
                        true
                    }
                }
            }
            4 -> {
                heading(content, "Home", "Keep what matters.",
                    "Choose what appears when you unlock your phone.")
                choice(content, R.drawable.ic_search, "Search button", "Find apps and more",
                    state.home.showSearchButton) { state.home = state.home.copy(showSearchButton = it) }
                choice(content, R.drawable.ic_grid, "All apps button", "Open the app drawer",
                    state.home.showAppsButton) { state.home = state.home.copy(showAppsButton = it) }
                choice(content, R.drawable.ic_home, "Clock and date", "Time at a glance",
                    state.home.showClock) { state.home = state.home.copy(showClock = it) }
                choice(content, R.drawable.ic_star, "Pinned apps", "Your chosen shortcuts",
                    state.home.showPinnedApps) { state.home = state.home.copy(showPinnedApps = it) }
            }
            5 -> {
                heading(content, "Shortcuts", "Pin your favorites.",
                    "Choose up to 12 apps. Hold and drag them into place later.")
                val counter = text("", 15f, true, primary)
                fun updateCounter() { counter.text = "${state.pins.size} of 12 selected" }
                updateCounter()
                content.addView(counter, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = context.dp(8); bottomMargin = context.dp(12)
                })
                val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val search = EditText(context).apply {
                    hint = "Find an app"
                    setSingleLine()
                    contentDescription = "Find an app to pin"
                    setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
                    background = GradientDrawable().apply {
                        setColor(surface); cornerRadius = context.dp(18).toFloat()
                    }
                }
                content.addView(search)
                fun updateList(query: String) {
                    list.removeAllViews()
                    apps.filter { it.second.contains(query, ignoreCase = true) }
                        .sortedWith(compareByDescending<Pair<String, String>> { it.first in state.pins }
                            .thenBy { it.second.lowercase() })
                        .take(80).forEach { (key, name) ->
                        val row = LinearLayout(context).apply {
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(context.dp(8), context.dp(3), context.dp(8), context.dp(3))
                        }
                        AppIconStore[key]?.let { bitmap ->
                            row.addView(ImageView(context).apply { setImageBitmap(bitmap) },
                                LinearLayout.LayoutParams(context.dp(36), context.dp(36)))
                        }
                        row.addView(CheckBox(context).apply {
                            text = name
                            textSize = 16f
                            isChecked = key in state.pins
                            setOnCheckedChangeListener { button, checked ->
                                if (!state.togglePin(key, checked)) {
                                    button.isChecked = false
                                    return@setOnCheckedChangeListener
                                }
                                updateCounter()
                            }
                        }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = context.dp(8) })
                        list.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                            bottomMargin = context.dp(3)
                        })
                    }
                }
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateList(s.toString())
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                updateList("")
                content.addView(list, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = context.dp(12)
                })
            }
            6 -> {
                heading(content, "Recommended", "People, one search away.",
                    "Type a name to call, text, or open a contact card. Android sends the selected number to the app you choose.")
                feature(content, R.drawable.ic_contact, "Why enable it?",
                    "Reach people without opening your contacts app.", true)
                feature(content, R.drawable.ic_info, "Why leave it off?",
                    "Fewer results can mean faster searches and less battery use.")
                feature(content, R.drawable.ic_home, "Private and on your device",
                    "Grove reads names and numbers only while enabled. They stay in memory. GraNet does not collect contacts; no copy is uploaded.")
                if (!state.search.contacts || !hasContacts()) action(content, "Enable contact search") {
                    state.search = state.search.copy(contacts = true)
                    render()
                    if (!hasContacts()) requestContacts()
                } else feature(content, R.drawable.ic_contact, "Contact search enabled",
                    "Names are ready to appear in Grove Search.", true)
                if (state.search.contacts) action(content, "Don't use contact search") {
                    state.search = state.search.copy(contacts = false); render()
                }
                content.addView(text("Turning this off stops reads and hides results. Android keeps a granted permission until you revoke it in system settings.",
                    14f, color = muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(16) })
            }
            7 -> {
                heading(content, "Optional", "Search files on your phone.",
                    "You probably don't need this. It's here for on-device music, documents, and other files you want to find by name.")
                feature(content, R.drawable.ic_document, "When it helps",
                    "Find local files without browsing folders.", true)
                feature(content, R.drawable.ic_info, "Why leave it off?",
                    "Skipping storage scans can make searches faster and use less battery.")
                feature(content, R.drawable.ic_home, "What Android grants",
                    "All files access grants broad read and write access to shared storage, but not other apps' private data or system partitions. Grove indexes names and paths in memory. It does not read contents, change files, or upload the index. GraNet does not collect your file list. Opening a result shares only that file with the app you choose.")
                if (!state.search.files || !hasFiles()) action(content, "Enable file search") {
                    state.search = state.search.copy(files = true)
                    render()
                    if (!hasFiles()) requestFiles()
                } else feature(content, R.drawable.ic_document, "File search enabled",
                    "Local file names can appear in Grove Search.", true)
                if (state.search.files) action(content, "Don't use file search") {
                    state.search = state.search.copy(files = false); render()
                }
                content.addView(text("Turning this off clears the in-memory index and hides results. Android keeps a granted permission until you revoke it in system settings.",
                    14f, color = muted), LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(16) })
            }
        }
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
