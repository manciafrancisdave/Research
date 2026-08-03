package com.siren.mobile.util

import androidx.compose.ui.text.TextStyle
import kotlin.math.abs
import kotlin.math.round

/**
 * Tabular (monospaced) figures. Applied to any number that updates in place —
 * magnitudes, roster counts, percentages — so digits don't shift width and make the
 * layout jitter while an event is live.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

/**
 * `String.format` is JVM-only, so shared code needs its own fixed-decimal formatter.
 * Used for the peak-acceleration readouts ("0.74 g"), which must never drift in
 * precision between platforms — the figures end up in the study's results.
 */
fun Double.toFixed(decimals: Int): String {
    if (isNaN()) return "—"
    if (isInfinite()) return if (this > 0) "∞" else "-∞"

    var factor = 1L
    repeat(decimals) { factor *= 10L }

    val scaled = round(abs(this) * factor).toLong()
    val whole = scaled / factor
    val frac = scaled % factor

    return buildString {
        if (this@toFixed < 0 && scaled != 0L) append('-')
        append(whole)
        if (decimals > 0) {
            append('.')
            append(frac.toString().padStart(decimals, '0'))
        }
    }
}

/** "0.74g" — the compact form used in badges and list rows. */
fun Double.asG(decimals: Int = 2): String = "${toFixed(decimals)}g"

/** "0.74 g" — the spaced form used in headline readouts. */
fun Double.asGSpaced(decimals: Int = 2): String = "${toFixed(decimals)} g"

/** Initials for avatars, shared by profiles, roster rows and contacts. */
fun String.initials(max: Int = 2): String =
    trim()
        .split(' ', '\t', '\n')
        .filter { it.isNotEmpty() }
        .take(max)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
