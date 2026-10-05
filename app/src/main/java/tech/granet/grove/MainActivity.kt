package tech.granet.grove

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.LauncherApps
import android.graphics.*
import android.util.Log
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.message
import java.util.*
import java.util.concurrent.Executors

/** Root HOME activity. Owns navigation; Android owns external apps and widget providers. */
class MainActivity : AppCompatActivity() {
    internal val catalogController by lazy { CatalogController(this) }
    internal val configController by lazy { ConfigController(this) }
    internal val homeController by lazy { HomeController(this) }
    internal val searchController by lazy { SearchController(this) }
    internal val actionController by lazy { ActionController(this) }
    internal val drawerController by lazy { DrawerController(this) }
    internal val setupController by lazy { SetupController(this) }
    internal val startupController by lazy { StartupController(this) }
    internal val wallpaperPresentationController by lazy { WallpaperPresentationController(this) }

    internal val prefs by lazy { getSharedPreferences("grove", MODE_PRIVATE) }
    internal val launcher by lazy { getSystemService(LauncherApps::class.java) }
    // Framework context avoids AppCompat substitutions in widget RemoteViews.
    internal val manager by lazy { AppWidgetManager.getInstance(applicationContext) }
    internal val host by lazy { AppWidgetHost(applicationContext, 1024) }
    internal val worker = Executors.newSingleThreadExecutor()
    internal val contactWorker = Executors.newSingleThreadExecutor()
    internal val requestContacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        searchController.sources.reconcile()
        if (searchMode) searchController.renderSearch(searchController.searchField?.text?.toString().orEmpty())
        setupController.firstRunSetup?.refreshPermissions()
    }
    internal val wallpaperController by lazy { WallpaperController(this, worker, this::message) }
    internal val uninstallNext = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        catalogController.loadApps()
        if (result.resultCode == RESULT_OK) actionController.launchNextUninstall(actionController.uninstallBatch.accepted())
        else {
            val remaining = actionController.uninstallBatch.cancel()
            if (remaining > 0) message("Remaining uninstalls canceled")
        }
    }
    internal val widgets by lazy { WidgetRegistry(host, manager, prefs) }
    internal val widgetFlow: WidgetFlow by lazy {
        WidgetFlow(this, manager, host, prefs, widgets,
            { bindWidget.launch(it) }, { configureResult.launch(it) }, { homeController.showHome() }, this::message)
    }
    internal var drawer = false
    internal var searchMode = false
    internal lateinit var surface: FrameLayout
    internal lateinit var root: LinearLayout
    internal val pinDragController by lazy {
        PinDragController(this, { homeController.body.parent as? ScrollView }, { configController.config }, configController::commitConfig,
            actionController::appMenu, touchRouter::cancel)
    }
    internal val touchRouter by lazy {
        HomeTouchRouter(this, { root }, { drawerController.drawerGrid }, { drawer || searchMode },
            { configController.config.gestures }, setupController::settings, homeController::animateHomeGesture, homeController::animateDrawerClosed)
    }
    internal val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) configController.exportDocument(uri)
    }
    internal val importConfig = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) configController.importDocument(uri)
    }
    internal val bindWidget: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) widgetFlow.configure() else widgetFlow.cancel()
    }
    internal val configureResult: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) widgetFlow.finish() else {
            Log.w("Grove", "Widget configuration returned ${result.resultCode} for id ${widgets.pending}")
            widgetFlow.cancel()
        }
    }
    internal val chooseHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    internal val changes = object : LauncherApps.Callback() {
        override fun onPackageAdded(p: String, u: UserHandle) { catalogController.loadApps(p); if (configController.config.search.contactIndexing) searchController.refreshContacts() }
        override fun onPackageRemoved(p: String, u: UserHandle) { catalogController.loadApps(); if (configController.config.search.contactIndexing) searchController.refreshContacts() }
        override fun onPackageChanged(p: String, u: UserHandle) { catalogController.loadApps(p); if (configController.config.search.contactIndexing) searchController.refreshContacts() }
        override fun onPackagesAvailable(p: Array<out String>, u: UserHandle, replacing: Boolean) = catalogController.loadApps()
        override fun onPackagesUnavailable(p: Array<out String>, u: UserHandle, replacing: Boolean) = catalogController.loadApps()
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
                        root.post { if (!drawer && !isDestroyed && !startupController.coreRecoveryVisible) homeController.showHome() }
                    }
                }
                true
            }
        }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (setupController.firstRunSetup != null) setupController.firstRunSetup?.back()
                else if (drawer && drawerController.drawerState.selecting) { drawerController.clearAppSelection(); drawerController.refreshDrawer() }
                else if (drawer || searchMode) homeController.animateDrawerClosed()
                // Back at Home has no navigation destination. Recreating the
                // view here would unexpectedly jump a scrolled layout to top.
            }
        })
        startupController.startupState = savedInstanceState
        runCatching {
            if (!prefs.getBoolean("setup_complete", false) && !setupController.setupPending()) {
                // Existing users keep their layout and can replay setup from the menu.
                prefs.edit().putBoolean(
                    if (prefs.contains("initialized")) "setup_complete" else "setup_pending", true
                ).apply()
            }
        }.onFailure { Log.w("Grove", "Setup state unavailable", it) }
        startupController.beginHome()
    }

    override fun onStart() {
        super.onStart()
        runCatching { host.startListening() }
            .onFailure { Log.w("Grove", "Widget listening unavailable", it); message("Widgets unavailable") }
    }
    override fun onResume() {
        super.onResume()
        setupController.firstRunSetup?.refreshPermissions()
        if (startupController.coreRecoveryVisible) return
        if (searchMode) searchController.renderSearch(searchController.searchField?.text?.toString().orEmpty())
        startupController.applyStartupPlan(StartupCoordinator.resume())
    }

    override fun onStop() {
        pinDragController.releaseHold(); touchRouter.cancel()
        runCatching { host.stopListening() }.onFailure { Log.w("Grove", "Widget stop failed", it) }
        drawerController.clearAppSelection()
        if (drawer) drawerController.refreshDrawer()
        super.onStop()
    }
    override fun onDestroy() {
        if (startupController.launcherCallbackRegistered) {
            try { launcher.unregisterCallback(changes) }
            catch (error: Exception) { Log.w("Grove", "Could not unregister launcher callback", error) }
            startupController.launcherCallbackRegistered = false
        }
        touchRouter.cancel()
        searchController.cancelPending()
        searchController.searchWorker.shutdownNow()
        searchController.shutdown()
        searchController.sources.shutdown()
        worker.shutdownNow()
        if (::surface.isInitialized) surface.background = null
        homeController.artwork?.recycle()
        homeController.artwork = null
        homeController.backdrop = null
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        homeController.rememberHomeScroll()
        outState.putInt("pending", widgets.pending)
        outState.putInt("homeScrollY", homeController.homeScrollY)
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent)
        if (drawer || searchMode) homeController.showHome()
        if (intent.action == Intent.ACTION_APPLICATION_PREFERENCES) root.post { setupController.settings() }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (setupController.firstRunSetup != null) return super.dispatchTouchEvent(event)
        if (pinDragController.busy) {
            touchRouter.cancel()
            return super.dispatchTouchEvent(event)
        }
        return touchRouter.dispatch(event) { super.dispatchTouchEvent(it) }
    }


}
