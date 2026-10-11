package com.qtekfun.ultimatevisitcards.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The backup file (docs/05 §2): a ZIP, optionally AES-256 encrypted (every entry, with one password) with
 *
 * ```
 * manifest.json                          BackupManifest
 * database.json                          BackupDatabase (ids kept as they are)
 * settings.json                          BackupSettings (non-sensitive preferences)
 * images/<cardId>/front.jpg, back.jpg, front_original.jpg, back_original.jpg
 * ```
 *
 * [VERSION] is the backup format version, independent of the Room schema version.
 */
object BackupFormat {
    const val VERSION = 1
    const val MANIFEST = "manifest.json"
    const val DATABASE = "database.json"
    const val SETTINGS = "settings.json"
    const val IMAGES = "images/"

    /** Storage path `cards/<id>/front.jpg` ↔ backup entry `images/<id>/front.jpg`. */
    fun entryFor(storagePath: String): String = IMAGES + storagePath.removePrefix(CARDS)

    fun storagePathFor(entry: String): String = CARDS + entry.removePrefix(IMAGES)

    private const val CARDS = "cards/"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

@Serializable
data class BackupManifest(
    @SerialName("backup_version") val backupVersion: Int,
    @SerialName("app_version") val appVersion: String,
    @SerialName("created_at") val createdAt: Long,
    val counts: BackupCounts,
    @SerialName("includes_originals") val includesOriginals: Boolean,
    val encrypted: Boolean,
)

@Serializable
data class BackupCounts(
    val contacts: Int,
    val companies: Int,
    val sectors: Int,
    val events: Int,
    val contactEvents: Int,
    val businessCards: Int,
    val images: Int,
)

@Serializable
data class BackupDatabase(
    val sectors: List<BackupSector> = emptyList(),
    val companies: List<BackupCompany> = emptyList(),
    val events: List<BackupEvent> = emptyList(),
    val contacts: List<BackupContact> = emptyList(),
    val contactEvents: List<BackupContactEvent> = emptyList(),
    val businessCards: List<BackupCard> = emptyList(),
)

@Serializable
data class BackupSector(val id: String, val name: String, val sortOrder: Int, val createdAt: Long)

@Serializable
data class BackupCompany(
    val id: String,
    val name: String,
    val sectorId: String? = null,
    val country: String? = null,
    val website: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupEvent(
    val id: String,
    val name: String,
    val location: String? = null,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
)

@Serializable
data class BackupContact(
    val id: String,
    val firstName: String,
    val lastName: String,
    val jobTitle: String? = null,
    val companyId: String? = null,
    val country: String? = null,
    val phone: String? = null,
    val mobile: String? = null,
    val email: String? = null,
    val website: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val exportedToPhoneAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupContactEvent(val contactId: String, val eventId: String)

@Serializable
data class BackupCard(
    val id: String,
    val contactId: String? = null,
    val eventId: String? = null,
    val frontOriginal: String,
    val frontProcessed: String,
    val backOriginal: String? = null,
    val backProcessed: String? = null,
    val frontQuad: String? = null,
    val backQuad: String? = null,
    val rawOcrText: String = "",
    val extractedJson: String? = null,
    val ocrEngine: String? = null,
    val ocrStatus: String,
    val isPrimary: Boolean,
    val capturedAt: Long,
    val createdAt: Long,
)

/** Non-sensitive preferences carried by the backup (no passwords, no lock settings). */
@Serializable
data class BackupSettings(val cardsView: String? = null)
