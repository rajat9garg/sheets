package com.sheets.models.domain

import java.time.Instant

data class Cell(
    val id: String, // Format: sheetId:row:column
    val sheetId: Long,
    val row: Int,
    val column: String,
    val data: String,
    val dataType: DataType,
    val evaluatedValue: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
