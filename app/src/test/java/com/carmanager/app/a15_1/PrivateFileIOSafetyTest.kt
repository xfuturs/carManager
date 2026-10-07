package com.carmanager.app.a15_1

import com.carmanager.app.core.util.PrivateFileIO
import java.io.*
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PrivateFileIOSafetyTest {
    @TempDir lateinit var directory: File
    private class Source(private val fail: Boolean = false) : ByteArrayInputStream("original".toByteArray()) {
        var closed = false
        var reads = 0
        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
            if (fail && reads++ > 0) throw IOException("provider")
            return super.read(buffer, offset, count)
        }
        override fun close() { closed = true; super.close() }
    }
    @Test fun `successful buffered copy closes input and indexes exact complete bytes`() {
        val source = Source()
        val result = PrivateFileIO.copyNew(directory, "document.bin", { source })
        assertEquals(File(directory,"document.bin").canonicalPath, result)
        assertEquals("original", File(result).readText()); assertTrue(source.closed)
    }
    @Test fun `provider failure closes input and removes partial destination`() {
        val source = Source(true)
        assertThrows(IOException::class.java) { PrivateFileIO.copyNew(directory,"partial",{ source }) }
        assertTrue(source.closed); assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun `output closes on writer failure and partial output is removed`() {
        lateinit var output: OutputStream
        assertThrows(IOException::class.java) { PrivateFileIO.create(directory,"partial.pdf") {
            output = it; it.write(12); throw IOException("write")
        } }
        assertThrows(IOException::class.java) { output.write(13) }
        assertFalse(File(directory,"partial.pdf").exists())
    }
    @Test fun `output closes after successful writer`() {
        lateinit var output: OutputStream
        PrivateFileIO.create(directory,"ready.pdf") { output = it; it.write(12) }
        assertThrows(IOException::class.java) { output.write(13) }
        assertEquals(1,File(directory,"ready.pdf").length())
    }
    @Test fun `cancellation propagates closes source and removes partial file`() {
        val source = Source(); var checks = 0
        assertThrows(CancellationException::class.java) {
            PrivateFileIO.copyNew(directory,"cancelled",{ source }) { if (++checks == 3) throw CancellationException() }
        }
        assertTrue(source.closed); assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun `empty stream is not a successful document`() {
        assertThrows(IllegalStateException::class.java) { PrivateFileIO.copyNew(directory,"empty",{ ByteArrayInputStream(byteArrayOf()) }) }
        assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun `null provider does not leave a created destination`() {
        assertThrows(IllegalStateException::class.java) { PrivateFileIO.copyNew(directory,"unavailable",{ null }) }
        assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun `provider open exception removes destination`() {
        assertThrows(IOException::class.java) { PrivateFileIO.copyNew(directory,"unavailable",{ throw IOException() }) }
        assertTrue(directory.listFiles()!!.isEmpty())
    }
    @Test fun `copy leaves original source bytes intact`() {
        val original = File(directory,"source").apply { writeText("private original") }
        val result = PrivateFileIO.copyNew(directory,"copy",{ original.inputStream() })
        assertEquals("private original",original.readText()); assertEquals(original.readText(),File(result).readText())
    }
    @Test fun `collision preserves earlier completed document`() {
        PrivateFileIO.create(directory,"same") { it.write(42) }
        assertThrows(IllegalStateException::class.java) { PrivateFileIO.create(directory,"same") { it.write(2) } }
        assertArrayEquals(byteArrayOf(42),File(directory,"same").readBytes())
    }
    @Test fun `canonical escape never writes outside private directory`() {
        assertThrows(IllegalStateException::class.java) { PrivateFileIO.create(directory,"../escape") { it.write(1) } }
        assertFalse(File(directory.parentFile,"escape").exists())
    }
    @Test fun `non directory target is rejected without changing bytes`() {
        val blocked = File(directory,"blocked").apply { writeText("keep") }
        assertThrows(IllegalStateException::class.java) { PrivateFileIO.create(blocked,"new") { it.write(1) } }
        assertEquals("keep",blocked.readText())
    }
}
