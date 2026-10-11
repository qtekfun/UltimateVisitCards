package com.qtekfun.ultimatevisitcards.domain.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A point in normalised image coordinates: (0, 0) is the top-left corner, (1, 1) the bottom-right one. */
@Serializable
data class NormPoint(val x: Float, val y: Float)

/**
 * The card's corners in the original photo, clockwise from the top-left one, in normalised coordinates so
 * they survive any rescaling of the original. Stored as JSON in `BusinessCard.frontQuad`/`backQuad` to
 * re-crop without taking the photo again.
 */
@Serializable
data class CardQuad(val topLeft: NormPoint, val topRight: NormPoint, val bottomRight: NormPoint, val bottomLeft: NormPoint) {
    val points: List<NormPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Null for a missing or unreadable value (the card is then re-cropped by hand if needed). */
        fun fromJson(value: String?): CardQuad? = value?.let {
            try {
                json.decodeFromString(serializer(), it)
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        fun of(points: List<NormPoint>): CardQuad {
            require(points.size == 4) { "A quad has four corners" }
            return CardQuad(points[0], points[1], points[2], points[3])
        }
    }
}
