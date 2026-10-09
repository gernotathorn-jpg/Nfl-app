package com.nflapp.util

import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {
    private val kickoffFormat = DateTimeFormatter.ofPattern("EEE, dd.MM. · HH:mm", Locale.getDefault())
    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, dd. MMMM", Locale.getDefault())
    private val dateTimeFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())

    fun kickoff(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        kickoffFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun day(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dayFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun dateTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        dateTimeFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

    fun percent(p: Double): String = "${(p * 100).roundToInt()} %"

    /** Spread from the home team's view, e.g. -3.5 -> "KC -3.5"; 0 -> "Pick'em". */
    fun spread(spread: Double, home: String, away: String): String = when {
        spread == 0.0 -> "Pick'em"
        spread < 0 -> "$home -${trim(abs(spread))}"
        else -> "$away -${trim(spread)}"
    }

    fun signed(value: Double, decimals: Int = 1): String {
        val s = "%.${decimals}f".format(Locale.getDefault(), value)
        return if (value > 0) "+$s" else s
    }

    fun number(value: Double, decimals: Int = 1): String = "%.${decimals}f".format(Locale.getDefault(), value)

    private fun trim(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else x.toString()

    fun teamColor(hex: String?, fallback: Color = Color.Gray): Color = try {
        if (hex == null) fallback else Color(android.graphics.Color.parseColor(hex))
    } catch (e: IllegalArgumentException) {
        fallback
    }
}
