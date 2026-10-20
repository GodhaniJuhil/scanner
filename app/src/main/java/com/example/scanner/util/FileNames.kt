package com.example.scanner.util

/** Makes a document title safe to use as a file name; never returns an empty string. */
fun String.toSafeFileName(): String =
    replace(Regex("[^A-Za-z0-9 _.-]"), "_").trim().ifEmpty { "document" }
