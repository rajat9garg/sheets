package com.sheets.models.document

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * MongoDB document model for cell dependencies
 */
@Document(collection = "cell_dependencies")
@CompoundIndexes(
    CompoundIndex(name = "sheetId_idx", def = "{'sheetId': 1}"),
    CompoundIndex(name = "sourceCellId_idx", def = "{'sourceCellId': 1}"),
    CompoundIndex(name = "targetCellId_idx", def = "{'targetCellId': 1}"),
    CompoundIndex(name = "sourceCellId_targetCellId_idx", def = "{'sourceCellId': 1, 'targetCellId': 1}", unique = true)
)
data class CellDependencyDocument(
    @Id
    val id: String = "", // Auto-generated
    val sheetId: Long,
    val sourceCellId: String, // The cell that depends on another cell
    val targetCellId: String, // The cell that is being depended upon
    val createdAt: Instant,
    val updatedAt: Instant
)
