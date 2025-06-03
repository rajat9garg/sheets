package com.sheets.controllers

import com.sheets.generated.api.CellApi
import com.sheets.generated.model.CellBatchResponse
import com.sheets.generated.model.CellDataRequest
import com.sheets.generated.model.CellRequest
import com.sheets.generated.model.CellResponse
import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.services.CellService
import com.sheets.services.SheetService
import com.sheets.services.expression.exception.CircularDependencyException
import com.sheets.services.expression.exception.ExpressionException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId

@RestController
class CellController(
    private val cellService: CellService,
    private val sheetService: SheetService
) : CellApi {

    private val logger = LoggerFactory.getLogger(CellController::class.java)

    override fun updateCell(
        sheetId: Long,
        xUserID: Long,
        cellRequest: CellRequest
    ): ResponseEntity<CellResponse> {
        logger.info("Received request to update cell at row: {}, column: {} in sheet: {} by user: {}", 
            cellRequest.row, cellRequest.column, sheetId, xUserID)
        
        try {
            // Validate that the sheet exists and user has access
            logger.debug("Validating sheet access for sheet: {} and user: {}", sheetId, xUserID)
            sheetService.getSheetById(sheetId, xUserID)

            // Convert column number to alphabetical notation (A, B, C, ... AA, AB, etc.)
            val columnLetter = numberToColumnLetter(cellRequest.column)
            val cellPosition = "$columnLetter${cellRequest.row}"
            logger.debug("Cell position in A1 notation: {}", cellPosition)
            
            // Create a cell object from the request
            val cellId = "${sheetId}:${cellRequest.row}:${cellRequest.column}"
            logger.debug("Cell ID for update: {}", cellId)
            
            try {
                // Get existing cell or create a new one if it doesn't exist
                logger.debug("Retrieving existing cell with ID: {}", cellId)
                val existingCell = cellService.getCell(cellId) ?: Cell(
                    id = cellId,
                    sheetId = sheetId,
                    row = cellRequest.row,
                    column = cellRequest.column.toString(),
                    data = "",
                    dataType = DataType.PRIMITIVE,
                    evaluatedValue = "",
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                
                // Check if the data is an expression
                val data = cellRequest.data
                val isExpression = data.trim().startsWith("=")
                
                if (isExpression) {
                    logger.debug("Processing expression: {}", data)
                    try {
                        // Update the cell with the expression
                        val updatedCell = cellService.updateCell(
                            existingCell.copy(
                                data = data
                            ),
                            xUserID.toString()
                        )
                        
                        // Convert to response
                        val response = toCellResponse(updatedCell)
                        logger.info("Successfully updated cell with expression at position: {} in sheet: {} by user: {}", 
                            cellPosition, sheetId, xUserID)
                        
                        return ResponseEntity.ok(response)
                    } catch (e: CircularDependencyException) {
                        logger.error("Circular dependency detected while updating cell at position: {} in sheet: {} by user: {}: {}", 
                            cellPosition, sheetId, xUserID, e.message)
                        return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(null)
                    } catch (e: ExpressionException) {
                        logger.error("Expression error while updating cell at position: {} in sheet: {} by user: {}: {}", 
                            cellPosition, sheetId, xUserID, e.message)
                        return ResponseEntity
                            .status(HttpStatus.BAD_REQUEST)
                            .body(null)
                    }
                } else {
                    // Update the cell with a primitive value
                    logger.debug("Processing primitive value for cell: {}", cellId)
                    val updatedCell = cellService.updateCell(
                        existingCell.copy(
                            data = data
                        ),
                        xUserID.toString()
                    )
                    
                    // Convert to response
                    val response = toCellResponse(updatedCell)
                    logger.info("Successfully updated cell with primitive value at position: {} in sheet: {} by user: {}", 
                        cellPosition, sheetId, xUserID)
                    
                    return ResponseEntity.ok(response)
                }
            } catch (e: IllegalStateException) {
                // This is likely a locking issue or deadlock
                logger.error("Lock acquisition failed while updating cell at position: {} in sheet: {} by user: {}: {}", 
                    cellPosition, sheetId, xUserID, e.message)
                return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(null)
            } catch (e: Exception) {
                logger.error("Unexpected error while updating cell at position: {} in sheet: {} by user: {}: {}", 
                    cellPosition, sheetId, xUserID, e.message, e)
                return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null)
            }
        } catch (e: Exception) {
            logger.error("Error validating sheet access for sheet: {} and user: {}: {}", 
                sheetId, xUserID, e.message, e)
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null)
        }
    }
    

    /**
     * Updates a cell by its direct ID
     */
    override fun updateCellById(
        sheetId: Long,
        cellId: String,
        xUserID: Long,
        cellDataRequest: CellDataRequest
    ): ResponseEntity<CellResponse> {
        logger.info("Received request to update cell with ID: {} in sheet: {} by user: {}", 
            cellId, sheetId, xUserID)
        
        try {
            // Validate that the sheet exists and user has access
            logger.debug("Validating sheet access for sheet: {} and user: {}", sheetId, xUserID)
            sheetService.getSheetById(sheetId, xUserID)
            
            // Validate that the cell ID belongs to the sheet
            if (!cellId.startsWith("$sheetId:")) {
                logger.warn("Cell ID: {} does not belong to sheet: {}", cellId, sheetId)
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(null)
            }
            
            // Validate that the value is not null
            val cellValue = cellDataRequest.value
            if (cellValue == null) {
                logger.warn("Cell value cannot be null for cell ID: {}", cellId)
                return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(null)
            }
            
            try {
                // Parse the cell ID to get row and column
                val parts = cellId.split(":")
                if (parts.size != 3) {
                    logger.warn("Invalid cell ID format: {}", cellId)
                    return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(null)
                }
                
                try {
                    val row = parts[1].toInt()
                    val column = parts[2].toInt()
                    
                    // Create a cell object with the provided data
                    val cell = Cell(
                        id = cellId,
                        sheetId = sheetId,
                        row = row,
                        column = column.toString(),
                        data = cellValue,
                        dataType = DataType.PRIMITIVE, // This will be determined by the service
                        evaluatedValue = "",           // This will be determined by the service
                        createdAt = Instant.now(),     // This will be overridden if cell exists
                        updatedAt = Instant.now()
                    )
                    
                    // Let the service handle cell existence check and creation/update
                    logger.debug("Sending cell to service for update: {}", cellId)
                    val updatedCell = cellService.updateCell(cell, xUserID.toString())
                    
                    // Convert to response
                    val response = toCellResponse(updatedCell)
                    logger.info("Successfully updated cell with ID: {} in sheet: {} by user: {}", 
                        cellId, sheetId, xUserID)
                    
                    return ResponseEntity.ok(response)
                } catch (e: NumberFormatException) {
                    logger.warn("Invalid row or column in cell ID: {}", cellId)
                    return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(null)
                }
            } catch (e: Exception) {
                logger.error("Error updating cell: {}", e.message, e)
                return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null)
            }
        } catch (e: Exception) {
            logger.error("Error updating cell: {}", e.message, e)
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null)
        }
    }
    
    override fun getCellsBySheetId(
        sheetId: Long,
        xUserID: Long
    ): ResponseEntity<CellBatchResponse> {
        logger.info("Received request to get all cells in sheet: {} by user: {}", sheetId, xUserID)
        
        try {
            // Validate that the sheet exists and user has access
            logger.debug("Validating sheet access for sheet: {} and user: {}", sheetId, xUserID)
            sheetService.getSheetById(sheetId, xUserID)
                
            // Get cells
            logger.debug("Retrieving cells for sheet: {}", sheetId)
            val cells = cellService.getCellsBySheetId(sheetId)
            
            // Convert to response
            val cellResponses = cells.map { toCellResponse(it) }
            logger.info("Successfully retrieved {} cells from sheet: {} for user: {}", 
                cells.size, sheetId, xUserID)
            
            return ResponseEntity.ok(CellBatchResponse(cellResponses))
        } catch (e: Exception) {
            logger.error("Error retrieving cells for sheet: {} and user: {}: {}", 
                sheetId, xUserID, e.message, e)
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null)
        }
    }
    
    override fun getCell(
        sheetId: Long,
        row: Int,
        column: Int,
        xUserID: Long
    ): ResponseEntity<CellResponse> {
        logger.info("Received request to get cell at row: {}, column: {} in sheet: {} by user: {}", 
            row, column, sheetId, xUserID)
        
        try {
            // Validate that the sheet exists and user has access
            logger.info("Validating sheet access for sheet: {} and user: {}", sheetId, xUserID)
            sheetService.getSheetById(sheetId, xUserID)
                
            // Get cell
            val cellId = "${sheetId}:${row}:${column}"
            logger.debug("Retrieving cell with ID: {}", cellId)
            val cell = cellService.getCell(cellId) ?: run {
                logger.info("Cell not found with ID: {}", cellId)
                return ResponseEntity.notFound().build()
            }
                
            // Convert to response
            val response = toCellResponse(cell)
            logger.info("Successfully retrieved cell at row: {}, column: {} in sheet: {} for user: {}", 
                row, column, sheetId, xUserID)
            
            return ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Error retrieving cell at row: {}, column: {} in sheet: {} for user: {}: {}", 
                row, column, sheetId, xUserID, e.message, e)
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null)
        }
    }
    
    override fun deleteCell(
        sheetId: Long,
        row: Int,
        column: Int,
        xUserID: Long
    ): ResponseEntity<Unit> {
        logger.info("Received request to delete cell at row: {}, column: {} in sheet: {} by user: {}", 
            row, column, sheetId, xUserID)
        
        try {
            // Validate that the sheet exists and user has access
            logger.info("Validating sheet access for sheet: {} and user: {}", sheetId, xUserID)
            sheetService.getSheetById(sheetId, xUserID)
                
            // Delete cell
            val cellId = "${sheetId}:${row}:${column}"
            logger.debug("Deleting cell with ID: {}", cellId)
            cellService.deleteCell(cellId, xUserID.toString())
            logger.info("Successfully deleted cell at row: {}, column: {} in sheet: {} by user: {}", 
                row, column, sheetId, xUserID)
            return ResponseEntity.noContent().build()
        } catch (e: IllegalStateException) {
            // This is likely a locking issue
            logger.error("Lock acquisition failed while deleting cell at row: {}, column: {} in sheet: {} by user: {}: {}", 
                row, column, sheetId, xUserID, e.message)
            return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .build()
        } catch (e: Exception) {
            logger.error("Unexpected error while deleting cell at row: {}, column: {} in sheet: {} by user: {}: {}", 
                row, column, sheetId, xUserID, e.message, e)
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build()
        }
    }
    
    private fun toCellResponse(cell: Cell): CellResponse {
        logger.debug("Converting domain Cell to CellResponse: {}", cell.id)
        return CellResponse(
            id = cell.id,
            sheetId = cell.sheetId,
            row = cell.row,
            column = cell.column.toIntOrNull() ?: 0,
            data = cell.data,
            dataType = when (cell.dataType) {
                DataType.PRIMITIVE -> CellResponse.DataType.PRIMITIVE
                DataType.EXPRESSION -> CellResponse.DataType.EXPRESSION
            },
            evaluatedValue = cell.evaluatedValue,
            createdAt = cell.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            updatedAt = cell.updatedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        )
    }
    
    /**
     * Converts a column number to alphabetical notation (A, B, C, ... AA, AB, etc.)
     */
    private fun numberToColumnLetter(columnNumber: Int): String {
        var dividend = columnNumber
        var columnName = ""
        
        while (dividend > 0) {
            val modulo = (dividend - 1) % 26
            columnName = (modulo + 'A'.code).toChar() + columnName
            dividend = (dividend - modulo) / 26
        }
        
        return columnName
    }
    
    /**
     * Converts an alphabetical column notation to a number
     */
    private fun columnLetterToNumber(columnLetter: String): Int {
        var result = 0
        for (c in columnLetter) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result
    }
}
