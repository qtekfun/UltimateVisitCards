package com.qtekfun.ultimatevisitcards.domain.merge

import com.qtekfun.ultimatevisitcards.domain.model.Contact
import java.time.Instant
import java.time.ZoneOffset

/** The contact fields compared side by side when merging (docs/06 §4). Notes are never chosen: they are joined. */
enum class MergeField { FIRST_NAME, LAST_NAME, JOB_TITLE, COMPANY, COUNTRY, PHONE, MOBILE, EMAIL, WEBSITE, ADDRESS }

/** One side of a merge: its values by field, its notes, and how recent its newest card is. */
data class MergeSide(val values: Map<MergeField, String?>, val notes: String?, val newestCardAt: Long) {
    fun value(field: MergeField): String? = values[field]?.takeIf { it.isNotBlank() }

    companion object {
        fun of(contact: Contact, companyName: String?, newestCardAt: Long) = MergeSide(
            values = mapOf(
                MergeField.FIRST_NAME to contact.firstName,
                MergeField.LAST_NAME to contact.lastName,
                MergeField.JOB_TITLE to contact.jobTitle,
                MergeField.COMPANY to companyName,
                MergeField.COUNTRY to contact.country,
                MergeField.PHONE to contact.phone,
                MergeField.MOBILE to contact.mobile,
                MergeField.EMAIL to contact.email,
                MergeField.WEBSITE to contact.website,
                MergeField.ADDRESS to contact.address
            ),
            notes = contact.notes,
            newestCardAt = newestCardAt
        )
    }
}

enum class MergeSource { EXISTING, INCOMING }

/**
 * What the comparison screen shows: per field, both values and the one chosen. Defaults (docs/06 §4): the
 * non-empty value; if both differ, the one from the side with the most recent card, the other one a tap away.
 */
data class MergePlan(val existing: MergeSide, val incoming: MergeSide, val choice: Map<MergeField, MergeSource>) {
    /** Fields where both sides have different values: the only ones worth showing as a choice. */
    val conflicts: List<MergeField>
        get() = MergeField.entries.filter { f ->
            val a = existing.value(f)
            val b = incoming.value(f)
            a != null && b != null && a != b
        }

    fun chosen(field: MergeField): String? = when (choice[field] ?: MergeSource.EXISTING) {
        MergeSource.EXISTING -> existing.value(field) ?: incoming.value(field)
        MergeSource.INCOMING -> incoming.value(field) ?: existing.value(field)
    }

    fun choose(field: MergeField, source: MergeSource) = copy(choice = choice + (field to source))

    /** Both notes kept: the existing ones, then the incoming ones under a dated separator. */
    fun mergedNotes(at: Long): String? {
        val a = existing.notes?.takeIf { it.isNotBlank() }
        val b = incoming.notes?.takeIf { it.isNotBlank() }
        if (a == null || b == null || a == b) return a ?: b
        val day = Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC).toLocalDate()
        return "$a\n— $day —\n$b"
    }

    companion object {
        fun of(existing: MergeSide, incoming: MergeSide): MergePlan {
            val newer = if (incoming.newestCardAt >= existing.newestCardAt) MergeSource.INCOMING else MergeSource.EXISTING
            val choice = MergeField.entries.associateWith { f ->
                when {
                    existing.value(f) == null -> MergeSource.INCOMING
                    incoming.value(f) == null -> MergeSource.EXISTING
                    else -> newer
                }
            }
            return MergePlan(existing, incoming, choice)
        }
    }
}
