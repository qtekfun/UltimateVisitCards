package com.qtekfun.ultimatevisitcards.data.db

import com.qtekfun.ultimatevisitcards.data.db.entity.BusinessCardEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.CompanyEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.ContactFtsEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.EventEntity
import com.qtekfun.ultimatevisitcards.data.db.entity.SectorEntity
import com.qtekfun.ultimatevisitcards.domain.model.BusinessCard
import com.qtekfun.ultimatevisitcards.domain.model.Company
import com.qtekfun.ultimatevisitcards.domain.model.Contact
import com.qtekfun.ultimatevisitcards.domain.model.Event
import com.qtekfun.ultimatevisitcards.domain.model.OcrStatus
import com.qtekfun.ultimatevisitcards.domain.model.Sector
import com.qtekfun.ultimatevisitcards.domain.search.ContactSearchDocument

internal fun SectorEntity.toDomain() = Sector(id = id, name = name, sortOrder = sortOrder, createdAt = createdAt)

internal fun CompanyEntity.toDomain() = Company(
    id = id,
    name = name,
    sectorId = sectorId,
    country = country,
    website = website,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun Company.toEntity(normalizedName: String) = CompanyEntity(
    id = id,
    name = name,
    normalizedName = normalizedName,
    sectorId = sectorId,
    country = country,
    website = website,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun ContactEntity.toDomain() = Contact(
    id = id,
    firstName = firstName,
    lastName = lastName,
    jobTitle = jobTitle,
    companyId = companyId,
    country = country,
    phone = phone,
    mobile = mobile,
    email = email,
    website = website,
    address = address,
    notes = notes,
    exportedToPhoneAt = exportedToPhoneAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun Contact.toEntity() = ContactEntity(
    id = id,
    firstName = firstName,
    lastName = lastName,
    jobTitle = jobTitle,
    companyId = companyId,
    country = country,
    phone = phone,
    mobile = mobile,
    email = email,
    website = website,
    address = address,
    notes = notes,
    exportedToPhoneAt = exportedToPhoneAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

internal fun EventEntity.toDomain() = Event(
    id = id,
    name = name,
    location = location,
    startDate = startDate,
    endDate = endDate,
    notes = notes,
    createdAt = createdAt
)

internal fun Event.toEntity() = EventEntity(
    id = id,
    name = name,
    location = location,
    startDate = startDate,
    endDate = endDate,
    notes = notes,
    createdAt = createdAt
)

internal fun BusinessCardEntity.toDomain() = BusinessCard(
    id = id,
    contactId = contactId,
    eventId = eventId,
    frontOriginal = frontOriginal,
    frontProcessed = frontProcessed,
    backOriginal = backOriginal,
    backProcessed = backProcessed,
    frontQuad = frontQuad,
    backQuad = backQuad,
    rawOcrText = rawOcrText,
    extractedJson = extractedJson,
    ocrEngine = ocrEngine,
    // An unknown value (newer backup) is treated as pending so the card is processed again, never lost.
    ocrStatus = OcrStatus.entries.firstOrNull { it.name == ocrStatus } ?: OcrStatus.PENDING,
    isPrimary = isPrimary,
    capturedAt = capturedAt,
    createdAt = createdAt
)

internal fun BusinessCard.toEntity() = BusinessCardEntity(
    id = id,
    contactId = contactId,
    eventId = eventId,
    frontOriginal = frontOriginal,
    frontProcessed = frontProcessed,
    backOriginal = backOriginal,
    backProcessed = backProcessed,
    frontQuad = frontQuad,
    backQuad = backQuad,
    rawOcrText = rawOcrText,
    extractedJson = extractedJson,
    ocrEngine = ocrEngine,
    ocrStatus = ocrStatus.name,
    isPrimary = isPrimary,
    capturedAt = capturedAt,
    createdAt = createdAt
)

internal fun ContactSearchDocument.toEntity() = ContactFtsEntity(
    contactId = contactId,
    name = name,
    firstName = firstName,
    lastName = lastName,
    company = company,
    jobTitle = jobTitle,
    country = country,
    sector = sector,
    email = email,
    phone = phone,
    notes = notes,
    ocrText = ocrText,
    events = events
)
