package tech.granet.grove

import android.content.Intent
import android.content.res.ColorStateList
import android.provider.AlarmClock
import android.view.View
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import com.google.android.material.button.MaterialButton
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.wallpaperLabel
import tech.granet.grove.ui.message
import java.util.Calendar

/** Home rendering and scrolling. Android owns the unmodified wallpaper behind transparent Home. */
internal class HomeController(private val activity: MainActivity) {
    internal lateinit var body: LinearLayout
    internal var homeScrollY = 0
    private var gridPage = 0
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

    fun base(readableBackdrop: Boolean = false) {
        with(activity) {
            searchController.cancelPending()
            searchController.searchField = null; searchController.searchResults = null; drawerController.drawerAdapter = null; drawerController.drawerEmpty = null; drawerController.drawerGrid = null
            root.animate().cancel(); root.translationY = 0f; root.alpha = 1f
            root.removeAllViews()
            // The Android wallpaper window is authoritative, including external/live changes.
            // Home stays undimmed. Drawer/search use their own readable translucent surface.
            surface.background = if (readableBackdrop)
                android.graphics.drawable.ColorDrawable(0xb3000000.toInt()) else null
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
            addGrid(catalogController.apps.filter { it.key in configController.config.favorites }.sortedBy { configController.config.favorites.indexOf(it.key) }, target)
        }
    }

    fun addGrid(items: List<App>, target: LinearLayout) {
        with(activity) {
            val grid = configController.config.homeGrid
            val columns = GridPolicy.columns(grid, resources.configuration.screenWidthDp)
            gridPage = GridPolicy.page(gridPage, items.size, grid)
            val displayed = GridPolicy.items(items, gridPage, grid)
            val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val groups = displayed.chunked(columns)
            val rowCount = grid?.rows ?: groups.size
            val height = if (grid == null) null else maxOf(drawerController.drawerTiles.minimumCellHeight(), (surface.height - dp(240)) / grid.rows)
            repeat(rowCount) { index ->
                val row = LinearLayout(this)
                val group = groups.getOrNull(index).orEmpty()
                group.forEach { app ->
                    val tile = drawerController.createTile(height)
                    drawerController.bindTile(tile, app); pinDragController.attach(tile.layout, app)
                    row.addView(tile.layout, LinearLayout.LayoutParams(0, height ?: -2, 1f))
                }
                repeat(columns - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, height ?: 1, 1f)) }
                rows.addView(row)
            }
            if (grid == null) target.addView(rows) else {
                val width = maxOf(resources.displayMetrics.widthPixels - dp(40), columns * dp(72))
                target.addView(HorizontalScrollView(this).apply {
                    isFillViewport = true; addView(rows, android.view.ViewGroup.LayoutParams(width, -2))
                })
                val count = GridPolicy.pageCount(items.size, grid)
                val navigation = LinearLayout(this)
                fun move(delta: Int) {
                    gridPage = (gridPage + delta).coerceIn(0, count - 1)
                    showHome()
                    root.post { (body.parent as? ScrollView)?.scrollTo(0, 0); homeScrollY = 0 }
                }
                navigation.addView(button("Previous") { move(-1) }.apply { isEnabled = gridPage > 0 }, LinearLayout.LayoutParams(0, -2, 1f))
                navigation.addView(wallpaperLabel("${gridPage + 1} / $count").apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -1, 1f))
                navigation.addView(button("Next") { move(1) }.apply { isEnabled = gridPage < count - 1 }, LinearLayout.LayoutParams(0, -2, 1f))
                target.addView(navigation)
            }
        }
    }

    fun resetSwipeFeedback() {
        with(activity) {
            root.animate().cancel()
            root.translationY = 0f
            root.alpha = 1f
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
