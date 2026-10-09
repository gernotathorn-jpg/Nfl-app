package com.nflapp

import com.nflapp.work.SyncSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SyncScheduleTest {
    private fun t(s: String) = Instant.parse(s)

    // 2026-10-06 is a Tuesday, 2026-10-08 a Thursday.

    @Test
    fun latestSlotIsLastTuesdayOrThursdayMorning() {
        assertEquals(t("2026-10-06T07:30:00Z"), SyncSchedule.latestSlot(t("2026-10-07T12:00:00Z")))
        assertEquals(t("2026-10-08T07:30:00Z"), SyncSchedule.latestSlot(t("2026-10-08T07:30:00Z")))
        assertEquals(t("2026-10-06T07:30:00Z"), SyncSchedule.latestSlot(t("2026-10-08T07:29:59Z")))
        assertEquals(t("2026-10-08T07:30:00Z"), SyncSchedule.latestSlot(t("2026-10-12T23:00:00Z")))
    }

    @Test
    fun nextSlot() {
        assertEquals(t("2026-10-08T07:30:00Z"), SyncSchedule.nextSlot(t("2026-10-06T07:30:00Z")))
        assertEquals(t("2026-10-13T07:30:00Z"), SyncSchedule.nextSlot(t("2026-10-09T01:00:00Z")))
    }

    @Test
    fun dueOnlyOncePerSlot() {
        val now = t("2026-10-08T09:00:00Z")
        assertTrue(SyncSchedule.isDue(null, now))
        assertTrue(SyncSchedule.isDue(t("2026-10-07T20:00:00Z"), now))
        assertFalse(SyncSchedule.isDue(t("2026-10-08T08:00:00Z"), now))
        // Wednesday after a Tuesday check: nothing to do
        assertFalse(SyncSchedule.isDue(t("2026-10-06T10:00:00Z"), t("2026-10-07T18:00:00Z")))
        // device was off from Tuesday to Saturday: catch up once
        assertTrue(SyncSchedule.isDue(t("2026-10-05T10:00:00Z"), t("2026-10-10T10:00:00Z")))
    }
}
