package com.sheets.services.`cell-management`

import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRedisRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
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
    private val cellDependencyService: CellDependencyService,
    private val cellLockService: CellLockService,
    private val expressionParser: ExpressionParser,
    private val expressionEvaluator: ExpressionEvaluator
) {
    private val logger = LoggerFactory.getLogger(PrimitiveDataProcessor::class.java)

    /**
     * Process a new cell with primitive data
     */
    fun processNewCell(cell: Cell, timestamp: Instant, userId: String): Cell {
        logger.debug("Processing primitive data for new cell: {}", cell.id)
        
        // Acquire lock on the cell
        if (!cellLockService.acquireLock(cell.id, userId)) {
            logger.warn("Failed to acquire lock on cell: {} for user: {}", cell.id, userId)
            throw IllegalStateException("Could not acquire lock on cell: ${cell.id}")
        }
        
        try {
            // Create a new cell with primitive data type
            val newCell = cell.copy(
                dataType = DataType.PRIMITIVE,
                evaluatedValue = cell.data,
                createdAt = timestamp,
                updatedAt = timestamp
            )
            
            // Save to Redis with TTL
            logger.debug("Saving new primitive cell to Redis: {}", newCell.id)
            cellRedisRepository.saveCell(newCell)
            
            // Save to MongoDB asynchronously
            logger.debug("Saving new primitive cell to MongoDB asynchronously: {}", newCell.id)
            cellAsyncService.saveCell(newCell)
            
            logger.info("Successfully created primitive cell: {} by user: {}", cell.id, userId)
            return newCell
        } finally {
            // Release the lock
            logger.debug("Releasing lock on cell: {} for user: {}", cell.id, userId)
            cellLockService.releaseLock(cell.id, userId)
        }
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
        
        // For primitive values, we still need to acquire a lock to prevent race conditions
        if (!cellLockService.acquireLock(existingCell.id, userId)) {
            throw IllegalStateException("Could not acquire lock on cell: ${existingCell.id}")
            TODO("throw contextual errors to the user")
            TODO("acquire lock on all the dependent cell a primitive data type can also be involed in another expression")
        }

        try {
            val updatedCell = existingCell.copy(
                data = newCellData.data,
                dataType = DataType.PRIMITIVE,
                evaluatedValue = newCellData.data,
                updatedAt = timestamp
            )
            
            // If the cell was previously an expression, clean up old dependencies
            if (existingCell.dataType == DataType.EXPRESSION) {
                logger.debug("Cleaning up dependencies for cell: {}", existingCell.id)
                cellDependencyService.deleteBySourceCellId(existingCell.id)
            }

            cellRedisRepository.saveCell(updatedCell)

            cellAsyncService.saveCell(updatedCell)

            updateDependentCells(existingCell.id, userId)
            
            logger.info("Successfully updated primitive cell: {} by user: {}", existingCell.id, userId)
            return updatedCell
        } finally {
            cellLockService.releaseLock(existingCell.id, userId)
        }
    }

    private fun updateDependentCells(cellId: String, userId: String) {
        logger.debug("Delegating dependent cell updates to CellDependencyService for cell: {}", cellId)
        cellDependencyService.updateDependentCells(cellId, userId)
    }

    private fun buildEvaluationContext(cellReferences: List<String>, sheetId: Long, row: Int, column: String): Map<String, String> {
        val context = mutableMapOf<String, String>()
        
        // Add sheet ID to the context
        context["sheetId"] = sheetId.toString()
        
        for (cellRef in cellReferences) {
            val cellId = if (cellRef.contains(":")) {
                "$sheetId:$cellRef"
            } else {
                val colLetter = cellRef.takeWhile { it.isLetter() }
                val rowNum = cellRef.dropWhile { it.isLetter() }.toIntOrNull() ?: row
                val colNum = colLetter.uppercase().fold(0) { acc, c -> acc * 26 + (c.code - 'A'.code + 1) }
                "$sheetId:$rowNum:$colNum"
            }
            
            val cell = cellRedisRepository.getCell(cellId)
            context[cellRef] = cell?.evaluatedValue ?: "#REF!"
        }
        
        context["row"] = row.toString()
        context["column"] = column
        
        return context
    }
}
