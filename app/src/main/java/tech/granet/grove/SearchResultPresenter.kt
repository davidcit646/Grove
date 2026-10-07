package tech.granet.grove

import android.net.Uri
import android.widget.LinearLayout
import tech.granet.grove.ui.message

/** Converts current search state into immutable SearchScreen rows and actions. */
internal class SearchResultPresenter(
    private val activity: MainActivity,
    private val access: SearchAccessController,
    private val sources: () -> SearchSources,
    private val live: SearchLiveQueries,
    private val settings: SearchSettingsCoordinator,
    private val refreshSettings: () -> Unit,
    private val refreshContacts: () -> Unit,
    private val indexFiles: () -> Unit,
    private val renderSearch: (String) -> Unit,
) {
    fun display(
        target: LinearLayout,
        query: String,
        matchingApps: List<App>,
        matchingContacts: List<ContactIndex.Contact>,
        matchingFiles: List<IndexedFile>,
    ) = with(activity) {
        val source = sources()
        searchController.searchScreen.render(
            target = target,
            query = query,
            calculation = if (query == settings.query && configController.config.search.calculator) {
                settings.calculation
            } else SearchCalculator.Result.NotCalculation,
            openCalculator = { settings.calculatorActions.open() },
            apps = matchingApps.map { app ->
                SearchScreen.AppRow(
                    app.key,
                    app.label,
                    catalogController.iconCache[app.key],
                    open = {
                        runCatching {
                            launcher.startMainActivity(app.component, android.os.Process.myUserHandle(), null, null)
                        }.onFailure {
                            message("This app is unavailable")
                            catalogController.loadApps()
                        }
                    },
                    menu = { actionController.appMenu(app) },
                )
            },
            appState = catalogController.state,
            retryApps = { catalogController.loadApps() },
            settingsUnavailable = configController.config.search.androidSettings && settings.androidUnavailable,
            retrySettings = refreshSettings,
            groveSettings = (
                if (query == settings.query && configController.config.search.groveSettings) settings.matches.grove
                else emptyList()
            ).map { SettingsSearchPresentation.row(this, settings.router, it) },
            androidSettings = (
                if (query == settings.query && configController.config.search.androidSettings) settings.matches.android
                else emptyList()
            ).map { SettingsSearchPresentation.row(this, settings.router, it) },
            contacts = (
                if (configController.config.search.contacts && access.hasContacts()) {
                    if (configController.config.search.contactIndexing &&
                        source.contactCacheReady && !source.contactLoadFailed
                    ) matchingContacts else live.contacts
                } else emptyList()
            ).map { contact ->
                SearchScreen.ContactRow(contact.id, contact.name) { actionController.contactMenu(contact) }
            },
            files = (
                if (configController.config.search.files && access.hasFiles()) {
                    if (configController.config.search.fileIndexing &&
                        source.fileCacheReady && !source.fileLoadFailed
                    ) matchingFiles else live.files
                } else emptyList()
            ).map { file ->
                SearchScreen.FileRow(
                    file,
                    open = { actionController.openFile(file) },
                    menu = { actionController.searchItemMenu(file) },
                )
            },
            contactState = (
                if (configController.config.search.contacts && access.hasContacts()) live.contactState else null
            ) ?: SearchSourceState.resolve(
                configController.config.search.contacts,
                access.hasContacts(),
                searchController.indexingContacts && !source.contactCacheReady,
                searchController.contactLoadFailed,
                searchController.contacts.size,
                source.contactScanSkipped,
            ),
            requestContactAccess = access::explainContacts,
            retryContacts = {
                if (configController.config.search.contactIndexing) refreshContacts() else renderSearch(query)
            },
            fileState = (
                if (configController.config.search.files && access.hasFiles()) live.fileState else null
            ) ?: SearchSourceState.resolve(
                configController.config.search.files,
                access.hasFiles(),
                searchController.indexingFiles && !source.fileCacheReady,
                searchController.fileLoadFailed,
                searchController.files.size,
                searchController.fileScanSkipped,
            ),
            requestFileAccess = access::explainFiles,
            retryFiles = {
                if (configController.config.search.fileIndexing) indexFiles() else renderSearch(query)
            },
            searchGoogle = {
                actionController.openWeb("https://www.google.com/search?q=${Uri.encode(query.trim())}")
            },
            googleMenu = { actionController.webResultMenu(query.trim(), "Google") },
            searchStore = { actionController.openPlayStore(query.trim()) },
            storeMenu = { actionController.playStoreMenu(query.trim()) },
        )
    }
}
