package com.carmanager.app.core.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.*

class ReportFileIOTest {
    @TempDir lateinit var directory: File
    private val bytes = ByteArray(100_000) { (it % 251).toByte() }
    private fun source() = File(directory, "original.pdf").apply { writeBytes(bytes) }

    @Test fun `copy streams all bytes and closes destination`() {
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        assertTrue(ReportFileIO.copy(source(), { output }))
        assertArrayEquals(bytes, output.toByteArray()); assertTrue(closed)
    }
    @Test fun `cancel needs neither source nor output`() {
        assertFalse(ReportFileIO.copy(File(directory, "absent"), null) { error("must not run") })
    }
    @Test fun `provider failure preserves original`() {
        val source = source()
        assertThrows(IOException::class.java) { ReportFileIO.copy(source, { object : OutputStream() {
            override fun write(b: Int) { throw IOException("provider failed") }
        } }) }
        assertArrayEquals(bytes, source.readBytes())
    }
    @Test fun `unavailable provider preserves source`() {
        val source = source()
        assertThrows(IllegalStateException::class.java) { ReportFileIO.copy(source, { null }) }
        assertArrayEquals(bytes, source.readBytes())
    }
    @Test fun `missing file never opens destination`() {
        assertThrows(IllegalStateException::class.java) { ReportFileIO.copy(File(directory, "absent"), { error("opened") }) }
    }
    @Test fun `workspace guard aborts midstream and leaves source intact`() {
        var checks = 0
        val source = source(); val output = ByteArrayOutputStream()
        assertThrows(IllegalStateException::class.java) { ReportFileIO.copy(source, { output }) {
            check(++checks < 4)
        } }
        assertTrue(output.size() in 1 until bytes.size); assertArrayEquals(bytes, source.readBytes())
    }
    @Test fun `partial rendering file is removed`() {
        assertThrows(IOException::class.java) { ReportFileIO.create(directory, "broken.pdf") { it.write(bytes); throw IOException() } }
        assertFalse(File(directory, "broken.pdf").exists())
    }
    @Test fun `empty render is rejected and cleaned`() {
        assertThrows(IllegalStateException::class.java) { ReportFileIO.create(directory, "empty.pdf") {} }
        assertFalse(File(directory, "empty.pdf").exists())
    }
    @Test fun `collision cannot overwrite previous report`() {
        ReportFileIO.create(directory, "same.pdf") { it.write(bytes) }
        assertThrows(IllegalStateException::class.java) { ReportFileIO.create(directory, "same.pdf") { it.write(1) } }
        assertArrayEquals(bytes, File(directory, "same.pdf").readBytes())
    }
    @Test fun `path traversal cannot create outside storage`() {
        assertThrows(IllegalStateException::class.java) { ReportFileIO.create(directory, "../escape.pdf") { it.write(1) } }
        assertFalse(File(directory.parentFile, "escape.pdf").exists())
    }
    @Test fun `generated names are safe bounded and distinct`() {
        val a = ReportFileIO.filename("é/\\:*?\"<>|".repeat(100), 0)
        val b = ReportFileIO.filename("é/\\:*?\"<>|".repeat(100), 0)
        assertNotEquals(a, b); assertTrue(a.matches(Regex("[A-Za-z0-9_.-]+")))
        assertTrue(a.length < 180); assertTrue(a.endsWith(".pdf"))
        assertEquals("application/pdf", ReportFileIO.MIME)
    }
}
