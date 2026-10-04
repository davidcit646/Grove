package tech.granet.grove

import java.nio.file.AccessDeniedException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

class FileIndexTest {
    @Test fun unreadableChildIsReportedAsPartial() {
        val root = Files.createTempDirectory("grove-index")
        val child = Files.createDirectory(root.resolve("blocked"))
        try {
            val result = FileIndex.scan(root.toFile(), openDirectory = { path ->
                if (path == child) throw AccessDeniedException(path.toString())
                Files.newDirectoryStream(path)
            })
            assertEquals(1, result.skippedDirectories)
            assertEquals(emptyList<IndexedFile>(), result.files)
        } finally {
            Files.deleteIfExists(child)
            Files.deleteIfExists(root)
        }
    }

    @Test(expected = AccessDeniedException::class)
    fun unreadableRootFailsTheScan() {
        val root = Files.createTempDirectory("grove-index")
        try {
            FileIndex.scan(root.toFile(), openDirectory = { throw AccessDeniedException(it.toString()) })
        } finally {
            Files.deleteIfExists(root)
        }
    }

    @Test fun cancellationHasNoResults() {
        val root = Files.createTempDirectory("grove-index")
        try {
            val result = FileIndex.scan(root.toFile(), shouldContinue = { false })
            assertEquals(emptyList<IndexedFile>(), result.files)
        } finally {
            Files.deleteIfExists(root)
        }
    }
}
