package tech.granet.grove

import android.app.Application

/** Installs crash reporting before anything else runs. */
class GroveApp : Application() {
    internal val settingsChanges = androidx.lifecycle.MutableLiveData<SettingsSnapshot>()
    internal val settingsStore by lazy { ConfigStore(getSharedPreferences("grove", MODE_PRIVATE)) }
    internal val settingsRepository by lazy { SettingsRepository(settingsStore::load, settingsStore::save, settingsStore::activate, published = { settingsChanges.postValue(it) }) }
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
