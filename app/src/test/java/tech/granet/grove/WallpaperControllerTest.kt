package tech.granet.grove

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class WallpaperControllerTest {
    @Test fun customCandidatePromotionPreservesCommittedUntilPromotion() {
        val dir = Files.createTempDirectory("grove-wallpaper").toFile()
        try {
            val committed = java.io.File(dir, "custom")
            val candidate = java.io.File(dir, "candidate")
            val backup = java.io.File(dir, "backup")
            committed.writeText("old")
            candidate.writeText("new")

            assertEquals("old", committed.readText())
            assertTrue(WallpaperController.promoteCandidate(committed, candidate, backup))
            assertEquals("new", committed.readText())
            assertFalse(candidate.exists())
            assertFalse(backup.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun noPendingCandidateKeepsExistingCommittedImage() {
        val dir = Files.createTempDirectory("grove-wallpaper").toFile()
        try {
            val committed = java.io.File(dir, "custom")
            committed.writeText("old")
            assertTrue(WallpaperController.promoteCandidate(
                committed, java.io.File(dir, "missing"), java.io.File(dir, "backup")))
            assertEquals("old", committed.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

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
