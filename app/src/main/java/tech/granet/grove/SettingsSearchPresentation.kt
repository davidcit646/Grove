package tech.granet.grove

import android.content.Intent
import tech.granet.grove.ui.message

/** Converts immutable discovery rows to UI actions; feature owners retain all setting effects. */
internal object SettingsSearchPresentation {
    fun icon(icon: SettingsIcon): Int = when (icon) {
        SettingsIcon.HOME -> R.drawable.ic_setup_home
        SettingsIcon.GRID -> R.drawable.ic_setup_apps
        SettingsIcon.FOLDER -> R.drawable.ic_setup_folder
        SettingsIcon.WIDGET -> R.drawable.ic_settings_widgets
        SettingsIcon.PALETTE -> R.drawable.ic_settings_palette
        SettingsIcon.IMAGE -> R.drawable.ic_settings_image
        SettingsIcon.DOCUMENT -> R.drawable.ic_settings_description
        SettingsIcon.HELP -> R.drawable.ic_settings_help
        SettingsIcon.MAIL -> R.drawable.ic_settings_mail
        SettingsIcon.INFO -> R.drawable.ic_settings_info
        SettingsIcon.SHIELD -> R.drawable.ic_settings_shield
        SettingsIcon.WIFI -> R.drawable.ic_settings_wifi
        SettingsIcon.BLUETOOTH -> R.drawable.ic_settings_bluetooth
        SettingsIcon.AIRPLANE -> R.drawable.ic_settings_airplane
        SettingsIcon.SOUND -> R.drawable.ic_settings_sound
        SettingsIcon.BATTERY -> R.drawable.ic_settings_battery
        SettingsIcon.LOCATION -> R.drawable.ic_settings_location
        SettingsIcon.SETTINGS -> R.drawable.ic_setup_settings
    }
    fun row(activity: MainActivity, router: AndroidSettingsRouter, entry: SettingsEntry) =
        SearchScreen.SettingsRow(entry.id, entry.title, entry.breadcrumb, icon(entry.icon)) {
            when (val target = entry.destination) {
                is SettingsDestination.Android -> router.open(entry)
                is SettingsDestination.Grove -> {
                    if (SettingsCatalogue.destination(target.route, target.anchor) == null) return@SettingsRow
                    try {
                        activity.startActivity(Intent(activity, SettingsActivity::class.java)
                            .putExtra("route", target.route).putExtra("anchor", target.anchor))
                    } catch (_: Exception) { activity.message("Grove settings are unavailable. Return Home and try again.") }
                }
            }
        }
}
