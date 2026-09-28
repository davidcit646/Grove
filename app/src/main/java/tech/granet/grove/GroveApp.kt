package tech.granet.grove

import android.app.Application

/** Installs crash reporting before anything else runs. */
class GroveApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
