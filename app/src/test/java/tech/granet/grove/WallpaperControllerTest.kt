package tech.granet.grove

import org.junit.Assert.assertThrows
import org.junit.Test

class WallpaperControllerTest {
    @Test fun customImageValidationFailsBeforeDecodeForBadInputs() {
        WallpaperController.validateCustomImage("image/jpeg", 1024, 1080, 2400)
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperController.validateCustomImage("text/plain", 1024, 1080, 2400)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperController.validateCustomImage("image/jpeg", 21L * 1024 * 1024, 1080, 2400)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperController.validateCustomImage("image/jpeg", 1024, 9000, 2400)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WallpaperController.validateCustomImage("image/jpeg", 0, 1080, 2400)
        }
    }
}
