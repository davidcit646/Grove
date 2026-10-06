package tech.granet.grove

/** Package/component events own app availability only, never contact-data refresh. */
internal object LauncherPackageEvents {
    fun changed(packageName: String, ownPackage: String, refreshCatalogue: (String) -> Unit) {
        if (packageName != ownPackage) refreshCatalogue(packageName)
    }
}
