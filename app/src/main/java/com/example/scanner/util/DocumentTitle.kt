package com.example.scanner.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Default name for a new scan, e.g. "Scan 2026-10-02 10-30" (no characters that are bad in file names). */
fun defaultDocumentTitle(): String =
    "Scan " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.getDefault()).format(Date())
