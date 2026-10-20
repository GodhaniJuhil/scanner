package com.example.scanner.util

/**
 * Returns a copy with the element at [index] moved by [delta] positions
 * (negative = towards the start). If either position is out of range the list is returned unchanged.
 */
fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().also { it.add(target, it.removeAt(index)) }
}
