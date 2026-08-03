package com.siren.mobile.util

/**
 * Date/time formatting delegated to each platform's native formatter
 * (SimpleDateFormat on Android, NSDateFormatter on iOS) so the app respects the
 * device's locale and 12/24-hour preference rather than hard-coding a pattern.
 */
expect object DateFmt {
    /** "9:41 AM" */
    fun clock(epochMillis: Long): String

    /** "9:41:23 AM" — used where the record needs second precision. */
    fun clockSeconds(epochMillis: Long): String

    /** "Aug 3, 2026" */
    fun date(epochMillis: Long): String

    /** "Aug 3, 2026 · 9:41 AM" */
    fun dateTime(epochMillis: Long): String

    /** "Aug 3 · 9:41 AM" — compact form for list rows. */
    fun shortDateTime(epochMillis: Long): String
}
