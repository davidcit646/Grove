package tech.granet.grove

import java.net.URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperControllerTest {
    @Test fun acceptsWikimediaThumbnailRedirects() {
        assertTrue(WallpaperController.allowedWallpaperDestination(
            URL("https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ef/Red_Flower_red.jpg/1920px-Red_Flower_red.jpg")))
        assertTrue(WallpaperController.allowedWallpaperDestination(
            URL("https://upload.wikimedia.org/wikipedia/commons/7/75/Green_Landscape_with_Hills_and_Trees.jpg")))
    }

    @Test fun rejectsUntrustedWallpaperRedirects() {
        for (url in listOf(
            "http://thumb.wikimedia.org/wikipedia/commons/image.jpg",
            "https://thumb.wikimedia.org.evil.example/image.jpg",
            "https://evil.example/image.jpg",
            "https://thumb.wikimedia.org:8443/image.jpg",
            "https://user@thumb.wikimedia.org/image.jpg",
        )) assertFalse(url, WallpaperController.allowedWallpaperDestination(URL(url)))
    }
}
