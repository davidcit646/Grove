package tech.granet.grove

import java.io.File

/** Compatibility adapter; each responsibility is owned by a focused component. */
internal object WallpaperArt {
    val commons get() = WallpaperCatalog.commons
    val sources get() = WallpaperCatalog.sources
    fun source(index: Int) = WallpaperCatalog.source(index)
    fun indexForId(id: String) = WallpaperCatalog.indexForId(id)
    fun customFile(filesDir: File) = WallpaperImageStore.customFile(filesDir)
    fun customCandidateFile(filesDir: File) = WallpaperImageStore.customCandidateFile(filesDir)
    fun customBackupFile(filesDir: File) = WallpaperImageStore.customBackupFile(filesDir)
    fun create(style: Int) = WallpaperRenderer.create(style)
}
