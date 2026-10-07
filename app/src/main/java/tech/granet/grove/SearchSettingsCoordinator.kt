package tech.granet.grove

import java.util.concurrent.ExecutorService

internal data class SearchSettingsWork(
    val calculation: SearchCalculator.Result,
    val grove: List<SettingsEntry>,
    val android: List<SettingsEntry>,
)

/** Owns searchable Grove/Android settings snapshots and calculator state for Search. */
internal class SearchSettingsCoordinator(
    private val activity: MainActivity,
    private val worker: ExecutorService,
    private val currentQuery: () -> String,
    private val render: (String) -> Unit,
) {
    val router by lazy { AndroidSettingsRouter(activity) }
    private val groveMatcher = SettingsMatcher(SettingsLabels.localize(activity, SettingsCatalogue.grove))
    private var androidSettings = emptyList<SettingsEntry>()
    private var androidMatcher = SettingsMatcher(emptyList())
    private var refreshGeneration = 0

    var androidUnavailable = false
        private set
    var matches = SettingsMatches(emptyList(), emptyList())
        private set
    var query = ""
        private set
    var calculation: SearchCalculator.Result = SearchCalculator.Result.NotCalculation
        private set

    val calculatorActions by lazy { CalculatorActions(activity) }

    fun refresh() {
        val generation = ++refreshGeneration
        if (!activity.configController.config.search.androidSettings || !activity.searchMode) {
            androidSettings = emptyList()
            androidMatcher = SettingsMatcher(emptyList())
            androidUnavailable = false
            return
        }
        if (worker.isShutdown) return
        worker.execute {
            if (generation != refreshGeneration || !activity.configController.config.search.androidSettings) return@execute
            val capabilities = router.snapshot()
            val snapshot = SettingsLabels.localize(activity, capabilities.entries)
            activity.runOnUiThread {
                if (generation != refreshGeneration || activity.isDestroyed || !activity.searchMode ||
                    !activity.configController.config.search.androidSettings
                ) return@runOnUiThread
                if (androidSettings != snapshot || androidUnavailable != capabilities.failed) {
                    androidUnavailable = capabilities.failed
                    androidSettings = snapshot
                    androidMatcher = SettingsMatcher(snapshot)
                    render(currentQuery())
                }
            }
        }
    }

    fun prepare(query: String, config: SearchSettings): SearchSettingsWork {
        val settingsSnapshot = androidSettings
        val matcherSnapshot = androidMatcher
        return SearchSettingsWork(
            calculation = if (config.calculator) SearchCalculator.calculate(query) else SearchCalculator.Result.NotCalculation,
            grove = if (config.groveSettings) groveMatcher.matching(query) else emptyList(),
            android = if (config.androidSettings) {
                SettingsSearchFallback.rows(matcherSnapshot.matching(query), settingsSnapshot)
            } else emptyList(),
        )
    }

    fun publish(query: String, work: SearchSettingsWork) {
        this.query = query
        calculation = work.calculation
        matches = SettingsMatches(work.grove, work.android)
    }
}
