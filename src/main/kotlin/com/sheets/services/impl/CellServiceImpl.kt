package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.repositories.CellRedisRepository
import com.sheets.repositories.CellRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.CellService
import com.sheets.services.`cell-management`.CellUtils
import com.sheets.services.`cell-management`.ExpressionDataProcessor
import com.sheets.services.`cell-management`.PrimitiveDataProcessor
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.exception.CircularDependencyException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CellServiceImpl(
    private val cellRepository: CellRepository,
    private val cellRedisRepository: CellRedisRepository,
    private val cellAsyncService: CellAsyncService,
    private val cellLockService: CellLockService,
    private val cellDependencyService: CellDependencyService,
    private val expressionParser: ExpressionParser,
    private val expressionDataProcessor: ExpressionDataProcessor,
    private val primitiveDataProcessor: PrimitiveDataProcessor,
    private val circularDependencyDetector: CircularDependencyDetector
) : CellService {

    private val logger = LoggerFactory.getLogger(CellServiceImpl::class.java)
    
    companion object {
        const val LOCK_TIMEOUT_MS = 30000L
    }

    override fun getCell(id: String): Cell? {
        logger.debug("Getting cell: {}", id)
        
        // Try to get from Redis first
        val cellFromRedis = cellRedisRepository.getCell(id)
        if (cellFromRedis != null) {
            logger.debug("Cell found in Redis: {}", id)
            return cellFromRedis
        }
        
        // If not in Redis, try to get from MongoDB
        logger.debug("Cell not found in Redis: {}. Trying MongoDB.", id)
        val cellFromMongo = cellRepository.findById(id)
        
        // If found in MongoDB, cache in Redis for next time
        if (cellFromMongo != null) {
            logger.debug("Cell found in MongoDB, caching in Redis: {}", id)
            cellRedisRepository.saveCell(cellFromMongo)
        }
        
        return cellFromMongo
    }

    override fun getCellsBySheetId(sheetId: Long): List<Cell> {
        logger.debug("Getting cells for sheet: {}", sheetId)
        
        // Get cells from MongoDB
        val cellsFromMongo = cellRepository.findBySheetId(sheetId)
        
        // Cache all cells in Redis
        cellsFromMongo.forEach { cell ->
            cellRedisRepository.saveCell(cell)
        }
        
        return cellsFromMongo
    }

    override fun updateCell(cell: Cell, userId: String): Cell {
        logger.info("Updating cell: {} by user: {}", cell.id, userId)
        
        // First, acquire sheet-level lock
        acquireSheetLock(cell.sheetId, userId)
        
        try {
            // Build dependency graph and check for circular dependencies
            val (cellsToLock, allDependencies) = buildDependencyGraph(cell)
            
            // Perform topological sort to determine lock acquisition order
            val sortedCellsToLock = topologicalSort(cellsToLock, allDependencies)
            logger.debug("Topologically sorted cells to lock: {}", sortedCellsToLock)
            
            // Acquire locks in topological order
            val acquiredLocks = acquireCellLocks(sortedCellsToLock, userId)
            
            try {
                // Perform the actual update
                return performCellUpdate(cell, userId)
            } finally {
                releaseCellLocks(acquiredLocks, userId)
            }
        } catch (e: CircularDependencyException) {
            logger.error("Circular dependency detected: {}", e.message)
            throw e
        } catch (e: IllegalStateException) {
            // Let IllegalStateException (lock conflicts) propagate up to the controller
            logger.error("Lock acquisition failed: {}", e.message)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error updating cell: {}", e.message, e)
            throw e
        } finally {
            // Always release the sheet-level lock
            try {
                releaseSheetLock(cell.sheetId, userId)
            } catch (e: Exception) {
                logger.warn("Error releasing sheet lock: {}", e.message, e)
                // Don't rethrow as we don't want to mask the original exception
            }
        }
    }
    
    /**
     * Acquires a lock on the sheet.
     * @throws IllegalStateException if the lock cannot be acquired
     */
    private fun acquireSheetLock(sheetId: Long, userId: String) {
        try {
            if (!cellLockService.acquireSheetLock(sheetId, userId, LOCK_TIMEOUT_MS)) {
                val lockOwner = cellLockService.getSheetLockOwner(sheetId)
                val errorMsg = "Could not acquire lock on sheet: $sheetId. Current lock owner: $lockOwner"
                logger.error(errorMsg)
                throw IllegalStateException(errorMsg)
            }
            logger.debug("Acquired lock on sheet: {} for user: {}", sheetId, userId)
        } catch (e: Exception) {
            if (e is IllegalStateException) {
                throw e
            }
            logger.error("Error acquiring sheet lock: {}", e.message, e)
            throw IllegalStateException("Error acquiring sheet lock: ${e.message}")
        }
    }
    
    /**
     * Releases the lock on the sheet.
     */
    private fun releaseSheetLock(sheetId: Long, userId: String) {
        logger.debug("Releasing lock on sheet: {} for user: {}", sheetId, userId)
        cellLockService.releaseSheetLock(sheetId, userId)
    }

    private fun buildDependencyGraph(cell: Cell): Pair<List<String>, Map<String, List<String>>> {
        val cellsToLock = mutableListOf<String>()
        cellsToLock.add(cell.id) // Always lock the cell being updated
        
        val allDependencies = mutableMapOf<String, List<String>>()
        
        if (CellUtils.isExpression(cell.data)) {
            val cellReferences = expressionParser.parse(cell.data)
            
            val dependencies = cellReferences.map { cellRef ->
                if (cellRef.contains(":")) {
                    // This is a range reference, keep as is
                    "${cell.sheetId}:$cellRef"
                } else {
                    // This is a single cell reference in A1 notation
                    val colLetter = cellRef.takeWhile { it.isLetter() }
                    val rowNum = cellRef.dropWhile { it.isLetter() }.toIntOrNull() ?: cell.row
                    "${cell.sheetId}:$rowNum:$colLetter"
                }
            }
            
            // Add all referenced cells to the list of cells to lock
            cellsToLock.addAll(dependencies)
            
            allDependencies[cell.id] = dependencies
            
            val cellDependencies = cellDependencyService.getDependenciesBySheetId(cell.sheetId)
            
            cellDependencies.groupBy { it.sourceCellId }
                .forEach { (sourceCellId, deps) ->
                    if (sourceCellId != cell.id) {
                        val targetIds = deps.map { it.targetCellId }
                        allDependencies[sourceCellId] = targetIds
                        
                        // If this cell depends on the cell we're updating, we need to lock it too
                        if (targetIds.contains(cell.id)) {
                            cellsToLock.add(sourceCellId)
                        }
                    }
                }
            
            // Check for circular dependencies before acquiring any locks
            val circularPath = circularDependencyDetector.detectCircularDependency(cell.id, allDependencies)
            if (circularPath != null) {
                val errorMsg = "Circular dependency detected: ${circularPath.joinToString(" -> ")}"
                logger.error(errorMsg)
                throw CircularDependencyException(listOf(errorMsg))
            }
        }
        
        return Pair(cellsToLock, allDependencies)
    }

    private fun topologicalSort(cellsToLock: List<String>, dependencies: Map<String, List<String>>): List<String> {
        // Create a map of cell to its in-degree (number of cells that depend on it)
        val inDegree = mutableMapOf<String, Int>()
        
        // Initialize in-degree for all cells to 0
        cellsToLock.forEach { cellId ->
            inDegree[cellId] = 0
        }
        
        // Calculate in-degree for each cell
        dependencies.forEach { (_, deps) ->
            deps.forEach { targetCellId ->
                if (cellsToLock.contains(targetCellId)) {
                    inDegree[targetCellId] = (inDegree[targetCellId] ?: 0) + 1
                }
            }
        }
        
        // Cells with lower in-degree should be locked first
        return cellsToLock.sortedBy { inDegree[it] ?: 0 }
    }
    
    /**
     * Acquires locks on all cells in the given order.
     * @return A list of cell IDs for which locks were successfully acquired
     * @throws IllegalStateException if any lock cannot be acquired
     */
    private fun acquireCellLocks(cellIds: List<String>, userId: String): List<String> {
        val acquiredLocks = mutableListOf<String>()
        
        for (cellId in cellIds) {
            try {
                if (!cellLockService.acquireLock(cellId, userId, LOCK_TIMEOUT_MS)) {
                    val lockOwner = cellLockService.getLockOwner(cellId)
                    val errorMsg = "Could not acquire lock on cell: $cellId. Current lock owner: $lockOwner"
                    logger.error(errorMsg)
                    
                    // Release any locks we've already acquired
                    releaseCellLocks(acquiredLocks, userId)
                    
                    throw IllegalStateException(errorMsg)
                }
                acquiredLocks.add(cellId)
                logger.debug("Acquired lock on cell: {} for user: {}", cellId, userId)
            } catch (e: Exception) {
                if (e is IllegalStateException) {
                    throw e
                }
                
                logger.error("Error acquiring lock for cell: {}: {}", cellId, e.message, e)
                
                // Release any locks we've already acquired
                releaseCellLocks(acquiredLocks, userId)
                
                throw IllegalStateException("Error acquiring lock on cell: $cellId: ${e.message}")
            }
        }
        
        return acquiredLocks
    }
    
    /**
     * Releases locks on all cells in the given list.
     */
    private fun releaseCellLocks(cellIds: List<String>, userId: String) {
        for (cellId in cellIds) {
            try {
                if (!cellLockService.releaseLock(cellId, userId)) {
                    val lockOwner = cellLockService.getLockOwner(cellId)
                    logger.warn("Failed to release lock for cell: {} by user: {}. Current lock owner: {}", 
                        cellId, userId, lockOwner)
                } else {
                    logger.debug("Released lock on cell: {} for user: {}", cellId, userId)
                }
            } catch (e: Exception) {
                logger.warn("Error releasing lock for cell: {} by user: {}: {}", 
                    cellId, userId, e.message, e)
                // Continue with other locks even if one fails
            }
        }
    }
    
    /**
     * Performs the actual cell update operation.
     */
    private fun performCellUpdate(cell: Cell, userId: String): Cell {
        try {
            val existingCell = getCell(cell.id)
            val now = Instant.now()
            
            val result = if (existingCell == null) {
                createNewCell(cell, now, userId)
            } else {
                updateExistingCell(existingCell, cell, now, userId)
            }
            
            return result
        } catch (e: Exception) {
            logger.error("Error updating cell: {} by user: {} - {}: {}", 
                cell.id, userId, e.javaClass.simpleName, e.message, e)
            throw e
        }
    }

    private fun createNewCell(cell: Cell, timestamp: Instant, userId: String): Cell {
        logger.info("Creating new cell: {}", cell.id)
        
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
        
        // Extract sheet ID from cell ID
        val sheetId = extractSheetId(id)
        
        // First, acquire sheet-level lock
        acquireSheetLock(sheetId, userId)
        
        try {
            // Check if the cell is used in any expressions
            checkCellDependencies(id)
            
            // Acquire cell-level lock
            acquireCellLock(id, userId)
            
            try {
                // Perform the actual deletion
                performCellDeletion(id)
                logger.info("Successfully deleted cell: {} by user: {}", id, userId)
            } catch (e: Exception) {
                logger.error("Error deleting cell: {}: {}", id, e.message, e)
                throw e
            } finally {
                // Release cell-level lock
                try {
                    releaseCellLock(id, userId)
                } catch (e: Exception) {
                    logger.warn("Error releasing cell lock: {}: {}", id, e.message, e)
                    // Don't rethrow as we don't want to mask the original exception
                }
            }
        } catch (e: IllegalStateException) {
            // Let IllegalStateException (lock conflicts or cell dependencies) propagate up to the controller
            logger.error("Error during cell deletion: {}", e.message)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error deleting cell: {}", e.message, e)
            throw e
        } finally {
            // Always release the sheet-level lock
            try {
                releaseSheetLock(sheetId, userId)
            } catch (e: Exception) {
                logger.warn("Error releasing sheet lock: {}", e.message, e)
                // Don't rethrow as we don't want to mask the original exception
            }
        }
    }
    
    /**
     * Extracts the sheet ID from a cell ID.
     */
    private fun extractSheetId(cellId: String): Long {
        return try {
            cellId.split(":")[0].toLong()
        } catch (e: Exception) {
            logger.error("Failed to extract sheet ID from cell ID: {}", cellId, e)
            throw IllegalArgumentException("Invalid cell ID format: $cellId")
        }
    }
    
    /**
     * Checks if the cell is used in any expressions.
     * @throws IllegalStateException if the cell is used in expressions
     */
    private fun checkCellDependencies(cellId: String) {
        val dependencies = cellDependencyService.getDependenciesByTargetCellId(cellId)
        if (dependencies.isNotEmpty()) {
            val dependentCellIds = dependencies.map { it.sourceCellId }
            throw IllegalStateException("Cannot delete cell as it is used in expressions in cells: ${dependentCellIds.joinToString(", ")}")
        }
    }
    
    /**
     * Acquires a lock on a cell.
     * @throws IllegalStateException if the lock cannot be acquired
     */
    private fun acquireCellLock(cellId: String, userId: String) {
        if (!cellLockService.acquireLock(cellId, userId, LOCK_TIMEOUT_MS)) {
            val lockOwner = cellLockService.getLockOwner(cellId)
            val errorMsg = "Could not acquire lock on cell: $cellId. Current lock owner: $lockOwner"
            logger.error(errorMsg)
            throw IllegalStateException(errorMsg)
        }
        logger.debug("Acquired lock on cell: {} for user: {}", cellId, userId)
    }
    
    /**
     * Releases the lock on a cell.
     */
    private fun releaseCellLock(cellId: String, userId: String) {
        logger.debug("Releasing lock on cell: {} for user: {}", cellId, userId)
        cellLockService.releaseLock(cellId, userId)
    }
    
    /**
     * Performs the actual cell deletion operation.
     */
    private fun performCellDeletion(cellId: String) {
        cellRedisRepository.deleteCell(cellId)
        cellAsyncService.deleteCell(cellId)
        cellDependencyService.deleteBySourceCellId(cellId)
    }
}
