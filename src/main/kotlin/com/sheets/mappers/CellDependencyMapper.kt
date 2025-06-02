package com.sheets.mappers

import com.sheets.models.document.CellDependencyDocument
import com.sheets.models.domain.CellDependency
import org.springframework.stereotype.Component

/**
 * Mapper for converting between CellDependency domain model and CellDependencyDocument
 */
@Component
class CellDependencyMapper {
    
    /**
     * Convert a domain CellDependency to a CellDependencyDocument
     * 
     * @param dependency The domain CellDependency to convert
     * @return The corresponding CellDependencyDocument
     */
    fun toDocument(dependency: CellDependency): CellDependencyDocument {
        return CellDependencyDocument(
            id = dependency.id,
            sheetId = dependency.sheetId,
            sourceCellId = dependency.sourceCellId,
            targetCellId = dependency.targetCellId,
            createdAt = dependency.createdAt,
            updatedAt = dependency.updatedAt
        )
    }
    
    /**
     * Convert a CellDependencyDocument to a domain CellDependency
     * 
     * @param document The CellDependencyDocument to convert
     * @return The corresponding domain CellDependency
     */
    fun toDomain(document: CellDependencyDocument): CellDependency {
        return CellDependency(
            id = document.id,
            sheetId = document.sheetId,
            sourceCellId = document.sourceCellId,
            targetCellId = document.targetCellId,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt
        )
    }
}
