package com.sheets.controllers

import com.sheets.generated.api.CellApi
import com.sheets.generated.model.CellBatchResponse
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

            // Use column letter directly from the request
            val columnLetter = cellRequest.column
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
                    column = cellRequest.column,
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
        column: String,
        xUserID: Long
    ): ResponseEntity<CellResponse> {
        logger.info("Received request to get cell at row: {}, column: {} in sheet: {} by user: {}", 
            row, column, sheetId, xUserID)
        
        try {
            // Validate that the sheet exists and user has access
            logger.debug("Validating sheet access for sheet: {} and user: {}", sheetId, xUserID)
            sheetService.getSheetById(sheetId, xUserID)
            
            // Create cell ID
            val cellId = "${sheetId}:${row}:${column}"
            logger.debug("Cell ID for retrieval: {}", cellId)
            
            // Get the cell
            val cell = cellService.getCell(cellId)
            
            if (cell == null) {
                logger.warn("Cell not found: {}", cellId)
                return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(null)
            }
            
            // Convert to response
            val response = toCellResponse(cell)
            logger.info("Successfully retrieved cell at row: {}, column: {} in sheet: {} by user: {}", 
                row, column, sheetId, xUserID)
            
            return ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Error retrieving cell at row: {}, column: {} in sheet: {} by user: {}: {}", 
                row, column, sheetId, xUserID, e.message, e)
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(null)
        }
    }
    
    override fun deleteCell(
        sheetId: Long,
        row: Int,
        column: String,
        xUserID: Long
    ): ResponseEntity<Unit> {
        logger.info("Received request to delete cell at row: {}, column: {} in sheet: {} by user: {}", 
            row, column, sheetId, xUserID)
        
        try {
            // Validate that the sheet exists and user has access
            logger.debug("Validating sheet access for sheet: {} and user: {}", sheetId, xUserID)
            sheetService.getSheetById(sheetId, xUserID)
            
            // Create cell ID
            val cellId = "${sheetId}:${row}:${column}"
            logger.debug("Cell ID for deletion: {}", cellId)
            
            try {
                // Delete the cell
                cellService.deleteCell(cellId, xUserID.toString())
                logger.info("Successfully deleted cell at row: {}, column: {} in sheet: {} by user: {}", 
                    row, column, sheetId, xUserID)
                
                return ResponseEntity.noContent().build()
            } catch (e: IllegalStateException) {
                if (e.message?.contains("Cannot delete cell as it is used in expressions") == true) {
                    logger.warn("Cannot delete cell at row: {}, column: {} in sheet: {} as it is used in expressions: {}", 
                        row, column, sheetId, e.message)
                    return ResponseEntity
                        .status(HttpStatus.CONFLICT)
                        .header("X-Error-Reason", "CELL_HAS_DEPENDENCIES")
                        .build()
                }
                
                // This is likely a locking issue or deadlock
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
        } catch (e: Exception) {
            logger.error("Error validating sheet access for sheet: {} and user: {}: {}", 
                sheetId, xUserID, e.message, e)
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .build()
        }
    }
    
    private fun toCellResponse(cell: Cell): CellResponse {
        return CellResponse(
            id = cell.id,
            sheetId = cell.sheetId,
            row = cell.row,
            column = cell.column,
            data = cell.data,
            dataType = CellResponse.DataType.valueOf(cell.dataType.name),
            evaluatedValue = cell.evaluatedValue,
            createdAt = cell.createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
            updatedAt = cell.updatedAt.atZone(ZoneId.systemDefault()).toOffsetDateTime()
        )
    }

}
