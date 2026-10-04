package tech.granet.grove

import android.Manifest
import android.app.role.RoleManager
import android.app.WallpaperManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.content.*
import android.content.res.ColorStateList
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.util.Log
import android.os.*
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.MenuRow
import tech.granet.grove.ui.confirmDialog
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.infoDialog
import tech.granet.grove.ui.wallpaperLabel
import tech.granet.grove.ui.listDialog
import tech.granet.grove.ui.menuDialog
import tech.granet.grove.ui.message
import java.util.*
import java.io.File
import java.util.concurrent.Executors

/** Root HOME activity. Owns navigation; Android owns external apps and widget providers. */
class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("grove", MODE_PRIVATE) }
    private val configStore by lazy { ConfigStore(prefs) }
    private val launcher by lazy { getSystemService(LauncherApps::class.java) }
    private val appCatalog by lazy { AppCatalog(launcher, packageManager, packageName, worker) }
    private var launcherCallbackRegistered = false
    private var coreRecoveryVisible = false
    // Widget RemoteViews must inflate with a plain framework context. An
    // AppCompatActivity context can substitute AppCompat views that reject
    // RemoteViews actions and end up in Android's "Couldn't add widget" view.
    private val manager by lazy { AppWidgetManager.getInstance(applicationContext) }
    private val host by lazy { AppWidgetHost(applicationContext, 1024) }
    private val worker = Executors.newSingleThreadExecutor()
    private val searchWorker = Executors.newSingleThreadExecutor()
    private val contactWorker = Executors.newSingleThreadExecutor()
    private val searchHandler = Handler(Looper.getMainLooper())
    @Volatile private var searchGeneration = 0
    private var pendingSearch: Runnable? = null
    private val sources by lazy {
        SearchSources(this, worker, contactWorker, { config.search }, ::hasContactAccess) {
            if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
        }
    }
    private val contacts get() = sources.contacts
    private val contactSearch get() = sources.contactSearch
    private val files get() = sources.files
    private val fileSearch get() = sources.fileSearch
    private val indexingContacts get() = sources.indexingContacts
    private val contactLoadFailed get() = sources.contactLoadFailed
    private val lastContactRefresh get() = sources.lastContactRefresh
    private val indexingFiles get() = sources.indexingFiles
    private val fileLoadFailed get() = sources.fileLoadFailed
    private val fileScanSkipped get() = sources.fileScanSkipped
    private val requestContacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshContacts() else if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
        firstRunSetup?.refreshPermissions()
    }
    private val wallpaperController by lazy { WallpaperController(this, worker, this::message) }
    // Every installed app icon is decoded during app discovery and retained for the
    // lifetime of the launcher process. Drawer rendering never decodes icons.
    private val iconCache get() = AppIconStore
    private var drawerAdapter: DrawerTiles.Adapter? = null
    private var drawerEmpty: TextView? = null
    private var drawerGrid: GridView? = null
    private var drawerVisibleCount = 0
    private val drawerState = DrawerState()
    private val drawerDragController by lazy { DrawerDragController(::appMenu) }
    private val uninstallBatch = UninstallBatch()
    private val uninstallNext = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        loadApps()
        if (result.resultCode == RESULT_OK) launchNextUninstall(uninstallBatch.accepted())
        else {
            val remaining = uninstallBatch.cancel()
            if (remaining > 0) message("Remaining uninstalls canceled")
        }
    }
    private var config = Config()
    private var firstRunSetup: FirstRunSetup? = null
    private var apps = emptyList<App>()
    private var appSearch = SearchResults.prepare(apps) { it.searchName }
    private val widgets by lazy { WidgetRegistry(host, manager, prefs) }
    private val widgetFlow: WidgetFlow by lazy {
        WidgetFlow(this, manager, host, prefs, widgets,
            { bindWidget.launch(it) }, { configureResult.launch(it) }, { showHome() }, this::message)
    }
    private var drawer = false
    private var searchMode = false
    private var searchResults: LinearLayout? = null
    private lateinit var surface: FrameLayout
    private lateinit var root: LinearLayout
    private val pinDragController by lazy {
        PinDragController(this, { body.parent as? ScrollView }, { config }, ::commitConfig,
            ::appMenu, touchRouter::cancel)
    }
    private lateinit var body: LinearLayout
    private var homeScrollY = 0
    private var searchField: EditText? = null
    @Volatile private var loadGeneration = 0
    private var artworkStyle = -1
    private var artwork: Bitmap? = null
    private var backdrop: Bitmap? = null
    private var backdropWidth = 0
    private var backdropHeight = 0
    private var wallpaperButtonColors: Pair<Int, Int>? = null
    private var pendingWallpaper: Triple<Int, Int, Int>? = null
    private val touchRouter by lazy {
        HomeTouchRouter(this, { root }, { drawerGrid }, { drawer || searchMode },
            { config.gestures }, ::settings, ::animateHomeGesture, ::animateDrawerClosed)
    }
    private var loadingApps = true

    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            contentResolver.openOutputStream(uri)?.use { ConfigDocuments.write(config, it) }
                ?: error("Cannot open file")
        }.onFailure { message("Could not export configuration") }
    }
    private val importConfig = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            contentResolver.openInputStream(uri)?.use(ConfigDocuments::read)
                ?: error("Cannot open file")
        }.onSuccess {
            runCatching { activateConfig(it) }
                .onSuccess { showHome(); message("Configuration imported") }
                .onFailure { error -> message(error.message ?: "Could not save configuration") }
        }.onFailure { message(it.message ?: "Invalid configuration") }
    }
    private val bindWidget: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) widgetFlow.configure() else widgetFlow.cancel()
    }
    private val configureResult: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) widgetFlow.finish() else {
            Log.w("Grove", "Widget configuration returned ${result.resultCode} for id ${widgets.pending}")
            widgetFlow.cancel()
        }
    }
    private val chooseHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    private val changes = object : LauncherApps.Callback() {
        override fun onPackageAdded(p: String, u: UserHandle) { loadApps(p); if (config.search.contacts && hasContactAccess()) refreshContacts() }
        override fun onPackageRemoved(p: String, u: UserHandle) { loadApps(); if (config.search.contacts && hasContactAccess()) refreshContacts() }
        override fun onPackageChanged(p: String, u: UserHandle) { loadApps(p); if (config.search.contacts && hasContactAccess()) refreshContacts() }
        override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, replacing: Boolean) = loadApps()
        override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, replacing: Boolean) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDecorFitsSystemWindows(false)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // The drawer's search field must not take focus (and open the IME)
            // merely because it is the first focusable child.
            isFocusableInTouchMode = true
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            val edges = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(dp(20), edges.top + dp(12), dp(20), edges.bottom + dp(12)); insets
        }
        surface = FrameLayout(this).apply { addView(root, FrameLayout.LayoutParams(-1, -1)) }
        setContentView(surface)
        CrashReporter.promptIfPending(this)
        root.setOnDragListener { _, event ->
            if (event.localState !is PinDragController.Drag) false else {
                when (event.action) {
                    DragEvent.ACTION_DRAG_LOCATION -> pinDragController.scrollNearEdge(root, event)
                    DragEvent.ACTION_DRAG_ENDED -> {
                        pinDragController.finishDrag()
                        root.post { if (!drawer && !isDestroyed) showHome() }
                    }
                }
                true
            }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (firstRunSetup != null) firstRunSetup?.back()
                else if (drawer && drawerState.selecting) { clearAppSelection(); refreshDrawer() }
                else if (drawer || searchMode) animateDrawerClosed()
                // Back at Home has no navigation destination. Recreating the
                // view here would unexpectedly jump a scrolled layout to top.
            }
        })
        startupState = savedInstanceState
        runCatching {
            if (!prefs.getBoolean("setup_complete", false) && !setupPending()) {
                // Existing users keep their layout and can replay setup from the menu.
                prefs.edit().putBoolean(
                    if (prefs.contains("initialized")) "setup_complete" else "setup_pending", true
                ).apply()
            }
        }.onFailure { Log.w("Grove", "Setup state unavailable", it) }
        beginHome()
    }

    private var startupState: Bundle? = null

    private fun setupPending(): Boolean = runCatching { prefs.getBoolean("setup_pending", false) }
        .onFailure { Log.w("Grove", "Setup flag unavailable", it) }.getOrDefault(false)

    private fun beginHome() {
        val loaded = runCatching { configStore.load() }.getOrElse { error ->
            Log.e("Grove", "Configuration unavailable", error)
            showCoreRecovery("Grove could not load its settings. Retry, or change your Home app in Android Settings. Your saved settings have not been erased.")
            return
        }
        config = loaded
        // Widget metadata is optional. Keep the app list and Home available if it is damaged.
        runCatching { widgets.restore(startupState) }
            .onFailure { Log.w("Grove", "Widget state unavailable", it) }
        if (!ensureLauncherCallback()) return
        coreRecoveryVisible = false
        root.setBackgroundColor(Color.TRANSPARENT)
        homeScrollY = startupState?.getInt("homeScrollY") ?: homeScrollY
        startupState = null
        showHome()
        applyStartupPlan(StartupCoordinator.coldStart(startupSnapshot()))
        if (configStore.brokenCustomConfig != null) root.post { showConfigRecoveryDialog() }
        if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) root.post { settings() }
    }

    private fun ensureLauncherCallback(): Boolean {
        if (launcherCallbackRegistered) return true
        return try {
            launcher.registerCallback(changes, Handler(Looper.getMainLooper()))
            launcherCallbackRegistered = true
            true
        } catch (error: Exception) {
            Log.e("Grove", "Launcher service unavailable", error)
            showCoreRecovery("Grove could not connect to Android's app launcher service.")
            false
        }
    }

    private fun showCoreRecovery(detail: String) {
        coreRecoveryVisible = true
        drawer = false
        searchMode = false
        root.animate().cancel()
        root.removeAllViews()
        root.setBackgroundColor(0xff182421.toInt())
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        panel.addView(TextView(this).apply {
            text = "Grove cannot load Home"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        panel.addView(TextView(this).apply {
            text = detail
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        panel.addView(Button(this).apply {
            text = "Retry"
            setOnClickListener { beginHome() }
        })
        panel.addView(Button(this).apply {
            text = "Android Home settings"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (_: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {
                        message("Android Settings is unavailable")
                    }
                }
            }
        })
        root.addView(panel, LinearLayout.LayoutParams(-1, -1))
    }

    override fun onStart() {
        super.onStart()
        runCatching { host.startListening() }
            .onFailure { Log.w("Grove", "Widget listening unavailable", it); message("Widgets unavailable") }
    }
    private fun clearAppSelection() {
        drawerState.clear()
    }
    override fun onResume() {
        super.onResume()
        firstRunSetup?.refreshPermissions()
        if (coreRecoveryVisible) return
        applyStartupPlan(StartupCoordinator.resume(startupSnapshot(), SystemClock.elapsedRealtime()))
    }

    private fun startupSnapshot() = StartupCoordinator.Snapshot(
        contactSearchEnabled = config.search.contacts,
        contactsGranted = hasContactAccess(),
        lastContactRefreshMs = lastContactRefresh,
        indexingContacts = indexingContacts,
        contactLoadFailed = contactLoadFailed,
        fileSearchEnabled = config.search.files,
        filesGranted = Environment.isExternalStorageManager(),
        hasFiles = files.isNotEmpty(),
        indexingFiles = indexingFiles,
    )

    private fun applyStartupPlan(plan: StartupCoordinator.Plan) {
        if (plan.clearFiles) sources.clearFiles()
        if (plan.loadApps) loadApps()
        if (plan.indexFiles) indexFiles()
        if (plan.refreshContacts) refreshContacts()
    }

    override fun onStop() {
        pinDragController.releaseHold(); touchRouter.cancel()
        runCatching { host.stopListening() }.onFailure { Log.w("Grove", "Widget stop failed", it) }
        clearAppSelection()
        if (drawer) refreshDrawer()
        super.onStop()
    }
    override fun onDestroy() {
        if (launcherCallbackRegistered) {
            try { launcher.unregisterCallback(changes) }
            catch (error: Exception) { Log.w("Grove", "Could not unregister launcher callback", error) }
            launcherCallbackRegistered = false
        }
        touchRouter.cancel()
        pendingSearch?.let(searchHandler::removeCallbacks)
        searchGeneration++
        searchWorker.shutdownNow()
        sources.shutdown()
        worker.shutdownNow()
        if (::surface.isInitialized) surface.background = null
        artwork?.recycle()
        artwork = null
        backdrop = null
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        rememberHomeScroll()
        outState.putInt("pending", widgets.pending)
        outState.putInt("homeScrollY", homeScrollY)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (drawer || searchMode) showHome()
        if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) root.post { settings() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (firstRunSetup != null) return super.dispatchTouchEvent(event)
        if (pinDragController.busy) {
            touchRouter.cancel()
            return super.dispatchTouchEvent(event)
        }
        return touchRouter.dispatch(event) { super.dispatchTouchEvent(it) }
    }

    private fun settleSwipeFeedback() {
        root.animate().cancel()
        root.animate().translationY(0f).setDuration(140L).start()
    }

    private fun animateHomeGesture(gesture: HomeGesture) {
        when (gesture) {
            HomeGesture.SEARCH -> showSearch(animate = true)
            HomeGesture.APP_DRAWER -> showDrawer(false, animate = true)
            else -> settleSwipeFeedback()
        }
    }

    private fun animateDrawerClosed() {
        if (!drawer && !searchMode) return
        getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
        root.animate().cancel()
        root.animate().translationY(root.height.toFloat()).setInterpolator(android.view.animation.AccelerateInterpolator())
            .setDuration(180L).withEndAction {
            showHome()
            enterContent(-maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat())
        }.start()
    }
    private fun loadApps(changedPackage: String? = null) {
        if (isDestroyed || worker.isShutdown) return
        val generation = ++loadGeneration
        val iconSize = dp(48)
        AppIconStore.useSize(iconSize)
        val reusable = iconCache.snapshotExcluding(changedPackage)
        appCatalog.load(
            changedPackage, iconSize, reusable,
            current = { generation == loadGeneration && !isDestroyed },
            onCatalog = { loadedApps, fallbackIcon ->
                val preparedApps = SearchResults.prepare(loadedApps) { it.searchName }
                runOnUiThread {
                    if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                    apps = loadedApps
                    appSearch = preparedApps
                    drawerVisibleCount = if (loadedApps.all { reusable.containsKey(it.key) })
                        loadedApps.size else minOf(24, loadedApps.size)
                    iconCache.replace(loadedApps.associate { it.key to (reusable[it.key] ?: fallbackIcon) })
                    if (!prefs.contains("initialized")) {
                        val initial = if (!setupPending() && config.favorites.isEmpty())
                            config.copy(favorites = apps.take(8).map { it.key }) else config
                        if (commitConfig(initial)) prefs.edit().putBoolean("initialized", true).apply()
                    }
                    if (coreRecoveryVisible) {
                        coreRecoveryVisible = false
                        root.setBackgroundColor(Color.TRANSPARENT)
                        showHome()
                    } else if (drawer) renderApps(searchField?.text?.toString().orEmpty())
                    else if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
                    else if (!pinDragController.busy) showHome()
                    if (setupPending() && firstRunSetup == null &&
                        configStore.brokenCustomConfig == null) root.post { if (!isDestroyed) startFirstRunSetup() }
                }
            },
            onIcons = { batch -> publishIcons(generation, batch) },
            onComplete = {
                runOnUiThread {
                    if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                    loadingApps = false
                    drawerVisibleCount = apps.size
                    if (drawer && searchField?.text.isNullOrEmpty()) renderApps("")
                }
            },
            onFailure = { error ->
                Log.w("Grove", "Unable to load apps", error)
                runOnUiThread {
                    if (isDestroyed || generation != loadGeneration) return@runOnUiThread
                    loadingApps = false
                    apps = emptyList()
                    appSearch = SearchResults.prepare(apps) { it.searchName }
                    iconCache.clear()
                    showCoreRecovery("Android could not provide the installed app list. Retry, or change your Home app in Android Settings.")
                }
            },
        )
    }

    private fun publishIcons(generation: Int, batch: Map<String, Bitmap>) {
        runOnUiThread {
            if (isDestroyed || generation != loadGeneration) return@runOnUiThread
            iconCache.putAll(batch)
            drawerVisibleCount = minOf(apps.size, drawerVisibleCount + 16)
            fun update(view: View) {
                if (view is ImageView) (view.tag as? String)?.let { key ->
                    batch[key]?.let(view::setImageBitmap)
                }
                if (view is ViewGroup) for (index in 0 until view.childCount) update(view.getChildAt(index))
            }
            update(root)
            if (drawer && searchField?.text.isNullOrEmpty()) renderApps("")
            else drawerAdapter?.notifyDataSetChanged()
        }
    }
    private fun commitConfig(next: Config): Boolean {
        return runCatching { configStore.save(next) }.fold(
            onSuccess = { config = next; true },
            onFailure = {
                Log.e("Grove", "Could not save settings", it)
                message("Could not save Grove settings")
                false
            },
        )
    }
    private fun activateConfig(next: Config) {
        val previousSearch = config.search
        configStore.activate(next)
        config = next
        if (previousSearch != next.search) applySearchSettings(previousSearch)
    }
    private fun button(text: String, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        val themeColors = ThemeColors.buttonSurface(this@MainActivity) to ThemeColors.onButtonSurface(this@MainActivity)
        val colors = if (config.homeScreen.useWallpaperButtonColors && artworkStyle == config.wallpaper)
            wallpaperButtonColors ?: themeColors
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
    private fun base() {
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch = null
        searchGeneration++
        searchField = null; searchResults = null; drawerAdapter = null; drawerEmpty = null; drawerGrid = null
        root.animate().cancel(); root.translationY = 0f; root.alpha = 1f
        root.removeAllViews()
        val width = surface.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = surface.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val key = Triple(config.wallpaper, width, height)
        if (artworkStyle != key.first || backdropWidth != width || backdropHeight != height || backdrop == null) {
            if (pendingWallpaper != key) {
                pendingWallpaper = key
                wallpaperController.background(key.first, width, height) { prepared, colors ->
                    if (pendingWallpaper != key || config.wallpaper != key.first) {
                        prepared?.recycle()
                    } else {
                        pendingWallpaper = null
                        if (prepared == null) {
                            message("Home wallpaper unavailable")
                        } else {
                            val previous = artwork
                            artwork = prepared
                            backdrop = prepared
                            artworkStyle = key.first
                            backdropWidth = width
                            backdropHeight = height
                            wallpaperButtonColors = colors
                            showWallpaperBackground(prepared)
                            if (previous !== prepared) previous?.recycle()
                            if (!drawer && !searchMode) showHome()
                        }
                    }
                }
            }
            surface.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xff416e60.toInt(), 0xff142f30.toInt()),
            )
        } else showWallpaperBackground(backdrop!!)
    }

    private fun showWallpaperBackground(image: Bitmap) {
        surface.background = LayerDrawable(arrayOf(
            BitmapDrawable(resources, image).apply { gravity = Gravity.FILL },
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x66000000, 0xaa000000.toInt()))
        ))
    }
    private fun openSearch() = showSearch(animate = true)
    private fun openAppDrawer() = showDrawer(false, animate = true)

    private fun openClock() {
        runCatching { startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)) }
            .onFailure { message("No Clock app is available") }
    }

    private fun openCalendar() {
        runCatching {
            startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
        }.onFailure { message("No Calendar app is available") }
    }

    private fun enterContent(fromY: Float) {
        root.translationY = fromY
        root.animate().translationY(0f).setInterpolator(android.view.animation.DecelerateInterpolator())
            .setDuration(220L).start()
    }

    private fun showHome(animate: Boolean = false) {
        rememberHomeScroll()
        clearAppSelection()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(root.windowToken, 0)
        drawer = false; searchMode = false; base()
        body = HomeScreen(this).render(root, config.homeScreen, ::button,
            ::openSearch, ::openAppDrawer, ::openClock, ::openCalendar,
            ::renderPinnedApps, widgetFlow::render)
        (body.parent as ScrollView).apply {
            val restored = homeScrollY
            post { if (body.parent === this) scrollTo(0, restored) }
        }
        if (animate) enterContent(-maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat())
    }

    private fun rememberHomeScroll() {
        if (!::body.isInitialized || drawer || searchMode) return
        (body.parent as? ScrollView)?.let { homeScrollY = it.scrollY }
    }

    private fun renderPinnedApps(target: LinearLayout) {
        if (config.homeScreen.showPinnedAppsHint) {
            val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(wallpaperLabel("PINNED APPS", 12f), LinearLayout.LayoutParams(0, -2, 1f))
            target.addView(heading)
            target.addView(wallpaperLabel("Hold and drag to move. Hold and release for options.", 12f))
        }
        addGrid(apps.filter { it.key in config.favorites }.sortedBy { config.favorites.indexOf(it.key) }, target)
        if (config.favorites.isEmpty()) target.addView(wallpaperLabel("Long-press an app in the drawer to pin it here."))
    }

    private fun indexFiles() = sources.indexFiles()
    private fun refreshContacts() = sources.refreshContacts()
    private fun hasContactAccess() =
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun requestContactAccess() { requestContacts.launch(Manifest.permission.READ_CONTACTS) }

    private fun explainContactAccess() {
        if (hasContactAccess()) { refreshContacts(); return }
        MaterialAlertDialogBuilder(this)
            .setTitle("Contact search access")
            .setMessage("Grove reads contact names and phone numbers from Android's Contacts Provider to show search results and contact actions. Results stay in memory on this device; Grove does not upload or save a contact copy. If you choose Call or Text, Android passes that number to the app you select. You can skip this and turn Contact search off at any time. The Android permission remains granted until you revoke it in system settings.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Continue to Android") { _, _ -> requestContactAccess() }
            .show()
    }

    private fun requestFileAccess() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")))
        }.onFailure { message("Open Android settings to allow shared storage search") }
    }

    private fun explainFileAccess() {
        if (Environment.isExternalStorageManager()) { indexFiles(); return }
        MaterialAlertDialogBuilder(this)
            .setTitle("Shared-storage file search access")
            .setMessage("Android's All files access grants Grove broad read and write access to shared storage, including files beyond photos and videos. It does not grant access to other apps' private data or system partitions. Grove uses it to read file names and paths for on-device search; it does not read file contents, modify files, or upload the index. Opening a result shares that one file with the app you select. This is optional. Turning File search off clears Grove's in-memory index, but Android keeps the permission until you revoke it in system settings.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open Android settings") { _, _ -> requestFileAccess() }
            .show()
    }

    private fun applySearchSettings(previous: SearchSettings) {
        if (!config.search.contacts) sources.clearContacts()
        else if (!previous.contacts) {
            if (hasContactAccess()) refreshContacts() else explainContactAccess()
        }
        if (!config.search.files) sources.clearFiles()
        else if (!previous.files) {
            if (Environment.isExternalStorageManager()) indexFiles() else explainFileAccess()
        }
        if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
    }

    private fun showSearch(animate: Boolean = false) {
        rememberHomeScroll()
        clearAppSelection()
        drawer = false; searchMode = true; base()
        root.addView(wallpaperLabel("Search", 30f))
        val field = EditText(this).apply {
            hint = "Apps, contacts, files, web, and Play Store"
            filters = arrayOf(InputFilter.LengthFilter(256))
            setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(0xffc1ccc5.toInt())
            contentDescription = "Search apps, contacts, files, Google, and Play Store"
        }
        searchField = field
        root.addView(field)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        searchResults = list
        root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(button("Home") { animateDrawerClosed() })
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = renderSearch(s.toString())
            override fun afterTextChanged(s: Editable?) {}
        })
        renderSearch("")
        if (animate) enterContent(-maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat())
        field.requestFocus()
        field.post { if (searchMode && searchField === field) getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field, 0) }
    }

    private val searchScreen by lazy { SearchScreen(this) }

    private fun renderSearch(query: String) {
        val target = searchResults ?: return
        val generation = ++searchGeneration
        pendingSearch?.let(searchHandler::removeCallbacks)
        pendingSearch = null
        val prepared = Search.prepare(query)
        if (prepared.text.isEmpty()) {
            displaySearch(target, query, emptyList(), emptyList(), emptyList())
            return
        }
        val appSnapshot = appSearch
        val contactSnapshot = contactSearch
        val fileSnapshot = fileSearch
        target.removeAllViews()
        val task = Runnable {
            if (searchWorker.isShutdown) return@Runnable
            searchWorker.execute {
                if (generation != searchGeneration) return@execute
                val matchingApps = SearchResults.matching(appSnapshot, prepared, 12)
                if (generation != searchGeneration) return@execute
                val matchingContacts = SearchResults.matching(contactSnapshot, prepared, 12)
                if (generation != searchGeneration) return@execute
                val matchingFiles = SearchResults.matching(fileSnapshot, prepared, 12)
                runOnUiThread {
                    if (generation != searchGeneration || !searchMode || searchResults !== target) return@runOnUiThread
                    pendingSearch = null
                    displaySearch(target, query, matchingApps, matchingContacts, matchingFiles)
                }
            }
        }
        pendingSearch = task
        searchHandler.postDelayed(task, 80L)
    }

    private fun displaySearch(target: LinearLayout, query: String,
                              matchingApps: List<App>, matchingContacts: List<ContactIndex.Contact>,
                              matchingFiles: List<IndexedFile>) {
        searchScreen.render(
            target, query,
            matchingApps.map { app -> SearchScreen.AppRow(app.key, app.label, iconCache[app.key],
                open = {
                    runCatching { launcher.startMainActivity(app.component, android.os.Process.myUserHandle(), null, null) }
                        .onFailure { message("This app is unavailable"); loadApps() }
                }, menu = { appMenu(app) }) },
            matchingContacts.map { contact -> SearchScreen.ContactRow(contact.name) { contactMenu(contact) } },
            matchingFiles.map { file -> SearchScreen.FileRow(file,
                open = { openFile(file) }, menu = { searchItemMenu(file) }) },
            SearchSourceState.resolve(config.search.contacts, hasContactAccess(),
                indexingContacts, contactLoadFailed, contacts.size),
            ::explainContactAccess, ::refreshContacts,
            SearchSourceState.resolve(config.search.files, Environment.isExternalStorageManager(),
                indexingFiles, fileLoadFailed, files.size, fileScanSkipped),
            requestFileAccess = { explainFileAccess() }, retryFiles = { indexFiles() },
            searchGoogle = { openWeb("https://www.google.com/search?q=${Uri.encode(query.trim())}") },
            googleMenu = { webResultMenu(query.trim(), "Google") },
            searchStore = { openPlayStore(query.trim()) },
            storeMenu = { playStoreMenu(query.trim()) },
        )
    }

    private val fileActions by lazy { FileActions(this) { config.search.files } }
    private fun sharedFileUri(file: File): Uri = fileActions.shareUri(file)

    private val contactActions by lazy {
        ContactActions(this, contactWorker, { config }, ::hasContactAccess,
            { packageName -> apps.any { it.component.packageName == packageName } }, ::showActionMenu)
    }
    private fun contactMenu(contact: ContactIndex.Contact) = contactActions.show(contact)

    private fun openFile(file: IndexedFile) = fileActions.open(file)

    private val searchActions by lazy { SearchActions(this, ::showActionMenu) }
    private fun openPlayStore(query: String, install: Boolean = false) =
        searchActions.openPlayStore(query, install)

    private fun appMenu(app: App) {
        val pinned = app.key in config.favorites
        showActionMenu(app.label, listOf(
            Triple(if (pinned) "Unpin from home" else "Pin to home", R.drawable.ic_grid) {
                val next = config.copy(favorites = if (pinned) config.favorites - app.key else config.favorites + app.key)
                if (commitConfig(next) && !drawer) showHome()
            },
            Triple("App info", R.drawable.ic_info) {
                runCatching { launcher.startAppDetailsActivity(app.component, android.os.Process.myUserHandle(), null, null) }
                    .onFailure { message("App information unavailable") }
            },
            Triple("Uninstall", R.drawable.ic_delete) {
                runCatching { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}"))) }
                    .onFailure { message("This app cannot be uninstalled") }
            },
        ))
    }

    private fun searchItemMenu(file: IndexedFile) {
        showActionMenu(file.name, listOf(
            Triple("Open", R.drawable.ic_open) { openFile(file) },
            Triple("Open containing folder", R.drawable.ic_folder) {
                val parent = file.file.parentFile
                if (parent == null) message("Containing folder unavailable") else openFile(IndexedFile(parent.name, "resource/folder", parent, "Folder"))
            },
            Triple("Share", R.drawable.ic_share) {
                runCatching {
                    val uri = sharedFileUri(file.file)
                    startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = file.mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newUri(contentResolver, file.name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share ${file.name}"))
                }.onFailure { message("Could not share this file") }
            },
            Triple("File details", R.drawable.ic_info) {
                infoDialog(file.name,
                    "${file.category}\n${file.file.absolutePath}\n${file.file.length()} bytes", "Done")
            },
        ))
    }

    private fun webResultMenu(query: String, provider: String) = searchActions.webResultMenu(query, provider)
    private fun playStoreMenu(query: String) = searchActions.playStoreMenu(query)

    private fun showActionMenu(title: String, actions: List<Triple<String, Int, () -> Unit>>) {
        menuDialog(title, actions.map { (name, icon, action) -> MenuRow(name, icon, action) })
    }

    private fun openWeb(url: String) = searchActions.openWeb(url)

    private fun showDrawer(keyboard: Boolean, animate: Boolean = false) {
        rememberHomeScroll()
        clearAppSelection()
        drawer = true; searchMode = false; base()
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
        searchField = field; root.addView(field)
        val content = FrameLayout(this)
        val empty = wallpaperLabel(if (loadingApps) "Preparing apps and icons…" else "No matching apps").apply { gravity = Gravity.CENTER }
        drawerEmpty = empty
        val grid = GridView(this).apply {
            numColumns = if (resources.configuration.screenWidthDp >= 600) 6 else 4
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
        content.addView(grid, FrameLayout.LayoutParams(-1, -1))
        content.addView(empty, FrameLayout.LayoutParams(-1, -1))
        grid.emptyView = empty
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderApps(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        renderApps("")
        root.addView(button("Home") { animateDrawerClosed() })
        if (animate) enterContent(maxOf(surface.height, resources.displayMetrics.heightPixels).toFloat() * if (keyboard) -1f else 1f)
        if (keyboard) { field.requestFocus(); field.post { if (drawer && searchField === field && field.hasFocus()) getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field, 0) } }
    }
    private fun renderApps(query: String) {
        drawerEmpty?.text = if (loadingApps) "Preparing apps and icons…" else "No matching apps"
        val prepared = Search.prepare(query)
        val filtered = if (prepared.text.isEmpty()) {
            val assigned = config.folders.flatMap { it.apps }.toSet()
            config.folders.map { DrawerTiles.Item.Folder(it) } + apps.take(drawerVisibleCount)
                .filterNot { it.key in assigned }.map { DrawerTiles.Item.Application(it) }
        } else SearchResults.matching(apps, prepared) { it.searchName }.map { DrawerTiles.Item.Application(it) }
        drawerAdapter?.submit(filtered)
    }
    private val drawerTiles by lazy { DrawerTiles(this, ::launchDrawerApp, ::appMenu) }
    private val folderActions by lazy {
        FolderActions(this, { config }, { apps }, drawerTiles, ::commitConfig,
            drawerState::clearKeys, ::refreshDrawer, ::appMenu, ::showActionMenu)
    }
    private fun launchDrawerApp(app: App) {
        runCatching { launcher.startMainActivity(app.component, Process.myUserHandle(), null, null) }
            .onFailure { message("This app is unavailable"); loadApps() }
    }
    private fun createTile() = drawerTiles.create()
    private fun bindTile(tile: DrawerTiles.Tile, app: App) = drawerTiles.bind(tile, app)

    private fun refreshDrawer() { if (drawer && !searchMode) renderApps(searchField?.text?.toString().orEmpty()) }

    private fun bindDrawerApp(tile: DrawerTiles.Tile, app: App) {
        bindTile(tile, app)
        tile.icon.alpha = if (drawerState.selecting) 0.35f else 1f
        tile.badge.visibility = if (drawerState.selecting) View.VISIBLE else View.GONE
        tile.badge.setImageResource(if (drawerState.isSelected(app.key)) R.drawable.ic_remove else R.drawable.ic_add)
        tile.layout.setOnClickListener {
            if (drawerState.selecting) {
                drawerState.toggle(app.key)
                drawerAdapter?.notifyDataSetChanged()
            } else runCatching { launcher.startMainActivity(app.component, Process.myUserHandle(), null, null) }
                .onFailure { message("This app is unavailable"); loadApps() }
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

    private fun bindDrawerFolder(tile: DrawerTiles.Tile, folder: AppFolder) {
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

    private fun drawerOptions() {
        val keys = drawerState.keys
        val actions = buildList {
            add(Triple(if (drawerState.selecting) "Done selecting" else "Select apps", R.drawable.ic_grid) {
                drawerState.toggleMode()
                refreshDrawer()
            })
            add(Triple("Create folder", R.drawable.ic_folder) { folderActions.promptCreate() })
            if (keys.isNotEmpty()) {
                add(Triple("Add to new folder", R.drawable.ic_folder) { folderActions.promptCreate(keys.toList()) })
                if (config.folders.isNotEmpty()) add(Triple("Move to folder", R.drawable.ic_folder) { folderActions.choose(keys) })
                add(Triple("Pin to home screen", R.drawable.ic_home) {
                    DrawerState.pin(config, keys)?.let {
                        if (commitConfig(it)) {
                            drawerState.clearKeys(); refreshDrawer(); message("Apps pinned to Home")
                        }
                    }
                    Unit
                })
                add(Triple("Uninstall apps", R.drawable.ic_delete) { uninstallSelected(keys) })
            }
        }
        showActionMenu("App drawer", actions)
    }

    private fun uninstallSelected(keys: Set<String>) {
        val packages = apps.filter { it.key in keys }.map { it.component.packageName }.distinct()
        confirmDialog("Uninstall ${packages.size} app${if (packages.size == 1) "" else "s"}?",
            "Android will ask you to confirm each uninstall.", "Continue") {
                launchNextUninstall(uninstallBatch.start(packages))
                drawerState.clearKeys(); refreshDrawer()
            }
    }

    private fun launchNextUninstall(packageName: String?) {
        if (packageName == null) return
        try {
            uninstallNext.launch(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$packageName"))
                .putExtra(Intent.EXTRA_RETURN_RESULT, true))
        } catch (error: Exception) {
            uninstallBatch.cancel()
            Log.w("Grove", "Could not launch batch uninstall for $packageName", error)
            message("Cannot uninstall $packageName")
        }
    }
    private fun addGrid(items: List<App>, target: LinearLayout) {
        val columns = if (resources.configuration.screenWidthDp >= 600) 6 else 4
        items.chunked(columns).forEach { group ->
            val row = LinearLayout(this)
            group.forEach { app ->
                val tile = createTile(); bindTile(tile, app); pinDragController.attach(tile.layout, app)
                row.addView(tile.layout, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(columns - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            target.addView(row)
        }
    }
    private fun settings() {
        showActionMenu("Grove settings", listOf(
            Triple("Launcher settings", R.drawable.ic_settings) { launcherSettings() },
            Triple("Replay first-run setup", R.drawable.ic_info) { startFirstRunSetup() },
            Triple("Set as default launcher", R.drawable.ic_launcher) {
                val role = getSystemService(RoleManager::class.java)
                if (role.isRoleAvailable(RoleManager.ROLE_HOME))
                    chooseHome.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
            },
            Triple("Add widget", R.drawable.ic_widget) { widgetFlow.pick() },
            Triple("Wallpapers", R.drawable.ic_wallpaper) { wallpapers() },
            Triple("Privacy policy", R.drawable.ic_info) {
                startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/davidcit646/Grove/blob/main/PRIVACY.md")))
            },
            Triple("About Grove", R.drawable.ic_info) {
                infoDialog("Grove · ${BuildConfig.VERSION_NAME}",
                    "A quiet place to start.\n\nFree and open source · Apache 2.0\nNo telemetry. Internet is used only when downloading selected wallpapers.\n\nSwipe down for search and swipe up for all apps when enabled. Swipe down from the top of the app drawer to close it. Hold and drag pinned apps to reorder them. Pinned apps can be placed near the top or bottom of Home.",
                    "Done")
            },
        ))
    }

    private fun startFirstRunSetup() {
        if (firstRunSetup != null || apps.isEmpty()) return
        if (setupPending())
            prefs.edit().remove("widget_tutorial_seen").apply()
        firstRunSetup = FirstRunSetup(
            this, surface, config, apps.map { it.key to it.label },
            ::hasContactAccess, { Environment.isExternalStorageManager() },
            { SearchSourceState.resolve(true, hasContactAccess(), indexingContacts,
                contactLoadFailed, contacts.size) },
            { SearchSourceState.resolve(true, Environment.isExternalStorageManager(), indexingFiles,
                fileLoadFailed, files.size, fileScanSkipped) },
            ::explainContactAccess, ::explainFileAccess,
            finishSetup@{ next ->
                firstRunSetup = null
                val previousSearch = config.search
                if (!commitConfig(next)) return@finishSetup
                applySearchSettings(previousSearch)
                prefs.edit().putBoolean("setup_complete", true).remove("setup_pending").apply()
                showHome()
                val role = getSystemService(RoleManager::class.java)
                if (role.isRoleAvailable(RoleManager.ROLE_HOME) && !role.isRoleHeld(RoleManager.ROLE_HOME)) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Use Grove as your home screen?")
                        .setMessage("Android will ask you to choose a Home app. You can switch back in Android Settings at any time.")
                        .setNegativeButton("Later", null)
                        .setPositiveButton("Choose Home app") { _, _ ->
                            chooseHome.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
                        }.show()
                }
            },
            skipSetup@{
                firstRunSetup = null
                if (setupPending() && config.favorites.isEmpty()) {
                    if (!commitConfig(config.copy(favorites = apps.take(8).map { it.key })))
                        return@skipSetup
                    showHome()
                }
                prefs.edit().putBoolean("setup_complete", true).remove("setup_pending").apply()
            },
        ).also { it.show() }
    }

    private fun launcherSettings() {
        LauncherSettingsScreen(
            this, { config },
            { next ->
                val previousSearch = config.search
                if (commitConfig(next)) {
                    if (previousSearch != next.search) applySearchSettings(previousSearch)
                    if (!drawer) showHome()
                }
            },
            { editConfig() },
            { export.launch("grove-config.json") },
            { importConfig.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            { setupPending() },
            { enabled ->
                prefs.edit().apply {
                    putBoolean("setup_complete", !enabled)
                    if (enabled) putBoolean("setup_pending", true)
                    else remove("setup_pending")
                }.apply()
            },
            {
                if (setupPending()) root.post {
                    if (!isDestroyed && setupPending()) {
                        showHome()
                        startFirstRunSetup()
                    }
                }
            },
        ).show()
    }
    private fun editConfig(initialText: String? = null) {
        val editor = EditText(this).apply {
            filters = arrayOf(InputFilter.LengthFilter(65_536))
            setText(initialText ?: config.json())
            typeface = Typeface.MONOSPACE
            minLines = 8
        }
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Configuration").setView(editor).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener { dialog.getButton(-1).setOnClickListener {
            runCatching { require(editor.length() <= 65536); ConfigStore.parse(editor.text.toString()) }
                .onSuccess { activateConfig(it); dialog.dismiss(); showHome() }.onFailure { editor.error = it.message ?: "Invalid JSON" }
        } }; dialog.show()
    }

    private fun showConfigRecoveryDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Configuration problem")
            .setMessage("Grove couldn’t read your saved custom configuration, so a safe fallback configuration is active. Your custom configuration has been preserved. You can continue editing it and try loading it, or load defaults and start over.")
            .setCancelable(false)
            .setPositiveButton("Edit custom config") { _, _ -> editConfig(configStore.brokenCustomConfig) }
            .setNegativeButton("Load defaults") { _, _ -> activateConfig(Config()); showHome(); message("Default configuration loaded") }
            .show()
    }

    private fun wallpapers() {
        WallpaperPicker(this, wallpaperController, config.wallpaper) { index, which ->
            wallpaperController.apply(index, which) { applied ->
                if (applied && which and WallpaperManager.FLAG_SYSTEM != 0) {
                    if (commitConfig(config.copy(wallpaper = index))) {
                        pendingWallpaper = null
                        artworkStyle = -1
                    }
                    showHome()
                }
            }
        }.show()
    }

}
