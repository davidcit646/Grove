package tech.granet.grove

import android.content.*
import android.content.res.ColorStateList
import android.graphics.*
import android.provider.AlarmClock
import android.os.*
import android.view.*
import android.widget.*
import com.google.android.material.button.MaterialButton
import tech.granet.grove.ui.wallpaperLabel
import tech.granet.grove.ui.message
import java.util.*

/** Home rendering and scrolling. Android owns the unmodified wallpaper behind transparent Home. */
internal class HomeController(private val activity: MainActivity) {
    internal lateinit var body: LinearLayout
    internal var homeScrollY = 0
    fun bodyInitialized() = ::body.isInitialized

    fun button(text: String, action: () -> Unit): MaterialButton = with(activity) { MaterialButton(this).apply {
        this.text = text
        val themeColors = ThemeColors.buttonSurface(activity) to ThemeColors.onButtonSurface(activity)
        val colors = if (PresentationPolicy.wallpaperColors(configController.config.themeMode, configController.config.homeScreen.useWallpaperButtonColors))
            presentationController.buttonColors ?: themeColors
        else themeColors
        backgroundTintList = ColorStateList.valueOf(colors.first)
        setTextColor(colors.second)
        val iconId = when (text) {
            "Search", "Search apps" -> R.drawable.ic_search
            "All apps" -> R.drawable.ic_grid
            "Home" -> R.drawable.ic_home
            "Edit" -> R.drawable.ic_settings
            else -> 0
        }
        if (iconId != 0) {
            setIconResource(iconId)
            iconTint = ColorStateList.valueOf(colors.second)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        }
        setOnClickListener { action() }
    }
    }

    fun base() {
        with(activity) {
            searchController.cancelPending()
            searchController.searchField = null; searchController.searchResults = null; drawerController.drawerAdapter = null; drawerController.drawerEmpty = null; drawerController.drawerGrid = null
            root.animate().cancel(); root.translationY = 0f; root.alpha = 1f
            root.removeAllViews()
            // The Android wallpaper window is authoritative, including external/live changes.
            // Keep the image unmodified; contrast belongs to text and system-bar regions.
            surface.background = null
        }
    }

    fun openSearch(): Unit = with(activity) { searchController.showSearch(animate = true)
    }

    fun openAppDrawer(): Unit = with(activity) { drawerController.showDrawer(false, animate = true)
    }

    fun openClock() {
        with(activity) {
            runCatching { startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
                .onFailure { message("No Clock app is available") }
        }
    }

    fun openCalendar() {
        with(activity) {
            runCatching {
                startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
            }.onFailure { message("No Calendar app is available") }
        }
    }

    fun enterContent(fromY: Float) {
        with(activity) {
            root.translationY = fromY
            root.animate().translationY(0f).setInterpolator(android.view.animation.DecelerateInterpolator())
                .setDuration(220L).start()
        }
    }

    fun showHome(animate: Boolean = false) {
        with(activity) {
            if (isDestroyed || startupController.coreRecoveryVisible) return
            rememberHomeScroll()
            drawerController.clearAppSelection()
            getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
            drawer = false; searchMode = false; base()
            body = HomeScreen(this).render(root, configController.config.homeScreen, this@HomeController::button,
                this@HomeController::openSearch, this@HomeController::openAppDrawer, this@HomeController::openClock, this@HomeController::openCalendar,
                this@HomeController::renderPinnedApps, widgetFlow::render)
            (body.parent as ScrollView).apply {
                val restored = homeScrollY
                post { if (body.parent === this) scrollTo(0, restored) }
            }
            if (animate) enterContent(-maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat())
        }
    }

    fun rememberHomeScroll() {
        with(activity) {
            if (!bodyInitialized() || drawer || searchMode) return
            (body.parent as? ScrollView)?.let { homeScrollY = it.scrollY }
        }
    }

    fun renderPinnedApps(target: LinearLayout) {
        with(activity) {
            if (configController.config.homeScreen.showPinnedAppsHint) {
                val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                heading.addView(wallpaperLabel("PINNED APPS", 12f), LinearLayout.LayoutParams(0, -2, 1f))
                target.addView(heading)
                target.addView(wallpaperLabel("Hold and drag to move. Hold and release for options.", 12f))
            }
            addGrid(catalogController.apps.filter { it.key in configController.config.favorites }.sortedBy { configController.config.favorites.indexOf(it.key) }, target)
            if (configController.config.favorites.isEmpty()) target.addView(wallpaperLabel("Long-press an app in the drawer to pin it here."))
        }
    }

    fun addGrid(items: List<App>, target: LinearLayout) {
        with(activity) {
            val columns = if (resources.configuration.screenWidthDp >= 600) 6 else 4
            items.chunked(columns).forEach { group ->
                val row = LinearLayout(this)
                group.forEach { app ->
                    val tile = drawerController.createTile(); drawerController.bindTile(tile, app); pinDragController.attach(tile.layout, app)
                    row.addView(tile.layout, LinearLayout.LayoutParams(0, -2, 1f))
                }
                repeat(columns - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
                target.addView(row)
            }
        }
    }

    fun settleSwipeFeedback() {
        with(activity) {
            root.animate().cancel()
            root.animate().translationY(0f).setDuration(140L).start()
        }
    }

    fun animateHomeGesture(gesture: HomeGesture) {
        with(activity) {
            when (gesture) {
                HomeGesture.SEARCH -> searchController.showSearch(animate = true)
                HomeGesture.APP_DRAWER -> drawerController.showDrawer(false, animate = true)
                else -> settleSwipeFeedback()
            }
        }
    }

    fun animateDrawerClosed() {
        with(activity) {
            if (!drawer && !searchMode) return
            getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
            root.animate().cancel()
            root.animate().translationY(root.height.toFloat()).setInterpolator(android.view.animation.AccelerateInterpolator())
                .setDuration(180L).withEndAction {
                showHome()
                enterContent(-maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat())
            }.start()
        }
    }
}
