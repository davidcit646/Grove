package tech.granet.grove

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.ImageView
import android.widget.LinearLayout
import tech.granet.grove.ui.dp

/** Renders one provisional setup page. Only FirstRunState changes; Finish commits it. */
internal class FirstRunPages(
    private val context: Context,
    private val state: FirstRunState,
    private val apps: List<Pair<String, String>>,
    private val hasContacts: () -> Boolean,
    private val hasFiles: () -> Boolean,
    private val requestContacts: () -> Unit,
    private val requestFiles: () -> Unit,
    private val rerender: () -> Unit,
    private val feedbackHost: () -> android.widget.FrameLayout?,
) {
    private val ui = FirstRunComponents(context)

    private var practice: SwipePracticeMotion? = null
    fun pausePractice() { practice?.pause() }
    fun resumePractice() { practice?.resume() }
    fun destroyPractice() { practice?.destroy(); practice = null }

    fun render(page: Int, content: LinearLayout) = with(ui) {
        when (page) {
            0 -> {
                heading(content, "Welcome to Grove", "Make Home yours.")
                card(content, true) { box ->
                    box.addView(icon(R.drawable.ic_setup_home, onAccent, 64))
                    box.addView(text("Change any choice later in Launcher settings.", 18f, color = onAccent),
                        LinearLayout.LayoutParams(-1, -2).apply { topMargin = context.dp(20) })
                }
            }
            1 -> {
                heading(content, "Make it a gesture.",
                    "Choose your swipes.")
                choice(content, R.drawable.ic_setup_search, "Swipe down", "Open Search",
                    state.gestures.swipeDownSearch) { state.gestures = state.gestures.copy(swipeDownSearch = it) }
                choice(content, R.drawable.ic_setup_apps, "Swipe up", "Open all apps",
                    state.gestures.swipeUpAppDrawer) { state.gestures = state.gestures.copy(swipeUpAppDrawer = it) }
            }
            2 -> {
                heading(content, "Try your swipes.",
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
                    val target = android.widget.FrameLayout(context).apply {
                        background = GradientDrawable().apply { setColor(surface); cornerRadius = context.dp(20).toFloat() }
                        isClickable = true
                        contentDescription = context.getString(R.string.swipe_practice_description)
                    }
                    val motion = feedbackHost()?.let { SwipePracticeMotion(context, it, state) }
                    practice = motion
                    motion?.guide?.let { guide ->
                        guide.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        target.addView(guide, android.widget.FrameLayout.LayoutParams(-1, -1))
                    }
                    var startX = 0f; var startY = 0f; var time = 0L; var tracking = false
                    target.setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                view.parent.requestDisallowInterceptTouchEvent(true)
                                startX = event.x; startY = event.y; time = event.eventTime; tracking = true
                                motion?.touched()
                            }
                            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                                tracking = false; motion?.released()
                                view.parent.requestDisallowInterceptTouchEvent(false)
                            }
                            MotionEvent.ACTION_UP -> {
                                val gesture = if (tracking) Gestures.resolve(event.x - startX, event.y - startY,
                                    event.eventTime - time, context.dp(55).toFloat(), state.gestures) else HomeGesture.NONE
                                val newPractice = when (gesture) {
                                    HomeGesture.SEARCH -> (!state.practicedDown).also { state.practicedDown = true }
                                    HomeGesture.APP_DRAWER -> (!state.practicedUp).also { state.practicedUp = true }
                                    HomeGesture.NONE -> false
                                }
                                update()
                                if (gesture != HomeGesture.NONE) motion?.success(newPractice)
                                tracking = false; motion?.released()
                                view.parent.requestDisallowInterceptTouchEvent(false)
                                view.performClick()
                            }
                        }
                        true
                    }
                    box.addView(target, LinearLayout.LayoutParams(-1, context.dp(220)).apply { topMargin = context.dp(18) })
                }
            }
            3 -> {
                heading(content, "Settings are a hold away.",
                    "Hold empty Home space to open settings.")
                card(content, true) { box ->
                    box.minimumHeight = context.dp(220)
                    box.addView(icon(R.drawable.ic_setup_settings, onAccent, 48))
                    val feedback = text(if (state.practicedHold) "You got it!" else "Try holding this card",
                        20f, true, onAccent)
                    box.addView(feedback, LinearLayout.LayoutParams(-1, -2).apply {
                        topMargin = context.dp(24)
                    })
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
                heading(content, "Keep what matters.",
                    "Choose what appears on Home.")
                choice(content, R.drawable.ic_setup_search, "Search button", "Find apps and more",
                    state.home.showSearchButton) { state.home = state.home.copy(showSearchButton = it) }
                choice(content, R.drawable.ic_setup_apps, "All apps button", "Open the app drawer",
                    state.home.showAppsButton) { state.home = state.home.copy(showAppsButton = it) }
                choice(content, R.drawable.ic_setup_home, "Clock and date", "Time at a glance",
                    state.home.showClock) { state.home = state.home.copy(showClock = it) }
                choice(content, R.drawable.ic_setup_star, "Pinned apps", "Your chosen shortcuts",
                    state.home.showPinnedApps) { state.home = state.home.copy(showPinnedApps = it) }
            }
            5 -> {
                heading(content, "Pin your favorites.",
                    "Choose up to 12 apps. Hold and drag them into place later.")
                val counter = text("", 15f, true, primary)
                fun updateCounter() { counter.text = "${state.pins.size} of 12 selected" }
                updateCounter()
                content.addView(counter, LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = context.dp(8); bottomMargin = context.dp(12)
                })
                val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val input = TextInputLayout(context).apply {
                    hint = "Find an app"
                    boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                }
                val search = TextInputEditText(input.context).apply {
                    setSingleLine()
                    contentDescription = "Find an app to pin"
                    setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
                }
                input.addView(search, LinearLayout.LayoutParams(-1, -2))
                content.addView(input)
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
                        row.addView(MaterialCheckBox(context).apply {
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
            6 -> permissionPage(content, contacts = true)
            7 -> permissionPage(content, contacts = false)
        }
    }

    private fun permissionPage(content: LinearLayout, contacts: Boolean) = with(ui) {
        val enabled = if (contacts) state.search.contacts else state.search.files
        val granted = if (contacts) hasContacts() else hasFiles()
        val title = if (contacts) "Contact search" else "File search"
        heading(content, title, if (contacts)
            "We need contact access for contact search to work."
            else "We need file access for file search to work.")
        choice(content, if (contacts) R.drawable.ic_setup_person else R.drawable.ic_setup_folder,
            title, "", enabled) { selected ->
            state.search = if (contacts) state.search.copy(contacts = selected)
                else state.search.copy(files = selected)
            rerender()
            if (selected && !granted) {
                if (contacts) requestContacts() else requestFiles()
            }
        }
        if (enabled) {
            content.addView(text(if (granted) "Access granted" else "Access needed", 14f, color = muted))
            if (!granted) action(content, "Allow access") {
                if (contacts) requestContacts() else requestFiles()
            }
        }
    }
}
