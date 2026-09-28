package tech.granet.grove

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import tech.granet.grove.ui.bodyText
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.titleText
import tech.granet.grove.ui.toggleRow

/** Full-screen, replayable setup. Answers are applied together on Finish. */
internal class FirstRunSetup(
    private val context: Context,
    private val host: FrameLayout,
    private val initial: Config,
    private val apps: List<Pair<String, String>>,
    private val hasContacts: () -> Boolean,
    private val hasFiles: () -> Boolean,
    private val requestContacts: () -> Unit,
    private val requestFiles: () -> Unit,
    private val finish: (Config) -> Unit,
    private val skip: () -> Unit,
) {
    private var page = 0
    private var gestures = initial.gestures
    private var home = initial.homeScreen
    private var searchSources = initial.search
    private val pins = initial.favorites.toMutableSet()
    private var overlay: View? = null
    private var practicedUp = false
    private var practicedDown = false

    fun show() = render()
    fun refreshPermissions() { if ((page == 5 || page == 6) && overlay != null) render() }
    fun back() { if (page > 0) { page--; render() } else { close(); skip() } }
    private fun close() { overlay?.let(host::removeView); overlay = null }

    private fun render() {
        close()
        val screen = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusableInTouchMode = true
            setBackgroundColor(ThemeColors.buttonSurface(context))
            setPadding(context.dp(24), context.dp(12), context.dp(24), context.dp(12))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                view.setPadding(context.dp(24), bars.top + context.dp(12), context.dp(24), bars.bottom + context.dp(12))
                insets
            }
        }
        overlay = screen
        host.addView(screen, FrameLayout.LayoutParams(-1, -1))
        screen.requestApplyInsets()
        screen.addView(context.bodyText("GROVE SETUP  ·  ${page + 1} OF 7"))
        val titles = listOf("Welcome to Grove", "Choose your swipes", "Try your swipes",
            "Choose your home controls", "Pin your apps", "Search your contacts", "Search your files")
        screen.addView(context.titleText(titles[page]))
        val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        screen.addView(ScrollView(context).apply { fillViewport = true; addView(content) },
            LinearLayout.LayoutParams(-1, 0, 1f))

        when (page) {
            0 -> content.addView(context.bodyText("Grove becomes your home screen when you choose it in Android. Let's set up the controls you want. You can change them later in Launcher settings."))
            1 -> {
                content.addView(context.bodyText("Which swipes should work on Home? The buttons can still open Search and all apps."))
                content.addView(context.toggleRow("Swipe down to search", gestures.swipeDownSearch) { gestures = gestures.copy(swipeDownSearch = it) })
                content.addView(context.toggleRow("Swipe up for all apps", gestures.swipeUpAppDrawer) { gestures = gestures.copy(swipeUpAppDrawer = it) })
            }
            2 -> {
                content.addView(context.bodyText("Try swiping on the practice area. You can tap Next without practicing."))
                val status = TextView(context).apply { textSize = 18f; setTextColor(ThemeColors.icon(context)) }
                fun update() { status.text = "Down to search: ${if (practicedDown) "Got it" else "Try it"}\nUp for apps: ${if (practicedUp) "Got it" else "Try it"}" }
                update()
                content.addView(status)
                content.addView(TextView(context).apply {
                    text = "Swipe here\n↑  Apps     ↓  Search"
                    textSize = 20f
                    gravity = Gravity.CENTER
                    setTextColor(ThemeColors.icon(context))
                    contentDescription = "Practice swiping up for apps or down for search. Next skips practice."
                    setBackgroundColor(ThemeColors.iconSurface(context))
                    var startY = 0f
                    setOnTouchListener { view, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                view.parent.requestDisallowInterceptTouchEvent(true)
                                startY = event.y
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                if (event.y - startY > context.dp(55)) practicedDown = true
                                if (event.y - startY < -context.dp(55)) practicedUp = true
                                update()
                                performClick()
                                true
                            }
                            else -> true
                        }
                    }
                }, LinearLayout.LayoutParams(-1, context.dp(220)).apply { topMargin = context.dp(20) })
            }
            3 -> {
                content.addView(context.bodyText("What should be visible on Home? Hold empty space to open settings even if you hide the buttons."))
                content.addView(context.toggleRow("Search button", home.showSearchButton) { home = home.copy(showSearchButton = it) })
                content.addView(context.toggleRow("All apps button", home.showAppsButton) { home = home.copy(showAppsButton = it) })
                content.addView(context.toggleRow("Clock and date", home.showClock) { home = home.copy(showClock = it) })
                content.addView(context.toggleRow("Pinned apps", home.showPinnedApps) { home = home.copy(showPinnedApps = it) })
            }
            4 -> {
                content.addView(context.bodyText("Choose up to 12 apps for Home. Hold and drag them later to change their order."))
                val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
                val search = EditText(context).apply { hint = "Find an app"; setSingleLine(); contentDescription = "Find an app to pin" }
                content.addView(search)
                fun updateList(query: String) {
                    list.removeAllViews()
                    apps.filter { it.second.contains(query, ignoreCase = true) }.take(80).forEach { (key, name) ->
                        list.addView(CheckBox(context).apply {
                            text = name
                            isChecked = key in pins
                            setOnCheckedChangeListener { button, checked ->
                                if (checked && pins.size >= 12) { button.isChecked = false; return@setOnCheckedChangeListener }
                                if (checked) pins.add(key) else pins.remove(key)
                            }
                        }, LinearLayout.LayoutParams(-1, -2))
                    }
                }
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = updateList(s.toString())
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                updateList("")
                content.addView(list)
            }
            5 -> {
                content.addView(context.bodyText("We recommend contact search for easier calling and texting. Type a person's name in Grove, then choose Call, Text, or their contact card. When you choose an action, Android passes the selected number to the app you pick."))
                content.addView(context.bodyText("If you don't need it, leaving it off means fewer results to search. Searches may be a little faster and use less battery, especially with a large contact list."))
                content.addView(context.bodyText("Grove reads contacts only on your device while this is enabled. GraNet does not collect or receive your contacts. Results stay in memory; no contact copy is uploaded."))
                content.addView(MaterialButton(context).apply {
                    text = if (searchSources.contacts && hasContacts()) "Contact search enabled" else "Enable contact search"
                    isEnabled = !searchSources.contacts || !hasContacts()
                    setOnClickListener {
                        searchSources = searchSources.copy(contacts = true)
                        render()
                        if (!hasContacts()) requestContacts()
                    }
                })
                if (searchSources.contacts) content.addView(MaterialButton(context).apply {
                    text = "Don't use contact search"
                    setOnClickListener { searchSources = searchSources.copy(contacts = false); render() }
                })
                content.addView(context.bodyText("You can change this later in Launcher settings. Turning it off stops contact reads and hides contact results; Android retains any permission you granted until you revoke it in system settings."))
            }
            6 -> {
                content.addView(context.bodyText("You probably don't need file search, but it's here if you keep music, documents, or other files on your device and want to find them by name in Grove."))
                content.addView(context.bodyText("Leaving it off avoids scanning shared storage. Searches may be faster and use less battery, especially if you have many files."))
                content.addView(context.bodyText("Android's All files access grants broad read and write access to shared storage, but not other apps' private data or system partitions. Grove only reads file names and paths for an in-memory index. It does not read contents, change files, or upload the index. GraNet does not collect or receive your file list. Opening a result shares that one file with the app you choose."))
                content.addView(MaterialButton(context).apply {
                    text = if (searchSources.files && hasFiles()) "File search enabled" else "Enable file search"
                    isEnabled = !searchSources.files || !hasFiles()
                    setOnClickListener {
                        searchSources = searchSources.copy(files = true)
                        render()
                        if (!hasFiles()) requestFiles()
                    }
                })
                if (searchSources.files) content.addView(MaterialButton(context).apply {
                    text = "Don't use file search"
                    setOnClickListener { searchSources = searchSources.copy(files = false); render() }
                })
                content.addView(context.bodyText("You can change this later in Launcher settings. Turning it off clears Grove's in-memory index and hides file results. Android retains any permission you granted until you revoke it in system settings."))
            }
        }
        val navigation = LinearLayout(context).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        navigation.addView(MaterialButton(context).apply {
            text = if (page == 0) "Skip setup" else "Back"
            setOnClickListener { back() }
        })
        navigation.addView(MaterialButton(context).apply {
            text = when (page) {
                5 -> if (searchSources.contacts && hasContacts()) "Continue" else "No thanks, next"
                6 -> if (searchSources.files && hasFiles()) "Finish" else "No thanks, finish"
                else -> "Next"
            }
            setOnClickListener {
                if (page == 5 && !hasContacts()) searchSources = searchSources.copy(contacts = false)
                if (page == 6) {
                    if (!hasFiles()) searchSources = searchSources.copy(files = false)
                    close()
                    finish(initial.copy(gestures = gestures, homeScreen = home, search = searchSources,
                        favorites = apps.map { it.first }.filter { it in pins }))
                } else { page++; render() }
            }
        })
        screen.addView(navigation)
    }
}
