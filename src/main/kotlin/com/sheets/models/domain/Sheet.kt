package com.sheets.models.domain

import java.time.Instant

/**
 * Domain model for Sheet
 */
data class Sheet(
    val id: Long = 0,
    val name: String,
    val description: String,
    val maxLength: Int = 100,
    val maxBreadth: Int = 100,
    val userId: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    // This field is used for mapping purposes and is not stored in the database
    val requestUserId: Long = userId
)
