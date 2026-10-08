package com.carmanager.app.a15_1

import com.carmanager.app.core.util.StagedDocumentFile
import com.carmanager.app.core.domain.model.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class StagedDocumentFileTest {
    @TempDir lateinit var directory: File
    private fun doc(path: String) = Document(1,1,"Image",DocumentCategory.PHOTOS,path,0,"local:device")
    @Test fun `exact finished bytes are indexed after preparation and inside short commit`() = runTest {
        val order=mutableListOf<String>(); var inCommit=false
        val result=StagedDocumentFile.store({
            assertFalse(inCommit); order += "copy"
            File(directory,"finished").apply { writeText("complete") }.path
        }, { path ->
            inCommit=true
            try { assertEquals("complete",File(path).readText()); order += "insert"; doc(path) }
            finally { inCommit=false }
        }, { fail<Boolean>("no cleanup after commit") })
        assertEquals(listOf("copy","insert"),order); assertEquals("complete",File(result.filePath).readText())
    }
    @Test fun `failed metadata insert removes only the newly staged file`() = runTest {
        val source=File(directory,"source").apply { writeText("original") }; val staged=File(directory,"staged")
        try { StagedDocumentFile.store({ staged.writeText("copy"); staged.path }, { throw IOException("SQL") }, { File(it).delete() }); fail<Unit>("failure") }
        catch (_: IOException) {}
        assertFalse(staged.exists()); assertEquals("original",source.readText())
    }
    @Test fun `cancellation at commit propagates and cleans uncommitted bytes`() = runTest {
        val staged=File(directory,"staged").apply { writeText("copy") }
        try { StagedDocumentFile.store({ staged.path }, { throw CancellationException("cancel") }, { File(it).delete() }); fail<Unit>("cancel") }
        catch (_: CancellationException) {}
        assertFalse(staged.exists())
    }
    @Test fun `cancellation after preparation skips metadata and cleans staged bytes`() = runTest {
        val staged=File(directory,"staged"); var inserted=false
        val task=launch { StagedDocumentFile.store({ staged.writeText("copy"); currentCoroutineContext().cancel(); staged.path }, { inserted=true; doc(it) }, { File(it).delete() }) }
        task.join(); assertTrue(task.isCancelled); assertFalse(inserted); assertFalse(staged.exists())
    }
    @Test fun `caller cancellation during committed insert never deletes indexed file`() = runTest {
        val staged=File(directory,"staged").apply { writeText("complete") }; val entered=CompletableDeferred<Unit>(); val release=CompletableDeferred<Unit>()
        var indexed=false; var removed=false
        val task=launch { StagedDocumentFile.store({ staged.path }, { entered.complete(Unit); release.await(); indexed=true; doc(it) }, { removed=true; File(it).delete() }) }
        entered.await(); task.cancel(); release.complete(Unit); task.join()
        assertTrue(task.isCancelled); assertTrue(indexed); assertFalse(removed); assertEquals("complete",staged.readText())
    }
    @Test fun `cleanup failure attempts compensation and preserves primary insertion error`() = runTest {
        val original=IOException("insert")
        var attempted=false
        try { StagedDocumentFile.store({ File(directory,"staged").apply { writeText("copy") }.path }, { throw original }, { attempted=true; false }); fail<Unit>("failure") }
        catch (error: IOException) {
            // La récupération de stack coroutines peut envelopper une copie de l'IOException.
            val chain=generateSequence<Throwable>(error) { it.cause }.toList()
            assertTrue(chain.any { it === original }); assertEquals("insert",error.message)
            assertTrue(attempted)
        }
    }
    @Test fun `preparation failure never attempts metadata or removes unknown source`() = runTest {
        try { StagedDocumentFile.store({ throw IOException("provider") }, { fail<Document>("no insert") }, { fail<Boolean>("no arbitrary cleanup") }); fail<Unit>("failure") }
        catch (_: IOException) {}
    }
}
