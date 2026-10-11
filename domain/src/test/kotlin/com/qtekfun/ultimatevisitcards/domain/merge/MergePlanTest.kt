package com.qtekfun.ultimatevisitcards.domain.merge

import kotlin.test.assertEquals
import org.junit.Test

class MergePlanTest {
    private fun side(newest: Long, notes: String? = null, vararg values: Pair<MergeField, String?>) = MergeSide(values.toMap(), notes, newest)

    @Test
    fun `non-empty values win, conflicts go to the side with the newest card`() {
        val existing = side(2026, null, MergeField.JOB_TITLE to "Sales Manager", MergeField.PHONE to "+34 900 000 001", MergeField.EMAIL to null)
        val incoming = side(2028, null, MergeField.JOB_TITLE to "Sales Director", MergeField.PHONE to null, MergeField.EMAIL to "john@abc.com")
        val plan = MergePlan.of(existing, incoming)
        assertEquals("Sales Director", plan.chosen(MergeField.JOB_TITLE))
        assertEquals("+34 900 000 001", plan.chosen(MergeField.PHONE))
        assertEquals("john@abc.com", plan.chosen(MergeField.EMAIL))
        assertEquals(listOf(MergeField.JOB_TITLE), plan.conflicts)
        // The other value is one tap away.
        assertEquals("Sales Manager", plan.choose(MergeField.JOB_TITLE, MergeSource.EXISTING).chosen(MergeField.JOB_TITLE))
    }

    @Test
    fun `an older incoming card keeps the existing values in conflicts`() {
        val plan = MergePlan.of(side(2028, null, MergeField.JOB_TITLE to "Director"), side(2026, null, MergeField.JOB_TITLE to "Manager"))
        assertEquals("Director", plan.chosen(MergeField.JOB_TITLE))
    }

    @Test
    fun `notes are joined with a dated separator, never lost`() {
        val plan = MergePlan.of(side(1, "Met at SMM"), side(2, "Wants a quote"))
        assertEquals("Met at SMM\n— 2026-01-01 —\nWants a quote", plan.mergedNotes(1_767_225_600_000L))
        assertEquals("Only one", MergePlan.of(side(1, "Only one"), side(2, null)).mergedNotes(0))
        assertEquals("Same", MergePlan.of(side(1, "Same"), side(2, "Same")).mergedNotes(0))
    }
}
