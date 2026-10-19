package com.oloomyar.app.model

import org.junit.Assert.*
import org.junit.Test

class RasterSourceTest {
    @Test fun sourcePixelBoundsAreValidatedAndRetainTheirGeometry() {
        val r=checkNotNull(RasterSource.parse("crop:g7_ch03_q15_question.webp#1400,310,1735,422"))
        assertEquals(335,r.width);assertEquals(112,r.height)
        assertEquals(335f/112,r.aspectRatio,.0001f)
        listOf("crop:../secret.webp#0,0,1,1","crop:x.webp#2,0,1,1","crop:x.webp#-1,0,1,1","crop:x.webp#0,0,1","x.webp").forEach { assertNull(it,RasterSource.parse(it)) }
    }
}
