package tech.granet.grove

import android.content.pm.LauncherApps
import android.os.UserHandle

/** Bridges Android package changes to the catalogue owner without storing package state here. */
internal class LauncherInventoryCallback(
    private val ownPackage: String,
    private val reload: () -> Unit,
) : LauncherApps.Callback() {
    override fun onPackageAdded(packageName: String, user: UserHandle) =
        LauncherPackageEvents.changed(packageName, ownPackage, reload)

    override fun onPackageRemoved(packageName: String, user: UserHandle) =
        LauncherPackageEvents.changed(packageName, ownPackage, reload)

    override fun onPackageChanged(packageName: String, user: UserHandle) =
        LauncherPackageEvents.changed(packageName, ownPackage, reload)

    override fun onPackagesAvailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) {
        if (packageNames.any { it != ownPackage }) reload()
    }

    override fun onPackagesUnavailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) {
        if (packageNames.any { it != ownPackage }) reload()
    }
}
