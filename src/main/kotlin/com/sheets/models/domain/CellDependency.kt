package com.sheets.models.domain

import java.time.Instant

data class CellDependency(
    val id: String = "", // Auto-generated
    val sheetId: Long,
    val sourceCellId: String, // The cell that depends on another cell
    val targetCellId: String, // The cell that is being depended upon
    val createdAt: Instant,
    val updatedAt: Instant
)
