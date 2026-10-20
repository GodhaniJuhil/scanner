package com.example.scanner.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {

    @Test
    fun keepsLettersDigitsSpacesAndSafePunctuation() {
        assertEquals("Scan 2026-10-02 10-30", "Scan 2026-10-02 10-30".toSafeFileName())
        assertEquals("my_file.v2", "my_file.v2".toSafeFileName())
    }

    @Test
    fun replacesCharactersThatAreInvalidInFileNames() {
        assertEquals("a_b_c_d_e", "a/b:c*d?e".toSafeFileName())
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals("Invoice", "  Invoice  ".toSafeFileName())
    }

    @Test
    fun blankTitleFallsBackToDefaultName() {
        assertEquals("document", "".toSafeFileName())
        assertEquals("document", "   ".toSafeFileName())
    }

    @Test
    fun cannotEscapeTheDocumentFolder() {
        // Slashes are replaced, so a title can never contain a path separator.
        assertEquals(".._.._etc_passwd", "../../etc/passwd".toSafeFileName())
    }
}
