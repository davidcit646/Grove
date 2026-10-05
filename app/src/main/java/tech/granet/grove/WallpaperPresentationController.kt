package tech.granet.grove

import android.app.WallpaperManager
import android.net.Uri

/** Wallpaper picker and applied preference. System application succeeds before preference commit. */
internal class WallpaperPresentationController(private val activity: MainActivity) {
    fun wallpapers() {
        with(activity) {
            WallpaperPicker(
                this,
                wallpaperController,
                configController.config.wallpaper,
                selected = ::applySelection,
                chooseCustom = { chooseWallpaperImage.launch(arrayOf("image/*")) },
            ).show()
        }
    }

    fun importCustom(uri: Uri) {
        with(activity) {
            wallpaperController.importCustom(uri) { ok ->
                if (ok) {
                    WallpaperPicker(
                        this,
                        wallpaperController,
                        14,
                        selected = ::applySelection,
                        chooseCustom = { chooseWallpaperImage.launch(arrayOf("image/*")) },
                    ).show()
                } else {
                    GroveErrorPresenter.show(this, GroveErrorRegistry.WALLPAPER_PREVIEW) {
                        chooseWallpaperImage.launch(arrayOf("image/*"))
                    }
                }
            }
        }
    }

    private fun commitHomeSelection(index: Int): Boolean = with(activity) {
        if (!configController.commitConfig(configController.config.copy(wallpaper = index))) return@with false
        homeController.pendingWallpaper = null
        homeController.artworkStyle = -1
        true
    }

    private fun applySelection(index: Int, which: Int) {
        with(activity) {
            wallpaperController.apply(index, which) applyDone@{ applied ->
                if (!applied) {
                    GroveErrorPresenter.show(this, GroveErrorRegistry.WALLPAPER_APPLY) {
                        applySelection(index, which)
                    }
                    return@applyDone
                }
                if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                    if (commitHomeSelection(index)) homeController.showHome()
                    else GroveErrorPresenter.show(this, GroveErrorRegistry.WALLPAPER_SYNC) {
                        if (commitHomeSelection(index)) homeController.showHome()
                    }
                }
            }
        }
    }
}
