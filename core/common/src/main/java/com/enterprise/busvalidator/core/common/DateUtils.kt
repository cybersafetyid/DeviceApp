package com.enterprise.busvalidator.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Standardized Date and Time formatting helpers.
 */
object DateUtils {
    
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val displayTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val displayDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun formatIsoUtc(timestampMs: Long): String {
        return isoFormat.format(Date(timestampMs))
    }

    fun formatDisplayTime(timestampMs: Long): String {
        return displayTimeFormat.format(Date(timestampMs))
    }

    fun formatDisplayDate(timestampMs: Long): String {
        return displayDateFormat.format(Date(timestampMs))
    }
}
