package tech.granet.grove

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import tech.granet.grove.ui.message

/** Android owns availability. Only exported, enabled handlers from the system Settings package qualify. */
internal class AndroidSettingsRouter(private val activity: Activity) {
    private fun settingsPackages(): Set<String> {
        val pm = activity.packageManager
        return pm.queryIntentActivities(Intent("android.settings.SETTINGS"), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo }.filter { SettingsCapabilities.trusted(handler(it)) }.map { it.packageName }.toSet()
    }
    private fun resolve(action: String, packages: Set<String> = settingsPackages()): ComponentName? {
        val pm = activity.packageManager
        return pm.queryIntentActivities(Intent(action), PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo }.filter { SettingsCapabilities.allowed(handler(it), packages) }
            .sortedWith(compareBy({ it.packageName }, { it.name })).firstOrNull()
            ?.let { ComponentName(it.packageName, it.name) }
    }
    private fun handler(info: android.content.pm.ActivityInfo) = SettingsHandler(info.packageName,
        info.enabled, info.applicationInfo.enabled, info.exported,
        info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
        info.permission == null || activity.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)

    fun snapshot(): List<SettingsEntry> {
        val packages = try { settingsPackages() } catch (_: Exception) { emptySet() }
        return SettingsCapabilities.available(SettingsCatalogue.android) { entry ->
            resolve((entry.destination as SettingsDestination.Android).action, packages) != null
        }
    }
    fun open(entry: SettingsEntry) {
        val target = entry.destination as? SettingsDestination.Android ?: return
        if (SettingsCatalogue.android.none { it.id == entry.id && it.destination == target }) return
        when (SettingsCapabilities.launch({ resolve(target.action) }) { component ->
            activity.startActivity(Intent(target.action).setComponent(component))
        }) {
            SettingsCapabilities.Launch.OPENED -> Unit
            SettingsCapabilities.Launch.UNAVAILABLE -> activity.message("This Android settings page is unavailable")
            SettingsCapabilities.Launch.FAILED -> activity.message("Android settings could not open. Try again from Settings.")
        }
    }
}

