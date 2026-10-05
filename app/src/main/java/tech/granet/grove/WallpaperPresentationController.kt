package tech.granet.grove

import android.app.WallpaperManager
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import java.util.*

/** WallpaperPresentationController owns its lane; Android lifecycle and results remain in MainActivity. */
internal class WallpaperPresentationController(private val activity: MainActivity) {
    fun wallpapers() {
        with(activity) {
            WallpaperPicker(this, wallpaperController, configController.config.wallpaper) { index, which ->
                wallpaperController.apply(index, which) { applied ->
                    if (applied && which and WallpaperManager.FLAG_SYSTEM != 0) {
                        if (configController.commitConfig(configController.config.copy(wallpaper = index))) {
                            homeController.pendingWallpaper = null
                            homeController.artworkStyle = -1
                        }
                        homeController.showHome()
                    }
                }
            }.show()
        }
    }
}
