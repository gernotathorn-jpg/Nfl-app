package com.nflapp.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Background checks happen on Tuesdays and Thursdays. The data pipeline runs at
 * 06:00 UTC on those days, so a "slot" opens at 07:30 UTC.
 *
 * WorkManager cannot express weekdays directly. A periodic worker wakes every
 * few hours and only touches the network if a slot has passed since the last
 * successful check, which also catches up after the device was off.
 */
object SyncSchedule {
    const val WORK_NAME = "nfl-sync"
    val DAYS = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
    val SLOT_TIME_UTC: LocalTime = LocalTime.of(7, 30)
    private const val WAKE_INTERVAL_HOURS = 6L

    /** The most recent slot at or before [now]. */
    fun latestSlot(now: Instant): Instant {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        for (back in 0L..7L) {
            val day = today.minusDays(back)
            val slot = day.atTime(SLOT_TIME_UTC).toInstant(ZoneOffset.UTC)
            if (day.dayOfWeek in DAYS && !slot.isAfter(now)) return slot
        }
        error("unreachable: a Tuesday or Thursday occurs every week")
    }

    fun nextSlot(now: Instant): Instant {
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        for (ahead in 0L..7L) {
            val day = today.plusDays(ahead)
            val slot = day.atTime(SLOT_TIME_UTC).toInstant(ZoneOffset.UTC)
            if (day.dayOfWeek in DAYS && slot.isAfter(now)) return slot
        }
        error("unreachable")
    }

    fun isDue(lastCheck: Instant?, now: Instant): Boolean =
        lastCheck == null || lastCheck.isBefore(latestSlot(now))

    fun networkType(wifiOnly: Boolean) = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

    /** (Re-)registers the periodic worker; UPDATE keeps its timing but applies new constraints. */
    fun schedule(context: Context, wifiOnly: Boolean) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(WAKE_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(networkType(wifiOnly)).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
