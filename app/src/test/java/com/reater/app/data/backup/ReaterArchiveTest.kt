package com.reater.app.data.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReaterArchiveTest {
    private fun inTemp(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("reater-test").toFile()
        try { block(directory) } finally { directory.deleteRecursively() }
    }

    @Test fun binaryMediaAndAvatarCropSurviveRoundTrip() = inTemp { directory ->
        val movie = File(directory, "video").apply { writeBytes(ByteArray(150_000) { (it % 251).toByte() }) }
        val crop = File(directory, "crop").apply { writeText("1.25,0.2,0.3") }
        val destination = ByteArrayOutputStream()
        val manifest = """{"fileFormat":"reater","version":"v2"}""".toByteArray()
        ReaterArchive.write(destination, manifest, mapOf("media/0.mp4" to movie,
            "avatars/custom_avatar_crop_123.txt" to crop))
        assertEquals(0x50, destination.toByteArray()[0].toInt())
        val unpacked = ReaterArchive.read(ByteArrayInputStream(destination.toByteArray()), File(directory, "unpack").apply { mkdirs() })
        assertArrayEquals(manifest, unpacked.getValue(ReaterArchive.MANIFEST).readBytes())
        assertArrayEquals(movie.readBytes(), unpacked.getValue("media/0.mp4").readBytes())
        assertEquals(ReaterArchive.sha256(movie), ReaterArchive.sha256(unpacked.getValue("media/0.mp4")))
        assertEquals(crop.readText(), unpacked.getValue("avatars/custom_avatar_crop_123.txt").readText())
    }

    @Test fun traversalAbsoluteAndUnknownEntriesAreRejected() = inTemp { directory ->
        listOf("../escape", "media/../../escape", "/media/0.mp4", "C:\\escape", "avatars/..", "unexpected.txt").forEach { name ->
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry(name)); it.write(42); it.closeEntry() }
            assertThrows(IllegalArgumentException::class.java) {
                ReaterArchive.read(ByteArrayInputStream(bytes.toByteArray()), directory)
            }
        }
        assertFalse(File(directory.parentFile, "escape").exists())
    }

    @Test fun missingManifestAndTruncatedArchiveFail() = inTemp { directory ->
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { it.putNextEntry(ZipEntry("media/0.mp4")); it.write(ByteArray(1000)); it.closeEntry() }
        assertThrows(IllegalArgumentException::class.java) { ReaterArchive.read(bytes.toByteArray().inputStream(), directory) }
        assertThrows(Exception::class.java) { ReaterArchive.read(bytes.toByteArray().copyOf(45).inputStream(), directory) }
    }

    @Test fun streamingLimitsPreventOversizedPayload() {
        val output = ByteArrayOutputStream()
        assertThrows(IllegalArgumentException::class.java) {
            ReaterArchive.copyBounded(ByteArray(100).inputStream(), output, 99)
        }
        assertEquals(0, output.size())
    }

    @Test fun hashDetectsCorruption() = inTemp { directory ->
        val file = File(directory, "asset").apply { writeText("abc") }
        val hash = ReaterArchive.sha256(file)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash)
        file.writeText("abd")
        assertNotEquals(hash, ReaterArchive.sha256(file))
    }
}
