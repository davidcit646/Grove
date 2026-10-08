package tech.granet.grove

import java.io.File

internal object WallpaperImageStore {
    fun customFile(filesDir: File) = File(filesDir, "wallpapers/custom-image")
    fun customCandidateFile(filesDir: File) = File(filesDir, "wallpapers/custom-image.pending")
    fun customBackupFile(filesDir: File) = File(filesDir, "wallpapers/custom-image.backup")

}
