package com.carmanager.app.a15_1

import com.carmanager.app.core.util.ImageDecodePlan
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ImageDecodePlanTest {
    @Test fun `6000 by 4000 uses bounded downsample and preserves aspect ratio`() {
        val plan = ImageDecodePlan.from(6000,4000)
        assertEquals(4,plan.sampleSize); assertEquals(1500,plan.width); assertEquals(1000,plan.height)
        assertEquals(1.5,plan.width.toDouble()/plan.height)
    }
    @Test fun `small image is never upscaled`() {
        assertEquals(ImageDecodePlan(1,640,480),ImageDecodePlan.from(640,480))
    }
    @Test fun `portrait dimensions also remain bounded`() {
        val plan = ImageDecodePlan.from(4000,6000)
        assertEquals(1000,plan.width); assertEquals(1500,plan.height)
    }
    @Test fun `odd dimensions round safely within decode and page bound`() {
        val plan = ImageDecodePlan.from(4097,2731)
        assertEquals(4,plan.sampleSize); assertTrue(maxOf(plan.width,plan.height)<=2048)
    }
    @Test fun `invalid or unreadable bounds reject before decoding`() {
        for ((width,height) in listOf(0 to 10,10 to 0,-1 to 10,10 to -1)) {
            assertThrows(IllegalArgumentException::class.java) { ImageDecodePlan.from(width,height) }
        }
    }
    @Test fun `extreme metadata cannot overflow into raw dimension allocation`() {
        val plan = ImageDecodePlan.from(Int.MAX_VALUE,Int.MAX_VALUE)
        assertTrue(plan.sampleSize>1); assertTrue(plan.width<=2048); assertTrue(plan.height<=2048)
    }
}
