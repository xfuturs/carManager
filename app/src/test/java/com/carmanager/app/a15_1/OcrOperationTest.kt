package com.carmanager.app.a15_1

import com.carmanager.app.core.util.OcrHelper
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OcrOperationTest {
    private var closed=0
    private fun recognizer(read: suspend () -> String, close: () -> Unit = {}) = object : OcrHelper.Recognizer {
        override suspend fun text() = read()
        override fun close() { closed++; close() }
    }
    @Test fun `parsed proposals and successful no value are distinct and both close client`() = runTest {
        val values=OcrHelper.analyze { recognizer({ "42,50 EUR 20,00 L" }) }
        assertEquals(OcrHelper.Analysis.Values(OcrHelper.OcrResult(42.5,20.0)),values)
        assertEquals(OcrHelper.Analysis.NoValues,OcrHelper.analyze { recognizer({ "illisible" }) }); assertEquals(2,closed)
    }
    @Test fun `recognition failure returns truthful failure and closes client`() = runTest {
        assertEquals(OcrHelper.Analysis.Failed,OcrHelper.analyze { recognizer({ throw IOException("ML Kit") }) }); assertEquals(1,closed)
    }
    @Test fun `factory provider failure is analysis failure before client ownership`() = runTest {
        assertEquals(OcrHelper.Analysis.Failed,OcrHelper.analyze { throw IOException("provider") }); assertEquals(0,closed)
    }
    @Test fun `recognizer cancellation propagates unchanged and closes client`() = runTest {
        val cancellation=CancellationException("cancel")
        try { OcrHelper.analyze { recognizer({ throw cancellation }) }; fail<Unit>("cancel") }
        catch (error: CancellationException) { assertSame(cancellation,error) }
        assertEquals(1,closed)
    }
    @Test fun `factory cancellation is not an analysis failure`() = runTest {
        try { OcrHelper.analyze { throw CancellationException() }; fail<Unit>("cancel") } catch (_: CancellationException) {}
        assertEquals(0,closed)
    }
    @Test fun `close exception cannot hide cancellation`() = runTest {
        val cancellation=CancellationException()
        try { OcrHelper.analyze { recognizer({ throw cancellation }, { throw IOException("close") }) }; fail<Unit>("cancel") }
        catch (error: CancellationException) { assertSame(cancellation,error); assertEquals(1,error.suppressed.size) }
        assertEquals(1,closed)
    }
    @Test fun `fatal error is rethrown after closing instead of pretending empty analysis`() = runTest {
        val fatal=AssertionError("fatal")
        try { OcrHelper.analyze { recognizer({ throw fatal }) }; fail<Unit>("fatal") } catch (error: AssertionError) { assertSame(fatal,error) }
        assertEquals(1,closed)
    }
}
