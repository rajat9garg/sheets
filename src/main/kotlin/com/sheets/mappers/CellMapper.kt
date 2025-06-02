package com.sheets.mappers

import com.sheets.models.document.CellDocument
import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import org.springframework.stereotype.Component

/**
 * Mapper for converting between Cell domain model and CellDocument
 */
@Component
class CellMapper {
    
    /**
     * Convert a domain Cell to a CellDocument
     * 
     * @param cell The domain Cell to convert
     * @return The corresponding CellDocument
     */
    fun toDocument(cell: Cell): CellDocument {
        return CellDocument(
            id = cell.id,
            sheetId = cell.sheetId,
            row = cell.row,
            column = cell.column,
            data = cell.data,
            dataType = cell.dataType.name,
            evaluatedValue = cell.evaluatedValue,
            createdAt = cell.createdAt,
            updatedAt = cell.updatedAt
        )
    }
    
    /**
     * Convert a CellDocument to a domain Cell
     * 
     * @param document The CellDocument to convert
     * @return The corresponding domain Cell
     */
    fun toDomain(document: CellDocument): Cell {
        return Cell(
            id = document.id,
            sheetId = document.sheetId,
            row = document.row,
            column = document.column,
            data = document.data,
            dataType = DataType.valueOf(document.dataType),
            evaluatedValue = document.evaluatedValue,
            createdAt = document.createdAt,
            updatedAt = document.updatedAt
        )
    }
}
