package com.sheets.services.`cell-management`

import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRedisRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Processor for primitive data type cells
 */
@Component
class PrimitiveDataProcessor(
    private val cellRedisRepository: CellRedisRepository,
    private val cellAsyncService: CellAsyncService,
    private val cellDependencyService: CellDependencyService
) {
    private val logger = LoggerFactory.getLogger(PrimitiveDataProcessor::class.java)

    /**
     * Process a new cell with primitive data
     */
    fun processNewCell(cell: Cell, timestamp: Instant, userId: String): Cell {
        logger.debug("Processing primitive data for new cell: {}", cell.id)

        // Create a new cell with primitive data type
        val newCell = cell.copy(
            dataType = DataType.PRIMITIVE,
            evaluatedValue = cell.data,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        // Save to Redis immediately
        logger.debug("Saving new primitive cell to Redis: {}", newCell.id)
        cellRedisRepository.saveCell(newCell)
        
        // Save to MongoDB asynchronously
        cellAsyncService.saveCell(newCell)
        
        logger.info("Successfully created primitive cell: {} by user: {}", cell.id, userId)
        return newCell
    }

    /**
     * Process an existing cell with primitive data
     */
    fun processExistingCell(
        existingCell: Cell,
        newCellData: Cell,
        timestamp: Instant,
        userId: String
    ): Cell {
        logger.debug("Processing primitive data for existing cell: {}", existingCell.id)

        // Update the cell
        val updatedCell = existingCell.copy(
            data = newCellData.data,
            dataType = DataType.PRIMITIVE,
            evaluatedValue = newCellData.data,
            updatedAt = timestamp
        )
        
        // Save to Redis immediately
        logger.debug("Saving updated primitive cell to Redis: {}", updatedCell.id)
        cellRedisRepository.saveCell(updatedCell)
        
        // Save to MongoDB asynchronously
        cellAsyncService.saveCell(updatedCell)
        
        // Clean up any dependencies that might have existed if this was previously an expression
        cellAsyncService.updateDependencies(existingCell.id, emptyList(), existingCell.sheetId, timestamp)
        
        // Update any cells that might depend on this cell
        cellAsyncService.updateDependentCells(existingCell.id, userId)
        
        logger.info("Successfully updated primitive cell: {} by user: {}", existingCell.id, userId)
        return updatedCell
    }
}
