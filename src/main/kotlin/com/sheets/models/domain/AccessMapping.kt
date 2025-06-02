package com.sheets.models.domain

import java.time.Instant
import java.util.UUID


data class AccessMapping(
    val id: UUID? = null,
    val sheetId: Long,
    val userId: Long,
    val accessType: AccessType,
    val createdAt: Instant,
    val updatedAt: Instant
)
