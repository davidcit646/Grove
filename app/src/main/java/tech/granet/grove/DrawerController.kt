package tech.granet.grove

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Process
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.Gravity
import android.view.DragEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.wallpaperLabel
import tech.granet.grove.ui.message

/** Drawer presentation, selection and folder interactions. Persistent mutations must succeed before selection is cleared. */
internal class DrawerController(private val activity: MainActivity) {
    internal var drawerAdapter: DrawerTiles.Adapter? = null
    internal var drawerEmpty: TextView? = null
    internal var drawerGrid: GridView? = null
    internal val drawerState = DrawerState()
    private var gridPage = 0
    private var lastGridQuery = ""
    private var gridNavigation: LinearLayout? = null
    private fun tileHeight(): Int? = activity.configController.config.drawerGrid?.let {
        maxOf(drawerTiles.minimumCellHeight(), ((drawerGrid?.height ?: 0) - activity.dp(4) * (it.rows - 1)) / it.rows)
    }
    internal val drawerDragController by lazy { with(activity) { DrawerDragController(actionController::appMenu) } }
    internal val drawerTiles by lazy { with(activity) { DrawerTiles(this, this@DrawerController::launchDrawerApp, actionController::appMenu, this@DrawerController::tileHeight) } }
    internal val folderActions by lazy { with(activity) {
        FolderActions(this, { configController.config }, { catalogController.apps }, drawerTiles, configController::commitConfig,
            drawerState::clearKeys, this@DrawerController::refreshDrawer, actionController::appMenu, actionController::showActionMenu)
    } }

    fun clearAppSelection() {
        with(activity) {
            drawerState.clear()
        }
    }

    fun showDrawer(keyboard: Boolean, animate: Boolean = false) {
        with(activity) {
            if (isDestroyed || startupController.coreRecoveryVisible) return
            homeController.rememberHomeScroll()
            clearAppSelection()
            gridPage = 0; lastGridQuery = ""
            drawer = true; searchMode = false; homeController.base(readableBackdrop = true)
            root.requestFocus()
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(wallpaperLabel("All apps", 30f), LinearLayout.LayoutParams(0, -2, 1f))
            header.addView(ImageView(this).apply {
                setImageResource(R.drawable.ic_settings)
                imageTintList = ColorStateList.valueOf(Color.WHITE)
                contentDescription = "App drawer options"
                setPadding(dp(12), dp(12), dp(12), dp(12))
                isClickable = true; isFocusable = true
                setOnClickListener { drawerOptions() }
            }, LinearLayout.LayoutParams(dp(52), dp(52)))
            root.addView(header)
            val field = EditText(this).apply {
                hint = "Search installed apps"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(0xffc1ccc5.toInt())
                filters = arrayOf(InputFilter.LengthFilter(256))
                contentDescription = "Search installed apps"
            }
            searchController.searchField = field; root.addView(field)
            val content = FrameLayout(this)
            val empty = wallpaperLabel(if (catalogController.state is CatalogState.Loading) "Preparing apps and icons…" else "No matching apps").apply { gravity = Gravity.CENTER }
            drawerEmpty = empty
            val grid = GridView(this).apply {
                numColumns = GridPolicy.columns(configController.config.drawerGrid, resources.configuration.screenWidthDp)
                stretchMode = GridView.STRETCH_COLUMN_WIDTH
                verticalSpacing = dp(4)
                clipToPadding = false
            }
            drawerGrid = grid
            drawerAdapter = drawerTiles.Adapter { tile, item ->
                when (item) {
                    is DrawerTiles.Item.Application -> bindDrawerApp(tile, item.app)
                    is DrawerTiles.Item.Folder -> bindDrawerFolder(tile, item.folder)
                }
            }
            grid.adapter = drawerAdapter
            val width = maxOf(resources.displayMetrics.widthPixels - dp(40),
                if (configController.config.drawerGrid != null) grid.numColumns * dp(72) else 0)
            content.addView(HorizontalScrollView(this).apply {
                isFillViewport = true
                addView(grid, android.view.ViewGroup.LayoutParams(width, -1))
            }, FrameLayout.LayoutParams(-1, -1))
            content.addView(empty, FrameLayout.LayoutParams(-1, -1))
            grid.emptyView = empty
            root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
            gridNavigation = LinearLayout(this).also { root.addView(it) }
            field.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderApps(s.toString()) }
                override fun afterTextChanged(s: Editable?) {}
            })
            renderApps("")
            root.addView(homeController.button("Home") { homeController.animateDrawerClosed() })
            if (animate) homeController.enterContent(maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat() * if (keyboard) -1f else 1f)
            if (keyboard) { field.requestFocus(); field.post { if (drawer && searchController.searchField === field && field.hasFocus()) getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field, 0) } }
        }
    }

    fun renderApps(query: String) {
        with(activity) {
            drawerEmpty?.text = if (catalogController.state is CatalogState.Loading) "Preparing apps and icons…" else "No matching apps"
            val prepared = Search.prepare(query)
            val filtered = if (prepared.text.isEmpty()) {
                val assigned = configController.config.folders.flatMap { it.apps }.toSet()
                configController.config.folders.map { DrawerTiles.Item.Folder(it) } + catalogController.apps.take(catalogController.drawerVisibleCount)
                    .filterNot { it.key in assigned }.map { DrawerTiles.Item.Application(it) }
            } else SearchResults.matching(catalogController.apps, prepared) { it.searchName }.map { DrawerTiles.Item.Application(it) }
            val grid = configController.config.drawerGrid
            if (lastGridQuery != query) { lastGridQuery = query; gridPage = 0 }
            gridPage = GridPolicy.page(gridPage, filtered.size, grid)
            drawerGrid?.let { view ->
                view.numColumns = GridPolicy.columns(grid, resources.configuration.screenWidthDp)
                view.layoutParams?.let { params ->
                    params.width = maxOf(resources.displayMetrics.widthPixels - dp(40), if (grid != null) view.numColumns * dp(72) else 0)
                    view.layoutParams = params
                }
            }
            drawerAdapter?.submit(GridPolicy.items(filtered, gridPage, grid))
            gridNavigation?.apply {
                removeAllViews(); visibility = if (grid == null) View.GONE else View.VISIBLE
                if (grid != null) {
                    val count = GridPolicy.pageCount(filtered.size, grid)
                    addView(homeController.button("Previous") { gridPage--; renderApps(query) }.apply { isEnabled = gridPage > 0 }, LinearLayout.LayoutParams(0, -2, 1f))
                    addView(wallpaperLabel("${gridPage + 1} / $count").apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, -1, 1f))
                    addView(homeController.button("Next") { gridPage++; renderApps(query) }.apply { isEnabled = gridPage < count - 1 }, LinearLayout.LayoutParams(0, -2, 1f))
                }
            }
        }
    }

    fun launchDrawerApp(app: App) {
        with(activity) {
            runCatching { launcher.startMainActivity(app.component, Process.myUserHandle(), null, null) }
                .onFailure { message("This app is unavailable"); catalogController.loadApps() }
        }
    }

    fun createTile(height: Int? = null): DrawerTiles.Tile = with(activity) { drawerTiles.create(height)
    }

    fun bindTile(tile: DrawerTiles.Tile, app: App): Unit = with(activity) { drawerTiles.bind(tile, app)
    }

    fun refreshDrawer() {
        with(activity) { if (drawer && !searchMode) renderApps(searchController.searchField?.text?.toString().orEmpty())
        }
    }

    fun bindDrawerApp(tile: DrawerTiles.Tile, app: App) {
        with(activity) {
            bindTile(tile, app)
            tile.icon.alpha = if (drawerState.selecting) 0.35f else 1f
            tile.badge.visibility = if (drawerState.selecting) View.VISIBLE else View.GONE
            tile.badge.setImageResource(if (drawerState.isSelected(app.key)) R.drawable.ic_remove else R.drawable.ic_add)
            tile.layout.setOnClickListener {
                if (drawerState.selecting) {
                    drawerState.toggle(app.key)
                    drawerAdapter?.notifyDataSetChanged()
                } else runCatching { launcher.startMainActivity(app.component, Process.myUserHandle(), null, null) }
                    .onFailure { message("This app is unavailable"); catalogController.loadApps() }
            }
            if (drawerState.selecting) tile.layout.setOnLongClickListener {
                drawerState.select(app.key); drawerAdapter?.notifyDataSetChanged(); true
            } else drawerDragController.attach(tile.layout, app)
            tile.layout.setOnDragListener { view, event ->
                if (event.localState !is DrawerDragController.Drag) false else {
                    when (event.action) {
                        DragEvent.ACTION_DRAG_ENTERED -> view.alpha = 0.55f
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> view.alpha = 1f
                        DragEvent.ACTION_DROP -> {
                            view.alpha = 1f
                            val source = (event.localState as DrawerDragController.Drag).key
                            if (source != app.key) folderActions.promptCreate(listOf(source, app.key))
                        }
                    }
                    true
                }
            }
        }
    }

    fun bindDrawerFolder(tile: DrawerTiles.Tile, folder: AppFolder) {
        with(activity) {
            tile.layout.setOnTouchListener(null)
            tile.layout.contentDescription = "Folder ${folder.name}"
            tile.name.text = folder.name
            tile.icon.setImageResource(R.drawable.ic_folder)
            tile.icon.imageTintList = ColorStateList.valueOf(Color.WHITE)
            tile.icon.alpha = 1f
            tile.badge.visibility = View.GONE
            tile.layout.setOnClickListener { folderActions.open(folder.name) }
            tile.layout.setOnLongClickListener { folderActions.options(folder.name); true }
            tile.layout.setOnDragListener { view, event ->
                if (event.localState !is DrawerDragController.Drag) false else {
                    when (event.action) {
                        DragEvent.ACTION_DRAG_ENTERED -> view.alpha = 0.55f
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> view.alpha = 1f
                        DragEvent.ACTION_DROP -> {
                            view.alpha = 1f
                            folderActions.move(setOf((event.localState as DrawerDragController.Drag).key), folder.name)
                        }
                    }
                    true
                }
            }
        }
    }

    fun drawerOptions() {
        with(activity) {
            val keys = drawerState.keys
            val actions = buildList {
                add(Triple(if (drawerState.selecting) "Done selecting" else "Select apps", R.drawable.ic_grid) {
                    drawerState.toggleMode()
                    refreshDrawer()
                })
                add(Triple("Create folder", R.drawable.ic_folder) { folderActions.promptCreate() })
                if (keys.isNotEmpty()) {
                    add(Triple("Add to new folder", R.drawable.ic_folder) { folderActions.promptCreate(keys.toList()) })
                    if (configController.config.folders.isNotEmpty()) add(Triple("Move to folder", R.drawable.ic_folder) { folderActions.choose(keys) })
                    add(Triple("Pin to home screen", R.drawable.ic_home) {
                        DrawerState.pin(configController.config, keys)?.let {
                            if (configController.commitConfig(it)) {
                                drawerState.clearKeys(); refreshDrawer(); message("Apps pinned to Home")
                            }
                        }
                        Unit
                    })
                    add(Triple("Uninstall apps", R.drawable.ic_delete) { actionController.uninstallSelected(keys) })
                }
            }
            actionController.showActionMenu("App drawer", actions)
        }
    }
}
