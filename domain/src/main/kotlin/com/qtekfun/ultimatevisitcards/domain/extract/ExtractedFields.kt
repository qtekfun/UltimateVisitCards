package com.qtekfun.ultimatevisitcards.domain.extract

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The fields the review screen proposes (docs/03-pantallas-navegacion.md, «Revisión»). */
@Serializable
enum class CardField {
    FIRST_NAME,
    LAST_NAME,
    JOB_TITLE,
    COMPANY,
    COUNTRY,
    PHONE,
    MOBILE,
    EMAIL,
    WEBSITE,
    ADDRESS,
    NOTES
}

/**
 * A proposed value with its confidence (0..1) and the OCR line it came from, so the review can highlight
 * doubtful fields and show where a value was read.
 */
@Serializable
data class ExtractedValue(val value: String, val confidence: Float, val sourceLine: String)

/**
 * The extractor's output, stored as JSON in `BusinessCard.extractedJson` so the card can be reviewed again
 * without repeating the OCR. Lines no field claimed are kept in [unassigned] (and always in `rawOcrText`).
 */
@Serializable
data class ExtractedFields(val fields: Map<CardField, ExtractedValue> = emptyMap(), val unassigned: List<String> = emptyList()) {
    operator fun get(field: CardField): ExtractedValue? = fields[field]

    fun value(field: CardField): String? = fields[field]?.value

    /** Fields under [threshold] are highlighted in the review. */
    fun doubtful(threshold: Float): Set<CardField> = fields.filterValues { it.confidence < threshold }.keys

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        /** Below this confidence a field is highlighted in the review (configurable later in Settings). */
        const val DEFAULT_THRESHOLD = 0.6f

        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(value: String?): ExtractedFields? = value?.let {
            try {
                json.decodeFromString(serializer(), it)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
