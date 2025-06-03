package com.sheets.models.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB document model for cells
 */
@Document(collection = "cells")
@CompoundIndexes(
    CompoundIndex(name = "sheetId_row_column", def = "{'sheetId': 1, 'row': 1, 'column': 1}", unique = true),
    CompoundIndex(name = "sheetId_idx", def = "{'sheetId': 1}")
)
data class CellDocument(
    @Id
    val id: String, // Format: sheetId:row:column
    val sheetId: Long,
    val row: Int,
    val column: String,
    val data: String,
    val dataType: String, // "PRIMITIVE" or "EXPRESSION"
    val evaluatedValue: String,
    val createdAt: Instant,
    val updatedAt: Instant
)
