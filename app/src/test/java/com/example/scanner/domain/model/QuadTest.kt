package com.example.scanner.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuadTest {

    @Test
    fun fullQuadIsConvex() {
        assertTrue(Quad.FULL.isConvex())
    }

    @Test
    fun skewedQuadIsConvex() {
        val quad = Quad(NormPoint(0.1f, 0.2f), NormPoint(0.9f, 0.1f), NormPoint(0.8f, 0.9f), NormPoint(0.2f, 0.8f))
        assertTrue(quad.isConvex())
    }

    @Test
    fun cornerDraggedPastOppositeSideIsNotConvex() {
        // Bottom-right pulled up and left of the top-left corner folds the shape over itself.
        val folded = Quad.FULL.withPoint(2, NormPoint(0.2f, 0.2f)).withPoint(0, NormPoint(0.5f, 0.5f))
        assertFalse(folded.isConvex())
    }

    @Test
    fun collapsedQuadIsNotConvex() {
        val line = Quad(NormPoint(0f, 0f), NormPoint(0.5f, 0.5f), NormPoint(1f, 1f), NormPoint(0.2f, 0.2f))
        assertFalse(line.isConvex())
    }

    @Test
    fun withPointReplacesOnlyThatCorner() {
        val moved = Quad.FULL.withPoint(1, NormPoint(0.7f, 0.1f))
        assertEquals(NormPoint(0.7f, 0.1f), moved.topRight)
        assertEquals(Quad.FULL.topLeft, moved.topLeft)
        assertEquals(Quad.FULL.bottomRight, moved.bottomRight)
        assertEquals(Quad.FULL.bottomLeft, moved.bottomLeft)
    }
}
