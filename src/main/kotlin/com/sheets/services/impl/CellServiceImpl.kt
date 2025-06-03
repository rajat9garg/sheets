package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.repositories.CellRedisRepository
import com.sheets.repositories.CellRepository
import com.sheets.services.*
import com.sheets.services.`cell-management`.CellUtils
import com.sheets.services.`cell-management`.ExpressionDataProcessor
import com.sheets.services.`cell-management`.PrimitiveDataProcessor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Service
class CellServiceImpl(
    private val cellRepository: CellRepository,
    private val cellRedisRepository: CellRedisRepository,
    private val cellAsyncService: CellAsyncService,
    private val cellDependencyService: CellDependencyService,
    private val cellLockService: CellLockService,
    private val primitiveDataProcessor: PrimitiveDataProcessor,
    private val expressionDataProcessor: ExpressionDataProcessor
) : CellService {

    private val logger = LoggerFactory.getLogger(CellServiceImpl::class.java)

    override fun getCell(id: String): Cell? {
        logger.info("Getting cell: {}", id)
        
        // Try to get from Redis first
        val cell = cellRedisRepository.getCell(id)
        if (cell != null) {
            logger.debug("Cell found in Redis: {}", id)
            return cell
        }
        
        // If not in Redis, try to get from MongoDB
        logger.debug("Cell not found in Redis: {}. Trying MongoDB.", id)
        val cellFromMongo = cellRepository.findById(id)
        
        if (cellFromMongo != null) {
            // Save to Redis for future use
            logger.debug("Cell found in MongoDB: {}. Saving to Redis.", id)
            cellRedisRepository.saveCell(cellFromMongo)
        }
        
        return cellFromMongo
    }

    override fun getCellsBySheetId(sheetId: Long): List<Cell> {
        logger.info("Getting cells for sheet: {}", sheetId)
        
        val cellsFromRedis = cellRedisRepository.getCellsBySheetId(sheetId)
        
        if (cellsFromRedis.isNotEmpty()) {
            logger.debug("Cells found in Redis for sheet: {}", sheetId)
            return cellsFromRedis
        }
        
        logger.debug("Getting cells from MongoDB for sheet: {}", sheetId)
        
        val cellsFromMongo = cellRepository.findBySheetId(sheetId)
        
        if (cellsFromMongo.isNotEmpty()) {
            logger.debug("Saving cells to Redis for sheet: {}", sheetId)
            cellsFromMongo.forEach { cellRedisRepository.saveCell(it) }
        }
        
        return cellsFromMongo
    }

    override fun updateCell(cell: Cell, userId: String): Cell {
        logger.info("Updating cell: {} by user: {}", cell.id, userId)
        
        return try {
            // Step 1: Retrieve existing cell or prepare for creation
            val existingCell = getCell(cell.id)
            val now = Instant.now()
            
            // Step 2: Process the cell data based on whether it's new or existing
            if (existingCell == null) {
                createNewCell(cell, now, userId)
            } else {
                updateExistingCell(existingCell, cell, now, userId)
            }
        } catch (e: Exception) {
            logger.error("Error updating cell: {} by user: {} - {}: {}", 
                cell.id, userId, e.javaClass.simpleName, e.message, e)
            throw e
        }
    }


    private fun createNewCell(cell: Cell, timestamp: Instant, userId: String): Cell {
        logger.info("Creating new cell: {}", cell.id)
        
        // Process the cell data based on its type
        return when {
            CellUtils.isExpression(cell.data) -> expressionDataProcessor.processNewCell(cell, timestamp, userId)
            else -> primitiveDataProcessor.processNewCell(cell, timestamp, userId)
        }
    }


    private fun updateExistingCell(
        existingCell: Cell,
        newCellData: Cell,
        timestamp: Instant,
        userId: String
    ): Cell {
        logger.info("Updating existing cell: {}", existingCell.id)
        
        return when {
            CellUtils.isExpression(newCellData.data) -> expressionDataProcessor.processExistingCell(existingCell, newCellData, timestamp, userId)
            else -> primitiveDataProcessor.processExistingCell(existingCell, newCellData, timestamp, userId)
        }
    }

    override fun deleteCell(id: String, userId: String) {
        logger.info("Deleting cell: {} by user: {}", id, userId)
        
        try {
            // Check if the cell is used in any expressions (has dependencies)
            val dependencies = cellDependencyService.getDependenciesByTargetCellId(id)
            if (dependencies.isNotEmpty()) {
                val dependentCellIds = dependencies.map { it.sourceCellId }
                logger.warn("Cannot delete cell: {} as it is used in expressions in cells: {}", id, dependentCellIds)
                throw IllegalStateException("Cannot delete cell as it is used in expressions in cells: ${dependentCellIds.joinToString(", ")}")
            }
            
            // Acquire lock on the cell
            if (!cellLockService.acquireLock(id, userId)) {
                logger.warn("Failed to acquire lock on cell: {} for user: {}", id, userId)
                throw IllegalStateException("Could not acquire lock on cell: $id")
            }
            
            try {
                // Delete the cell from Redis
                logger.debug("Deleting cell from Redis: {}", id)
                cellRedisRepository.deleteCell(id)
                
                // Delete the cell from MongoDB asynchronously
                logger.debug("Deleting cell from MongoDB asynchronously: {}", id)
                cellAsyncService.deleteCell(id)
                
                // Delete dependencies
                logger.debug("Deleting dependencies for cell: {}", id)
                cellDependencyService.deleteBySourceCellId(id)
                
                logger.info("Successfully deleted cell: {} by user: {}", id, userId)
            } finally {
                // Release the lock
                logger.debug("Releasing lock on cell: {} for user: {}", id, userId)
                cellLockService.releaseLock(id, userId)
            }
        } catch (e: Exception) {
            logger.error("Error deleting cell: {} by user: {} - {}: {}", id, userId, e.javaClass.simpleName, e.message, e)
            throw e
        }
    }

    fun evaluateExpression(cellId: String, expression: String, userId: String): String {
        logger.info("Evaluating expression for cell: {}", cellId)
        
        val parts = cellId.split(":")
        if (parts.size != 3) {
            throw IllegalArgumentException("Invalid cell ID: $cellId")
        }
        
        val sheetId = parts[0].toLong()
        val row = parts[1].toInt()
        val column = parts[2]
        val now = Instant.now()
        
        try {
            val (dataType, evaluatedValue, dependencies) = expressionDataProcessor.processExpression(expression, sheetId, row, column)
            
            logger.debug("Updating dependencies for cell: {}", cellId)
            cellDependencyService.deleteBySourceCellId(cellId)
            
            if (dependencies.isNotEmpty()) {
                logger.debug("Creating {} new dependencies for cell: {}", dependencies.size, cellId)
                val cellDependencies = CellUtils.createCellDependencies(sheetId, cellId, dependencies, now)
                cellDependencyService.createDependencies(cellDependencies)
            }
            
            return evaluatedValue
        } catch (e: Exception) {
            logger.error("Error evaluating expression for cell: {} - {}: {}", cellId, e.javaClass.simpleName, e.message, e)
            throw e
        }
    }

    /**
     * Update dependent cells synchronously
     */
    fun updateDependentCellsSync(cellId: String, userId: String) {
        logger.info("Updating dependent cells synchronously for cell: {}", cellId)
        
        try {
            // Get all cells that depend on this cell
            val dependencies = cellDependencyService.getDependenciesByTargetCellId(cellId)
            
            if (dependencies.isEmpty()) {
                logger.debug("No dependent cells found for cell: {}", cellId)
                return
            }
            
            logger.debug("Found {} dependent cells for cell: {}", dependencies.size, cellId)
            
            // Update each dependent cell
            for (dependency in dependencies) {
                val sourceCellId = dependency.sourceCellId
                logger.debug("Updating dependent cell: {}", sourceCellId)
                
                // Get the dependent cell
                val cell = getCell(sourceCellId)
                
                if (cell != null) {
                    // Update the cell
                    logger.debug("Updating dependent cell: {}", sourceCellId)
                    updateCell(cell, userId)
                } else {
                    logger.warn("Dependent cell not found: {}", sourceCellId)
                }
            }
            
            logger.info("Successfully updated dependent cells for cell: {}", cellId)
        } catch (e: Exception) {
            logger.error("Error updating dependent cells for cell: {} - {}: {}", cellId, e.javaClass.simpleName, e.message, e)
            throw e
        }
    }

    private fun updateDependentCellsAsync(cellId: String, userId: String) {
        logger.info("Updating dependent cells asynchronously for cell: {}", cellId)
        
        CompletableFuture.runAsync {
            try {
                updateDependentCellsSync(cellId, userId)
            } catch (e: Exception) {
                logger.error("Error in async update of dependent cells for cell: {} - {}: {}", cellId, e.javaClass.simpleName, e.message, e)
            }
        }
    }
}
