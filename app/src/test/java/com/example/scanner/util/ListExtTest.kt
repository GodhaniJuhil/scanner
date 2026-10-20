package com.example.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ListExtTest {

    private val pages = listOf("a", "b", "c", "d")

    @Test
    fun movesElementTowardsTheEnd() {
        assertEquals(listOf("a", "c", "b", "d"), pages.moved(1, +1))
    }

    @Test
    fun movesElementTowardsTheStart() {
        assertEquals(listOf("a", "c", "b", "d"), pages.moved(2, -1))
    }

    @Test
    fun movesByMoreThanOnePosition() {
        assertEquals(listOf("b", "c", "d", "a"), pages.moved(0, +3))
    }

    @Test
    fun movingFirstElementUpIsIgnored() {
        assertSame(pages, pages.moved(0, -1))
    }

    @Test
    fun movingLastElementDownIsIgnored() {
        assertSame(pages, pages.moved(3, +1))
    }

    @Test
    fun invalidIndexIsIgnored() {
        assertSame(pages, pages.moved(9, -1))
        assertSame(pages, pages.moved(-1, +1))
    }

    @Test
    fun doesNotModifyTheOriginalList() {
        pages.moved(0, +1)
        assertEquals(listOf("a", "b", "c", "d"), pages)
    }
}
