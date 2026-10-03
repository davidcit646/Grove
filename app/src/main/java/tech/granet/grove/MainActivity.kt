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
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.os.*
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tech.granet.grove.ui.MenuRow
import tech.granet.grove.ui.confirmDialog
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.infoDialog
import tech.granet.grove.ui.label
import tech.granet.grove.ui.listDialog
import tech.granet.grove.ui.menuDialog
import tech.granet.grove.ui.message
import java.util.*
import java.io.File
import java.nio.file.Files
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
    private var contacts = emptyList<ContactIndex.Contact>()
    private var contactSearch = SearchResults.prepare(contacts) { it.searchName }
    private var contactObserverRegistered = false
    private var contactWarningShown = false
    private var lastContactRefresh = 0L
    private var indexingContacts = false
    private var contactLoadFailed = false
    private val delayedContactRefresh = Runnable { refreshContacts() }
    private val contactObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            searchHandler.removeCallbacks(delayedContactRefresh)
            searchHandler.postDelayed(delayedContactRefresh, 400L)
        }
    }
    private val requestContacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshContacts() else if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
        firstRunSetup?.refreshPermissions()
    }
    private val wallpaperController by lazy { WallpaperController(this, worker, this::message) }
    // Every installed app icon is decoded during app discovery and retained for the
    // lifetime of the launcher process. Drawer rendering never decodes icons.
    private val iconCache get() = AppIconStore
    private var drawerAdapter: AppAdapter? = null
    private var drawerEmpty: TextView? = null
    private var drawerGrid: GridView? = null
    private var drawerVisibleCount = 0
    private val drawerState = DrawerState()
    private data class DrawerDrag(val key: String)
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
    private var drawer = false
    private var searchMode = false
    private var files = emptyList<IndexedFile>()
    private var fileSearch = SearchResults.prepare(files) { it.searchName }
    private var indexingFiles = false
    private var fileLoadFailed = false
    private var fileScanSkipped = 0
    @Volatile private var fileIndexGeneration = 0
    @Volatile private var contactGeneration = 0
    private var searchResults: LinearLayout? = null
    private lateinit var surface: FrameLayout
    private lateinit var root: LinearLayout
    private data class PinDrag(val key: String)
    private var activePinDrag: PinDrag? = null
    private var heldPin: View? = null
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
    private val gestureSession = GestureSession()
    private var touchedScroll: ScrollView? = null
    private var loadingApps = true
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (config.gestures.longPressHomeContextMenu && gestureSession.longPress()) {
            cancelChildTouch()
            settings()
        }
    }

    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { contentResolver.openOutputStream(uri)?.use { it.write(config.json().toByteArray()) } ?: error("Cannot open file") }
            .onFailure { message("Could not export configuration") }
    }
    private val importConfig = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = contentResolver.openInputStream(uri)?.use { input -> val buffer = java.io.ByteArrayOutputStream(); val chunk = ByteArray(4096); while (buffer.size() <= 65536) { val count = input.read(chunk, 0, minOf(chunk.size, 65537 - buffer.size())); if (count < 0) break; buffer.write(chunk, 0, count) }; buffer.toByteArray() } ?: error("Cannot open file")
            require(bytes.size <= 65536) { "Configuration exceeds 64 KB" }
            ConfigStore.parse(bytes.toString(Charsets.UTF_8))
        }.onSuccess { activateConfig(it); showHome(); message("Configuration imported") }
            .onFailure { message(it.message ?: "Invalid configuration") }
    }
    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) configureWidget() else cancelWidget()
    }
    private val configureResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) finishWidget() else {
            Log.w("Grove", "Widget configuration returned ${result.resultCode} for id ${widgets.pending}")
            cancelWidget()
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
        if (!prefs.getBoolean("setup_complete", false) && !prefs.getBoolean("setup_pending", false)) {
            // Existing users keep their layout and can replay setup from the menu.
            prefs.edit().putBoolean(if (prefs.contains("initialized")) "setup_complete" else "setup_pending", true).apply()
        }
        config = configStore.load()
        widgets.restore(savedInstanceState)
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
            if (event.localState !is PinDrag) false else {
                when (event.action) {
                    DragEvent.ACTION_DRAG_LOCATION -> scrollPinDrag(root, event)
                    DragEvent.ACTION_DRAG_ENDED -> {
                        activePinDrag = null
                        releasePinHold()
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
        if (!ensureLauncherCallback()) return
        homeScrollY = savedInstanceState?.getInt("homeScrollY") ?: 0
        showHome(); applyStartupPlan(StartupCoordinator.coldStart(startupSnapshot()))
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
            text = "Grove cannot load your apps"
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
            setOnClickListener { if (ensureLauncherCallback()) loadApps() }
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
        if (plan.clearFiles) {
            fileIndexGeneration++
            files = emptyList()
            fileSearch = SearchResults.prepare(files) { it.searchName }
            indexingFiles = false
            fileLoadFailed = false
            fileScanSkipped = 0
            if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
        }
        if (plan.loadApps) loadApps()
        if (plan.indexFiles) indexFiles()
        if (plan.refreshContacts) refreshContacts()
    }

    override fun onStop() {
        releasePinHold(); longPressHandler.removeCallbacks(longPressRunnable)
        gestureSession.cancel()
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
        longPressHandler.removeCallbacks(longPressRunnable)
        pendingSearch?.let(searchHandler::removeCallbacks)
        searchHandler.removeCallbacks(delayedContactRefresh)
        searchGeneration++
        searchWorker.shutdownNow()
        contactWorker.shutdownNow()
        if (contactObserverRegistered) contentResolver.unregisterContentObserver(contactObserver)
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

    private fun cancelChildTouch() {
        val cancel = MotionEvent.obtain(gestureSession.downTime, SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (firstRunSetup != null) return super.dispatchTouchEvent(event)
        if (heldPin != null || activePinDrag != null) {
            gestureSession.cancel()
            return super.dispatchTouchEvent(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                root.animate().cancel()
                root.translationY = 0f
                root.alpha = 1f
                longPressHandler.removeCallbacks(longPressRunnable)
                val inDrawer = drawer || searchMode
                val grid = drawerGrid
                val atTop = inDrawer && grid != null && !grid.canScrollVertically(-1) &&
                    pointInside(grid, event.rawX, event.rawY)
                val widget = !inDrawer && touchInsideWidget(root, event.rawX, event.rawY)
                val interactive = !inDrawer && touchInsideInteractive(root, event.rawX, event.rawY)
                touchedScroll = if (!inDrawer) scrollAt(root, event.rawX, event.rawY) else null
                val schedule = gestureSession.begin(event.rawX, event.rawY, event.eventTime,
                    inDrawer, atTop, ::root.isInitialized, widget, interactive)
                if (schedule && config.gestures.longPressHomeContextMenu) {
                    longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                val consume = gestureSession.cancel()
                settleSwipeFeedback()
                if (consume) return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = gestureSession.verticalDelta(event.rawY)
                val scrollCanMove = touchedScroll?.canScrollVertically(if (dy > 0) -1 else 1) == true
                val step = gestureSession.move(event.rawX, event.rawY, event.eventTime,
                    ViewConfiguration.get(this).scaledTouchSlop.toFloat(), dp(72).toFloat(),
                    config.gestures, scrollCanMove, dp(24).toFloat())
                if (step.moved) longPressHandler.removeCallbacks(longPressRunnable)
                if (step.cancelChildren) cancelChildTouch()
                step.offset?.let { root.translationY = it }
                if (step.consume) return true
            }
            MotionEvent.ACTION_UP -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                val step = gestureSession.release(event.rawX, event.rawY, event.eventTime,
                    ViewConfiguration.get(this).scaledTouchSlop.toFloat(), dp(72).toFloat(),
                    dp(88).toFloat(), config.gestures, config.gestures.tapHomeContextMenu)
                if (step.cancelChildren) cancelChildTouch()
                if (step.closeDrawer) animateDrawerClosed()
                else if (step.gesture != HomeGesture.NONE) animateHomeGesture(step.gesture)
                else if (step.contextMenu) settings()
                else if (step.settle) settleSwipeFeedback()
                if (step.consume) return true
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun pointInside(view: View, rawX: Float, rawY: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return rawX >= location[0] && rawX < location[0] + view.width &&
            rawY >= location[1] && rawY < location[1] + view.height
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
    private fun scrollAt(view: View, x: Float, y: Float): ScrollView? {
        val bounds = Rect()
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x.toInt(), y.toInt())) return null
        if (view is ScrollView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            scrollAt(view.getChildAt(index), x, y)?.let { return it }
        }
        return null
    }

    private fun touchInsideWidget(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (view is AppWidgetHostView) {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return rawX >= location[0] && rawX < location[0] + view.width &&
                rawY >= location[1] && rawY < location[1] + view.height
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (touchInsideWidget(view.getChildAt(index), rawX, rawY)) return true
            }
        }
        return false
    }

    private fun touchInsideInteractive(view: View, rawX: Float, rawY: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false
        if (view !== root && (view.isClickable || view.isLongClickable || view is EditText || view is AppWidgetHostView)) {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            if (rawX >= location[0] && rawX < location[0] + view.width &&
                rawY >= location[1] && rawY < location[1] + view.height) return true
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                if (touchInsideInteractive(view.getChildAt(index), rawX, rawY)) return true
            }
        }
        return false
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
                        if (!prefs.getBoolean("setup_pending", false) && config.favorites.isEmpty())
                            config = config.copy(favorites = apps.take(8).map { it.key })
                        prefs.edit().putBoolean("initialized", true).apply(); save()
                    }
                    if (coreRecoveryVisible) {
                        coreRecoveryVisible = false
                        root.setBackgroundColor(Color.TRANSPARENT)
                        showHome()
                    } else if (drawer) renderApps(searchField?.text?.toString().orEmpty())
                    else if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
                    else if (activePinDrag == null && heldPin == null) showHome()
                    if (prefs.getBoolean("setup_pending", false) && firstRunSetup == null &&
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
    private fun save() = configStore.save(config)
    private fun activateConfig(next: Config) {
        val previousSearch = config.search
        config = next
        configStore.activate(config)
        if (previousSearch != next.search) applySearchSettings(previousSearch)
    }
    private fun button(text: String, action: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        val colors = if (config.homeScreen.useWallpaperButtonColors && artworkStyle == config.wallpaper && artwork != null) {
            wallpaperButtonColors ?: ThemeColors.wallpaperButtonColors(artwork!!).also { wallpaperButtonColors = it }
        } else ThemeColors.buttonSurface(this@MainActivity) to ThemeColors.onButtonSurface(this@MainActivity)
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
                wallpaperController.background(key.first, width, height) { prepared ->
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
                            wallpaperButtonColors = null
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
            ::renderPinnedApps, ::renderWidgets)
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
            heading.addView(label("PINNED APPS", 12f), LinearLayout.LayoutParams(0, -2, 1f))
            target.addView(heading)
            target.addView(label("Hold and drag to move. Hold and release for options.", 12f))
        }
        addGrid(apps.filter { it.key in config.favorites }.sortedBy { config.favorites.indexOf(it.key) }, target)
        if (config.favorites.isEmpty()) target.addView(label("Long-press an app in the drawer to pin it here."))
    }

    private fun indexFiles() {
        if (!config.search.files || !Environment.isExternalStorageManager() || indexingFiles) return
        val generation = ++fileIndexGeneration
        indexingFiles = true
        fileLoadFailed = false
        if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
        worker.execute {
            if (generation != fileIndexGeneration) return@execute
            val result = runCatching {
                val scan = FileIndex.scan(Environment.getExternalStorageDirectory(),
                    shouldContinue = { generation == fileIndexGeneration })
                scan to SearchResults.prepare(scan.files) { it.searchName }
            }
            runOnUiThread(Runnable {
                if (generation != fileIndexGeneration || isDestroyed) return@Runnable
                indexingFiles = false
                result.onSuccess {
                    files = it.first.files
                    fileSearch = it.second
                    fileScanSkipped = it.first.skippedDirectories
                    fileLoadFailed = false
                }.onFailure {
                    files = emptyList()
                    fileSearch = SearchResults.prepare(files) { it.searchName }
                    fileScanSkipped = 0
                    fileLoadFailed = true
                    Log.w("Grove", "Could not index shared storage", it)
                    message("Could not index shared storage")
                }
                if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
            })
        }
    }

    private fun hasContactAccess() = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun refreshContacts() {
        if (!config.search.contacts || !hasContactAccess()) {
            contactGeneration++
            contacts = emptyList()
            contactSearch = SearchResults.prepare(contacts) { it.searchName }
            indexingContacts = false
            contactLoadFailed = false
            if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
            return
        }
        if (!contactObserverRegistered) {
            runCatching { contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI,
                true, contactObserver); contactObserverRegistered = true }
        }
        if (contactWorker.isShutdown) return
        indexingContacts = true
        contactLoadFailed = false
        if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
        val generation = ++contactGeneration
        contactWorker.execute {
            if (generation != contactGeneration) return@execute
            val result = runCatching {
                val loaded = ContactIndex.load(contentResolver) { generation == contactGeneration }
                loaded to SearchResults.prepare(loaded) { it.searchName }
            }
            runOnUiThread {
                if (isDestroyed || generation != contactGeneration || !config.search.contacts || !hasContactAccess()) return@runOnUiThread
                indexingContacts = false
                result.onSuccess {
                    contacts = it.first
                    contactSearch = it.second
                    lastContactRefresh = SystemClock.elapsedRealtime()
                    contactLoadFailed = false
                    if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
                    if (it.first.isEmpty() && !contactWarningShown) {
                        contactWarningShown = true
                        infoDialog("No device contacts found",
                            "Grove can search contacts available through Android. If your contacts are kept only inside another app, enable its device contact sync.")
                    }
                }
                    .onFailure {
                        contacts = emptyList()
                        contactSearch = SearchResults.prepare(contacts) { it.searchName }
                        contactLoadFailed = true
                        if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
                        Log.w("Grove", "Contacts provider unavailable", it)
                        if (!contactWarningShown) {
                            contactWarningShown = true
                            infoDialog("Contact search unavailable",
                                "Grove couldn't read the device's contacts provider. Check that a contacts app is enabled and contact access is allowed.")
                        }
                    }
            }
        }
    }

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
        if (!config.search.contacts) {
            contactGeneration++
            contacts = emptyList()
            contactSearch = SearchResults.prepare(contacts) { it.searchName }
            indexingContacts = false
            contactLoadFailed = false
            searchHandler.removeCallbacks(delayedContactRefresh)
            if (contactObserverRegistered) {
                contentResolver.unregisterContentObserver(contactObserver)
                contactObserverRegistered = false
            }
        } else if (!previous.contacts) {
            if (hasContactAccess()) refreshContacts() else explainContactAccess()
        }
        if (!config.search.files) {
            fileIndexGeneration++
            files = emptyList()
            fileSearch = SearchResults.prepare(files) { it.searchName }
            indexingFiles = false
            fileLoadFailed = false
            fileScanSkipped = 0
        } else if (!previous.files) {
            if (Environment.isExternalStorageManager()) indexFiles() else explainFileAccess()
        }
        if (searchMode) renderSearch(searchField?.text?.toString().orEmpty())
    }

    private fun showSearch(animate: Boolean = false) {
        rememberHomeScroll()
        clearAppSelection()
        drawer = false; searchMode = true; base()
        root.addView(label("Search", 30f))
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

    private fun sharedFileUri(file: File): Uri {
        require(config.search.files && Environment.isExternalStorageManager()) { "File search is unavailable" }
        val root = Environment.getExternalStorageDirectory().canonicalFile
        val canonical = file.canonicalFile
        require(canonical.path.startsWith("${root.path}${File.separator}") && file.exists() &&
            !Files.isSymbolicLink(file.toPath())) { "File is outside shared storage" }
        return FileProvider.getUriForFile(this, "$packageName.files", canonical)
    }

    private fun contactMenu(contact: ContactIndex.Contact) {
        if (!config.search.contacts || !hasContactAccess()) return
        contactWorker.execute {
            val details = runCatching { ContactIndex.details(contentResolver, resources, contact) }
                .getOrElse { Log.w("Grove", "Cannot read contact details", it); ContactIndex.Details(emptyList(), emptyList()) }
            runOnUiThread {
                if (isDestroyed || !config.search.contacts || !hasContactAccess()) return@runOnUiThread
                val actions = mutableListOf<Triple<String, Int, () -> Unit>>()
                fun action(label: String, icon: Int, intent: () -> Intent) {
                    actions.add(Triple(label, icon) {
                        runCatching { startActivity(intent()) }.onFailure { message("No compatible app is available") }
                    })
                }
                val waTargets = ContactIndex.whatsAppTargets(details.channels) { pkg ->
                    apps.any { it.component.packageName == pkg }
                }
                val numbers = details.numbers
                fun callRow(number: ContactIndex.Number) = Triple("${number.label} · ${number.value}", R.drawable.ic_call) {
                    runCatching {
                        startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.value, null)), "Call with"))
                    }.onFailure { message("No compatible app is available") }
                    Unit
                }
                fun textRow(number: ContactIndex.Number) = Triple("${number.label} · ${number.value}", R.drawable.ic_message) {
                    runCatching {
                        startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number.value, null)), "Message with"))
                    }.onFailure { message("No compatible app is available") }
                    Unit
                }
                // One Call row and one Text row per contact. Tapping always asks which app
                // (Phone, Google Voice, Linphone, …) should place it. A number picker only
                // appears when the contact genuinely has several different numbers.
                when {
                    numbers.size == 1 -> {
                        actions.add(callRow(numbers[0]))
                        actions.add(textRow(numbers[0]))
                    }
                    numbers.size > 1 -> {
                        actions.add(Triple("Call", R.drawable.ic_call) {
                            showActionMenu("Call ${contact.name}", numbers.map(::callRow))
                            Unit
                        })
                        actions.add(Triple("Text", R.drawable.ic_message) {
                            showActionMenu("Text ${contact.name}", numbers.map(::textRow))
                            Unit
                        })
                    }
                }
                numbers.forEach { number ->
                    val international = PhoneNumberUtils.formatNumberToE164(number.value, Locale.getDefault().country)
                    if (international != null && international.startsWith('+') && international.length in 9..16) {
                        val digits = international.drop(1)
                        waTargets.forEach { target ->
                            action("Message ${number.label} via ${target.label}", R.drawable.ic_message) {
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).setPackage(target.packageName)
                            }
                        }
                    }
                }
                ContactIndex.collapseChannels(details.channels).forEach { channel ->
                    val packageName = when (channel.label) {
                        "WhatsApp" -> if (channel.mime.contains("w4b")) "com.whatsapp.w4b" else "com.whatsapp"
                        "Messenger" -> "com.facebook.orca"
                        else -> null
                    }
                    if (packageName != null && apps.any { it.component.packageName == packageName }) {
                        action("Open in ${channel.label}", R.drawable.ic_message) {
                            Intent(Intent.ACTION_VIEW, channel.uri).setPackage(packageName)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                }
                action("View contact card", R.drawable.ic_contact) { Intent(Intent.ACTION_VIEW, contact.uri) }
                action("Edit contact", R.drawable.ic_edit) {
                    Intent(Intent.ACTION_EDIT).setDataAndType(contact.uri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
                }
                showActionMenu(contact.name, actions)
            }
        }
    }

    private fun openFile(file: IndexedFile) {
        val uri = runCatching { sharedFileUri(file.file) }
            .getOrElse { Log.w("Grove", "Cannot share indexed file", it); message("Cannot open this file"); return }
        fun openAs(mime: String) = runCatching {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }.isSuccess
        if (!openAs(file.mime) && !openAs("*/*")) {
            Log.w("Grove", "No app handles MIME type ${file.mime}")
            message("No app can open this file")
        }
    }

    private fun openPlayStore(query: String, install: Boolean = false) {
        val encoded = Uri.encode(query)
        val marketUrl = if (install) "market://search?q=$encoded&c=apps" else "market://search?q=$encoded"
        val webUrl = if (install) "https://play.google.com/store/search?q=$encoded&c=apps" else "https://play.google.com/store/search?q=$encoded"
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(marketUrl))) }
            .onFailure { openWeb(webUrl) }
    }

    private fun appMenu(app: App) {
        val pinned = app.key in config.favorites
        showActionMenu(app.label, listOf(
            Triple(if (pinned) "Unpin from home" else "Pin to home", R.drawable.ic_grid) {
                config = config.copy(favorites = if (pinned) config.favorites - app.key else config.favorites + app.key)
                save(); if (!drawer) showHome()
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

    private fun webResultMenu(query: String, provider: String) {
        showActionMenu("$provider search", listOf(
            Triple("Search with Google", R.drawable.ic_public) { openWeb("https://www.google.com/search?q=${Uri.encode(query)}") },
            Triple("Ask an AI (ChatGPT)", R.drawable.ic_ai) { openWeb("https://chatgpt.com/?q=${Uri.encode(query)}") },
            Triple("Ask an AI (Gemini)", R.drawable.ic_ai) { openWeb("https://gemini.google.com/app?q=${Uri.encode(query)}") },
            Triple("Copy search text", R.drawable.ic_copy) {
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Search", query))
                message("Search copied")
            },
            Triple("Share search", R.drawable.ic_share) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, query), "Share search"))
            },
        ))
    }

    private fun playStoreMenu(query: String) {
        showActionMenu("Play Store search", listOf(
            Triple("Install an app", R.drawable.ic_download) { openPlayStore(query, install = true) },
            Triple("View in Play Store", R.drawable.ic_store) { openPlayStore(query) },
            Triple("Leave a Play Store review", R.drawable.ic_star) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://writeReview?package=$packageName"))) }
                    .onFailure { openWeb("https://play.google.com/store/apps/details?id=$packageName") }
            },
            Triple("Copy search text", R.drawable.ic_copy) {
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Search", query))
                message("Search copied")
            },
        ))
    }

    private fun showActionMenu(title: String, actions: List<Triple<String, Int, () -> Unit>>) {
        menuDialog(title, actions.map { (name, icon, action) -> MenuRow(name, icon, action) })
    }

    private fun openWeb(url: String) {
        runCatching {
            val uri = Uri.parse(url)
            require(uri.scheme == "https") { "Only HTTPS links are supported" }
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
            .onFailure { message("No app can open this search") }
    }

    private fun showDrawer(keyboard: Boolean, animate: Boolean = false) {
        rememberHomeScroll()
        clearAppSelection()
        drawer = true; searchMode = false; base()
        root.requestFocus()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("All apps", 30f), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_settings)
            imageTintList = ColorStateList.valueOf(ThemeColors.icon(this@MainActivity))
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
        val empty = label(if (loadingApps) "Preparing apps and icons…" else "No matching apps").apply { gravity = Gravity.CENTER }
        drawerEmpty = empty
        val grid = GridView(this).apply {
            numColumns = if (resources.configuration.screenWidthDp >= 600) 6 else 4
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = dp(4)
            clipToPadding = false
        }
        drawerGrid = grid
        drawerAdapter = AppAdapter()
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
            config.folders.map { DrawerItem.Folder(it) } + apps.take(drawerVisibleCount)
                .filterNot { it.key in assigned }.map { DrawerItem.Application(it) }
        } else SearchResults.matching(apps, prepared) { it.searchName }.map { DrawerItem.Application(it) }
        drawerAdapter?.submit(filtered)
    }
    private sealed class DrawerItem {
        data class Application(val app: App) : DrawerItem()
        data class Folder(val folder: AppFolder) : DrawerItem()
    }
    private class Tile(val layout: LinearLayout, val icon: ImageView, val name: TextView, val badge: ImageView)
    private fun createTile(): Tile {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(10))
            isFocusable = true; isClickable = true
        }
        val icon = ImageView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        val name = label("", 12f).apply { gravity = Gravity.CENTER; maxLines = 2; minLines = 2 }
        val iconFrame = FrameLayout(this)
        iconFrame.addView(icon, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        val badge = ImageView(this).apply {
            setPadding(dp(7), dp(7), dp(7), dp(7))
            imageTintList = ColorStateList.valueOf(ThemeColors.icon(this@MainActivity))
            background = GradientDrawable().apply {
                setColor(ThemeColors.iconSurface(this@MainActivity))
                cornerRadius = dp(10).toFloat()
            }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        iconFrame.addView(badge, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.TOP or Gravity.END))
        layout.addView(iconFrame, LinearLayout.LayoutParams(dp(56), dp(52)))
        layout.addView(name)
        return Tile(layout, icon, name, badge).also { layout.tag = it }
    }
    private fun bindTile(tile: Tile, app: App) {
        tile.badge.visibility = View.GONE
        tile.icon.alpha = 1f
        tile.layout.contentDescription = app.label; tile.name.text = app.label; tile.icon.imageTintList = null
        bindIcon(tile.icon, app)
        tile.layout.setOnClickListener {
            runCatching { launcher.startMainActivity(app.component, android.os.Process.myUserHandle(), null, null) }
                .onFailure { message("This app is unavailable"); loadApps() }
        }
        tile.layout.setOnLongClickListener { appMenu(app); true }
        tile.layout.setOnTouchListener(null)
        tile.layout.setOnDragListener(null)
    }
    private fun bindIcon(view: ImageView, app: App) {
        view.tag = app.key
        val cached = iconCache[app.key]
        view.setImageBitmap(cached)
    }
    private inner class AppAdapter : BaseAdapter() {
        private var items = emptyList<DrawerItem>()
        fun submit(next: List<DrawerItem>) { items = next; notifyDataSetChanged() }
        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val tile = (convertView?.tag as? Tile) ?: createTile()
            when (val item = items[position]) {
                is DrawerItem.Application -> bindDrawerApp(tile, item.app)
                is DrawerItem.Folder -> bindDrawerFolder(tile, item.folder)
            }
            return tile.layout
        }
    }
    private fun refreshDrawer() { if (drawer && !searchMode) renderApps(searchField?.text?.toString().orEmpty()) }

    private fun bindDrawerApp(tile: Tile, app: App) {
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
        } else configureDrawerDrag(tile.layout, app)
        tile.layout.setOnDragListener { view, event ->
            if (event.localState !is DrawerDrag) false else {
                when (event.action) {
                    DragEvent.ACTION_DRAG_ENTERED -> view.alpha = 0.55f
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> view.alpha = 1f
                    DragEvent.ACTION_DROP -> {
                        view.alpha = 1f
                        val source = (event.localState as DrawerDrag).key
                        if (source != app.key) promptFolderName(listOf(source, app.key))
                    }
                }
                true
            }
        }
    }

    private fun bindDrawerFolder(tile: Tile, folder: AppFolder) {
        tile.layout.setOnTouchListener(null)
        tile.layout.contentDescription = "Folder ${folder.name}"
        tile.name.text = folder.name
        tile.icon.setImageResource(R.drawable.ic_folder)
        tile.icon.imageTintList = ColorStateList.valueOf(ThemeColors.icon(this))
        tile.icon.alpha = 1f
        tile.badge.visibility = View.GONE
        tile.layout.setOnClickListener { openFolder(folder.name) }
        tile.layout.setOnLongClickListener { folderOptions(folder.name); true }
        tile.layout.setOnDragListener { view, event ->
            if (event.localState !is DrawerDrag) false else {
                when (event.action) {
                    DragEvent.ACTION_DRAG_ENTERED -> view.alpha = 0.55f
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> view.alpha = 1f
                    DragEvent.ACTION_DROP -> {
                        view.alpha = 1f
                        moveAppsToFolder(setOf((event.localState as DrawerDrag).key), folder.name)
                    }
                }
                true
            }
        }
    }

    private fun folderOptions(name: String) {
        showActionMenu(name, listOf(
            Triple("Open folder", R.drawable.ic_folder) { openFolder(name) },
            Triple("Rename folder", R.drawable.ic_edit) { promptRenameFolder(name) },
            Triple("Delete folder", R.drawable.ic_delete) {
                confirmDialog("Delete $name?", "Apps in this folder will return to All apps.", "Delete") {
                        DrawerState.deleteFolder(config, name)?.let { config = it; save(); refreshDrawer() }
                    }
            },
        ))
    }

    private fun openFolder(name: String) {
        val folder = config.folders.firstOrNull { it.name == name } ?: return
        val members = folder.apps.mapNotNull { key -> apps.firstOrNull { it.key == key } }
        val grid = GridView(this).apply {
            numColumns = 4; verticalSpacing = dp(8)
            adapter = object : BaseAdapter() {
                override fun getCount() = members.size
                override fun getItem(position: Int) = members[position]
                override fun getItemId(position: Int) = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val tile = (convertView?.tag as? Tile) ?: createTile()
                    bindTile(tile, members[position])
                    tile.layout.setOnLongClickListener {
                        showActionMenu(members[position].label, listOf(
                            Triple("Remove from folder", R.drawable.ic_delete) {
                                removeFromFolders(setOf(members[position].key)); refreshDrawer(); openFolder(name)
                            },
                            Triple("App options", R.drawable.ic_settings) { appMenu(members[position]) },
                        )); true
                    }
                    return tile.layout
                }
            }
        }
        grid.layoutParams = ViewGroup.LayoutParams(-1, dp(320))
        MaterialAlertDialogBuilder(this).setTitle(name).setView(grid).setPositiveButton("Done", null).show()
    }

    private fun removeFromFolders(keys: Set<String>) {
        config = DrawerState.removeFromFolders(config, keys)
        save()
    }

    private fun moveAppsToFolder(keys: Set<String>, name: String) {
        val next = DrawerState.moveToFolder(config, keys, name) ?: return
        config = next
        drawerState.clearKeys(); save(); refreshDrawer()
    }

    private fun promptFolderName(keys: List<String> = emptyList()) {
        val input = EditText(this).apply { hint = "Folder name"; isSingleLine = true; setPadding(dp(24), dp(16), dp(24), dp(16)) }
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Create folder").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Create", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val next = DrawerState.createFolder(config, input.text.toString(), keys)
                if (next == null) {
                    input.error = "Choose a unique folder name up to 40 characters"
                    return@setOnClickListener
                }
                config = next
                drawerState.clearKeys(); save(); refreshDrawer(); dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun promptRenameFolder(name: String) {
        val input = EditText(this).apply { setText(name); isSingleLine = true; setPadding(dp(24), dp(16), dp(24), dp(16)) }
        MaterialAlertDialogBuilder(this).setTitle("Rename folder").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val next = DrawerState.renameFolder(config, name, input.text.toString())
                if (next == null) message("Choose a unique folder name up to 40 characters")
                else { config = next; save(); refreshDrawer() }
            }.show()
    }

    private fun drawerOptions() {
        val keys = drawerState.keys
        val actions = buildList {
            add(Triple(if (drawerState.selecting) "Done selecting" else "Select apps", R.drawable.ic_grid) {
                drawerState.toggleMode()
                refreshDrawer()
            })
            add(Triple("Create folder", R.drawable.ic_folder) { promptFolderName() })
            if (keys.isNotEmpty()) {
                add(Triple("Add to new folder", R.drawable.ic_folder) { promptFolderName(keys.toList()) })
                if (config.folders.isNotEmpty()) add(Triple("Move to folder", R.drawable.ic_folder) { chooseFolder(keys) })
                add(Triple("Pin to home screen", R.drawable.ic_home) {
                    DrawerState.pin(config, keys)?.let { config = it; save() }
                    drawerState.clearKeys(); refreshDrawer(); message("Apps pinned to Home")
                })
                add(Triple("Uninstall apps", R.drawable.ic_delete) { uninstallSelected(keys) })
            }
        }
        showActionMenu("App drawer", actions)
    }

    private fun chooseFolder(keys: Set<String>) {
        val names = config.folders.map { it.name }
        listDialog("Move to folder", names) { index -> moveAppsToFolder(keys, names[index]) }
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
                val tile = createTile(); bindTile(tile, app); configurePinDrag(tile.layout, app)
                row.addView(tile.layout, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(columns - group.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            target.addView(row)
        }
    }
    private fun scrollPinDrag(target: View, event: DragEvent) {
        val scroll = body.parent as? ScrollView ?: return
        val bounds = Rect(); scroll.getGlobalVisibleRect(bounds)
        val location = IntArray(2); target.getLocationOnScreen(location)
        val y = event.y + location[1]
        if (y < bounds.top + dp(64)) scroll.smoothScrollBy(0, -dp(28))
        else if (y > bounds.bottom - dp(64)) scroll.smoothScrollBy(0, dp(28))
    }

    private fun releasePinHold() {
        heldPin?.apply {
            scaleX = 1f; scaleY = 1f
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        heldPin = null
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun configureDrawerDrag(view: View, app: App) {
        var downX = 0f
        var downY = 0f
        var touchActive = false
        var dragArmed = false
        var dragging = false
        fun release() {
            dragArmed = false
            view.scaleX = 1f; view.scaleY = 1f
            view.parent?.requestDisallowInterceptTouchEvent(false)
        }
        view.setOnLongClickListener {
            // A stationary hold opens actions on release. Moving after the
            // hold starts a drag, so either gesture can be used on one app.
            if (!touchActive) appMenu(app) else {
                dragArmed = true
                view.parent?.requestDisallowInterceptTouchEvent(true)
                view.scaleX = 1.08f; view.scaleY = 1.08f
            }
            true
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    touchActive = true; dragging = false
                }
                MotionEvent.ACTION_MOVE -> if (dragArmed) {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (dx * dx + dy * dy > slop * slop) {
                        dragging = view.startDragAndDrop(null, View.DragShadowBuilder(view), DrawerDrag(app.key), 0)
                        release()
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    touchActive = false
                    if (dragArmed) {
                        release(); view.isPressed = false; appMenu(app)
                        return@setOnTouchListener true
                    }
                    if (dragging) { dragging = false; return@setOnTouchListener true }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    touchActive = false; dragging = false
                    view.cancelLongPress(); view.isPressed = false
                    if (dragArmed) release()
                }
            }
            false
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun configurePinDrag(view: View, app: App) {
        var downX = 0f
        var downY = 0f
        var touchActive = false
        var touchCanceled = false
        view.setOnLongClickListener {
            // Accessibility long-click has no pointer to drag: open app options directly.
            if (!touchActive) appMenu(app) else {
                heldPin = view
                gestureSession.cancel()
                longPressHandler.removeCallbacks(longPressRunnable)
                view.parent.requestDisallowInterceptTouchEvent(true)
                view.scaleX = 1.08f; view.scaleY = 1.08f
            }
            true
        }
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; touchActive = true; touchCanceled = false
                }
                MotionEvent.ACTION_MOVE -> if (heldPin === view) {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    val slop = ViewConfiguration.get(this).scaledTouchSlop
                    if (dx * dx + dy * dy > slop * slop) {
                        val drag = PinDrag(app.key)
                        activePinDrag = drag
                        val started = view.startDragAndDrop(null, View.DragShadowBuilder(view), drag, 0)
                        releasePinHold()
                        if (!started) activePinDrag = null
                    }
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    touchActive = false
                    if (touchCanceled) { view.isPressed = false; return@setOnTouchListener true }
                    if (heldPin === view) {
                        releasePinHold(); view.isPressed = false; appMenu(app)
                        return@setOnTouchListener true
                    }
                    if (activePinDrag != null) return@setOnTouchListener true
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    touchActive = false; touchCanceled = true
                    view.cancelLongPress(); view.isPressed = false
                    if (heldPin === view) releasePinHold()
                }
            }
            false
        }
        view.setOnDragListener { target, event ->
            val drag = event.localState as? PinDrag
            if (drag == null) false else {
                when (event.action) {
                    DragEvent.ACTION_DRAG_LOCATION -> scrollPinDrag(target, event)
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        target.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100L).start()
                    }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                        target.animate().scaleX(1f).scaleY(1f).setDuration(100L).start()
                    }
                    DragEvent.ACTION_DROP -> {
                        DrawerState.movePin(config, drag.key, app.key)?.let { config = it; save() }
                    }
                }
                true
            }
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
            Triple("Add widget", R.drawable.ic_widget) { pickWidget() },
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
        if (prefs.getBoolean("setup_pending", false))
            prefs.edit().remove("widget_tutorial_seen").apply()
        firstRunSetup = FirstRunSetup(
            this, surface, config, apps.map { it.key to it.label },
            ::hasContactAccess, { Environment.isExternalStorageManager() },
            ::explainContactAccess, ::explainFileAccess,
            { next ->
                firstRunSetup = null
                val previousSearch = config.search
                config = next
                save()
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
            {
                firstRunSetup = null
                if (prefs.getBoolean("setup_pending", false) && config.favorites.isEmpty()) {
                    config = config.copy(favorites = apps.take(8).map { it.key })
                    save()
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
                config = next
                save()
                if (previousSearch != next.search) applySearchSettings(previousSearch)
                if (!drawer) showHome()
            },
            { editConfig() },
            { export.launch("grove-config.json") },
            { importConfig.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            { prefs.getBoolean("setup_pending", false) },
            { enabled ->
                prefs.edit().apply {
                    putBoolean("setup_complete", !enabled)
                    if (enabled) putBoolean("setup_pending", true)
                    else remove("setup_pending")
                }.apply()
            },
            {
                if (prefs.getBoolean("setup_pending", false)) root.post {
                    if (!isDestroyed && prefs.getBoolean("setup_pending", false)) {
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

    // Widget lifecycle: allocate -> bind consent -> optional configuration -> persist.
    private fun pickWidget() {
        if (widgets.pending != -1) {
            MaterialAlertDialogBuilder(this).setTitle("Widget setup interrupted")
                .setMessage("Retry saving the pending widget or remove it before adding another.")
                .setPositiveButton("Retry") { _, _ -> configureWidget() }
                .setNegativeButton("Remove") { _, _ -> cancelWidget() }
                .show()
            return
        }
        if (!prefs.getBoolean("widget_tutorial_seen", false)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Widget controls")
                .setMessage("Once a widget is on your Home screen, tap and hold it for options such as resizing, configuring, or removing it.")
                .setPositiveButton("Got It!") { _, _ ->
                    prefs.edit().putBoolean("widget_tutorial_seen", true).apply()
                    showWidgetPicker()
                }
                .setNegativeButton("Not now", null)
                .show()
            return
        }
        showWidgetPicker()
    }
    private fun showWidgetPicker() {
        val providers = runCatching {
            manager.installedProviders.sortedBy { it.loadLabel(packageManager).lowercase() }
        }.onFailure { Log.w("Grove", "Widget providers unavailable", it) }.getOrNull()
        if (providers.isNullOrEmpty()) { message("No widgets available"); return }
        listDialog("Add widget", providers.map { it.loadLabel(packageManager).toString() }, negative = "Cancel") { index ->
            val provider = providers.getOrNull(index) ?: return@listDialog
            val id = runCatching { widgets.allocate() }
                .onFailure { Log.e("Grove", "Widget allocation failed", it) }.getOrNull()
                ?: run { message("Cannot allocate widget"); return@listDialog }
            val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider.provider) }
                .onFailure { Log.e("Grove", "Widget bind failed", it) }.getOrNull()
            if (bound == true) configureWidget()
            else if (bound == false) runCatching {
                bindWidget.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider))
            }.onFailure { Log.e("Grove", "Widget bind launch failed", it); cancelWidget(); message("Cannot bind this widget") }
            else { cancelWidget(); message("Cannot bind this widget") }
        }
    }

    private fun configureWidget() {
        val id = widgets.pending
        val info = manager.getAppWidgetInfo(id) ?: run {
            Log.w("Grove", "No widget provider info for id $id")
            cancelWidget(); message("Couldn't add this widget"); return
        }
        if (info.configure != null) {
            runCatching { configureResult.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                .setComponent(info.configure).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)) }
                .onFailure { Log.e("Grove", "Widget configuration failed to launch for ${info.configure}", it); cancelWidget(); message("Widget configuration unavailable") }
        } else finishWidget()
    }

    private fun finishWidget() {
        if (widgets.finish()) showHome()
        else message("Couldn't save this widget; retry or remove it")
    }

    private fun cancelWidget() {
        if (!widgets.cancel()) message("Couldn't release widget; retry")
    }

    private fun renderWidgets(target: LinearLayout) =
        WidgetScreen(this, manager, host, prefs, widgets.ids, widgets::remove) { showHome() }.render(target)

    private fun wallpapers() {
        WallpaperPicker(this, wallpaperController, config.wallpaper) { index, which ->
            wallpaperController.apply(index, which) { applied ->
                if (applied && which and WallpaperManager.FLAG_SYSTEM != 0) {
                    config = config.copy(wallpaper = index)
                    save()
                    pendingWallpaper = null
                    artworkStyle = -1
                    showHome()
                }
            }
        }.show()
    }

}
