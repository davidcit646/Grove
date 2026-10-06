package tech.granet.grove

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.WorkManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import tech.granet.grove.ui.dp
import tech.granet.grove.ui.message

/** UI host only: navigation, lifecycle and Android result adapters. SettingsSession owns documents. */
class SettingsActivity : AppCompatActivity() {
    private val session: SettingsSession by viewModels()
    private val routes = mutableListOf("root")
    private lateinit var host: FrameLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var pages: SettingsPages
    private var renderedRevision = -1L
    private val motion = FirstRunMotion()
    private val importPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(session::readDocument) }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(session::export) }
    private val contacts = registerForActivityResult(ActivityResultContracts.RequestPermission()) { if (::pages.isInitialized) pages.refreshStatus() }
    private val homeRole = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { if (::pages.isInitialized) pages.refreshStatus() }
    override fun onCreate(state: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(state)
        window.setDecorFitsSystemWindows(false)
        val context = DynamicColors.wrapContextIfAvailable(this)
        val shell = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        toolbar = MaterialToolbar(context).apply {
            setNavigationIcon(R.drawable.ic_setup_back)
            setNavigationContentDescription("Back")
            setNavigationOnClickListener { back() }
        }
        host = FrameLayout(context)
        shell.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        shell.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.setOnApplyWindowInsetsListener { view, insets ->
            val edges = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            view.setPadding(edges.left, edges.top, edges.right, edges.bottom); insets
        }
        setContentView(shell)
        val available = try { session.repository.snapshot(); true } catch (_: Exception) { false }
        if (!available) {
            toolbar.title = "Settings unavailable"
            val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            panel.addView(com.google.android.material.button.MaterialButton(context).apply {
                text = "Retry"; setOnClickListener { recreate() }
            })
            panel.addView(com.google.android.material.button.MaterialButton(context).apply {
                text = "Android Home settings"; setOnClickListener { openLink(Intent(Settings.ACTION_HOME_SETTINGS)) }
            })
            host.addView(panel); return
        }
        state?.getStringArrayList("routes")?.filter { it in SettingsPages.routes }?.let { if (it.isNotEmpty()) { routes.clear(); routes.addAll(it) } }
        if (state == null) intent.getStringExtra("route")?.takeIf { it in SettingsPages.routes }?.let { routes.add(it) }
        if (session.draft == null) session.draft = state?.getString("draft") ?: intent.getStringExtra("draft")
        if (session.editorBase == null && state?.containsKey("draft") == true) {
            // Process death makes an old draft provisional again; review may not auto-apply it.
            session.editorBase = SettingsSnapshot(session.repository.snapshot().config, -1)
        }
        pages = SettingsPages(this, session, ::navigate, ::delegate, ::requestAccess,
            { importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            { export.launch("grove-config.json") }, ::chooseHome, { applyTheme(); render() })
        session.documentResult.observe(this) { result ->
            if (result != null) {
                session.documentResult.value = null
                result.message?.let(::message)
                if (session.candidate != null && routes.last() != "review") navigate("review")
                else if (routes.last() == "editor") render()
            }
        }
        for (kind in listOf("contacts", "files")) try {
            WorkManager.getInstance(this).getWorkInfosForUniqueWorkLiveData(IndexWork.name(kind)).observe(this) {
                session.commands.observeIndex(kind, it)
                pages.refreshStatus()
            }
        } catch (_: Exception) { message("Background index status unavailable") }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = back()
        })
        (application as GroveApp).settingsChanges.observe(this) {
            if (::pages.isInitialized && it.revision != renderedRevision) { applyTheme(); render() }
        }
        applyTheme()
        render()
        if (state == null) intent.getStringExtra("importUri")?.let { session.readDocument(Uri.parse(it)) }
    }
    override fun onResume() {
        super.onResume()
        if (::pages.isInitialized) {
            if (session.repository.snapshot().revision != renderedRevision) { applyTheme(); render() }
            pages.refreshStatus()
        }
    }
    override fun onStop() { motion.finish(); super.onStop() }
    override fun onDestroy() { motion.finish(); super.onDestroy() }
    override fun onSaveInstanceState(state: Bundle) {
        state.putStringArrayList("routes", ArrayList(routes)); state.putString("draft", session.draft)
        super.onSaveInstanceState(state)
    }
    private fun applyTheme() {
        val mode = when (session.repository.snapshot().config.themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (delegate.localNightMode != mode) delegate.localNightMode = mode
    }
    private fun navigate(route: String) {
        if (motion.busy || route !in SettingsPages.routes) return
        routes.add(route); render(true)
    }
    private fun back() {
        motion.finish()
        session.cancelDocument()
        if (routes.last() in listOf("homeGrid", "drawerGrid")) { session.gridColumns = null; session.gridRows = null }
        if (routes.size > 1) { routes.removeAt(routes.lastIndex); render(false) } else finish()
    }
    private fun render(forward: Boolean? = null) {
        motion.finish()
        renderedRevision = session.repository.snapshot().revision
        val route = routes.last()
        toolbar.title = SettingsPages.title(route)
        val content = pages.render(route)
        val view = ScrollView(content.context).apply { isFillViewport = true; addView(content) }
        val outgoing = host.getChildAt(0)
        host.addView(view, FrameLayout.LayoutParams(-1, -1))
        if (forward == null) { outgoing?.let(host::removeView) }
        else motion.slide(host, outgoing, view, forward) { view.requestFocus() }
    }
    private fun delegate(action: String) {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("settingsAction", action))
            finish()
        } catch (_: Exception) { message("This launcher action is unavailable. Return Home and try again.") }
    }
    private fun requestAccess(kind: String) {
        try {
            if (kind == "contacts") contacts.launch(Manifest.permission.READ_CONTACTS)
            else startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
        } catch (_: Exception) { message("Open Android app settings to allow access") }
    }
    private fun chooseHome() {
        try {
            val role = getSystemService(RoleManager::class.java)
            if (role.isRoleAvailable(RoleManager.ROLE_HOME)) homeRole.launch(role.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else openLink(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) { message("Android Home settings unavailable") }
    }
    internal fun openLink(intent: Intent) {
        try { startActivity(intent) } catch (_: Exception) { message("No app is available to open this destination") }
    }
    internal fun permitted(kind: String): Boolean = if (kind == "contacts")
        checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED else Environment.isExternalStorageManager()
}
