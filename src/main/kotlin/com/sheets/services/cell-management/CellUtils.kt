package com.sheets.services.`cell-management`

import com.sheets.models.domain.Cell
import com.sheets.models.domain.CellDependency
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.exception.CircularDependencyException
import com.sheets.repositories.CellRedisRepository
import com.sheets.services.CellAsyncService
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Utility class for cell management operations
 */
object CellUtils {
    private val logger = LoggerFactory.getLogger(CellUtils::class.java)
    
    const val LOCK_TIMEOUT_MS = 30000L

    /**
     * Check if a string is an expression (starts with '=')
     */
    fun isExpression(data: String): Boolean {
        return data.startsWith("=")
    }

    /**
     * Create cell dependencies for a list of target cell IDs
     */
    fun createCellDependencies(
        sheetId: Long,
        sourceCellId: String,
        targetCellIds: List<String>,
        timestamp: Instant
    ): List<CellDependency> {
        return targetCellIds.map { targetCellId ->
            CellDependency(
                sheetId = sheetId,
                sourceCellId = sourceCellId,
                targetCellId = targetCellId,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        }
    }

    /**
     * Build a dependency map for circular dependency detection
     */
    fun buildDependencyMap(
        cellDependencyService: CellDependencyService,
        sheetId: Long,
        dependencies: List<String>
    ): Map<String, List<String>> {
        val dependencyMap = mutableMapOf<String, List<String>>()
        
        for (dependency in dependencies) {
            val dependentCellDependencies = cellDependencyService.getDependenciesBySourceCellId(dependency)
                .map { it.targetCellId }
            
            dependencyMap[dependency] = dependentCellDependencies
        }
        
        return dependencyMap
    }
    
    fun acquireSheetLock(cellLockService: CellLockService, sheetId: Long, userId: String) {
        try {
            if (!cellLockService.acquireSheetLock(sheetId, userId, LOCK_TIMEOUT_MS)) {
                val lockOwner = cellLockService.getSheetLockOwner(sheetId)
                val errorMsg = "Could not acquire lock on sheet: $sheetId. Current lock owner: $lockOwner"
                throw IllegalStateException(errorMsg)
            }
        } catch (e: Exception) {
            if (e is IllegalStateException) {
                throw e
            }
            throw IllegalStateException("Error acquiring sheet lock: ${e.message}")
        }
    }
    
    fun releaseSheetLock(cellLockService: CellLockService, sheetId: Long, userId: String) {
        cellLockService.releaseSheetLock(sheetId, userId)
    }

    fun buildDependencyGraph(cell: Cell, expressionParser: ExpressionParser, 
                            cellDependencyService: CellDependencyService,
                            circularDependencyDetector: CircularDependencyDetector): Pair<List<String>, Map<String, List<String>>> {
        val cellsToLock = mutableListOf<String>()
        cellsToLock.add(cell.id)
        
        val allDependencies = mutableMapOf<String, List<String>>()
        
        if (isExpression(cell.data)) {
            val cellReferences = expressionParser.parse(cell.data)
            
            val dependencies = cellReferences.map { cellRef ->
                if (cellRef.contains(":")) {
                    "${cell.sheetId}:$cellRef"
                } else {
                    val colLetter = cellRef.takeWhile { it.isLetter() }
                    val rowNum = cellRef.dropWhile { it.isLetter() }.toIntOrNull() ?: cell.row
                    "${cell.sheetId}:$rowNum:$colLetter"
                }
            }
            
            cellsToLock.addAll(dependencies)
            
            allDependencies[cell.id] = dependencies
            
            val cellDependencies = cellDependencyService.getDependenciesBySheetId(cell.sheetId)
            
            cellDependencies.groupBy { it.sourceCellId }
                .forEach { (sourceCellId, deps) ->
                    if (sourceCellId != cell.id) {
                        val targetIds = deps.map { it.targetCellId }
                        allDependencies[sourceCellId] = targetIds
                        
                        if (targetIds.contains(cell.id)) {
                            cellsToLock.add(sourceCellId)
                        }
                    }
                }
            
            val circularPath = circularDependencyDetector.detectCircularDependency(cell.id, allDependencies)
            if (circularPath != null) {
                val errorMsg = "Circular dependency detected: ${circularPath.joinToString(" -> ")}"
                throw CircularDependencyException(listOf(errorMsg))
            }
        }
        
        return Pair(cellsToLock, allDependencies)
    }

    fun topologicalSort(cellsToLock: List<String>, dependencies: Map<String, List<String>>): List<String> {
        val inDegree = mutableMapOf<String, Int>()
        
        cellsToLock.forEach { cellId ->
            inDegree[cellId] = 0
        }
        
        dependencies.forEach { (_, deps) ->
            deps.forEach { targetCellId ->
                if (cellsToLock.contains(targetCellId)) {
                    inDegree[targetCellId] = (inDegree[targetCellId] ?: 0) + 1
                }
            }
        }
        
        return cellsToLock.sortedBy { inDegree[it] ?: 0 }
    }
    
    fun acquireCellLocks(cellLockService: CellLockService, cellIds: List<String>, userId: String): List<String> {
        val acquiredLocks = mutableListOf<String>()
        
        for (cellId in cellIds) {
            try {
                if (!cellLockService.acquireLock(cellId, userId, LOCK_TIMEOUT_MS)) {
                    val lockOwner = cellLockService.getLockOwner(cellId)
                    val errorMsg = "Could not acquire lock on cell: $cellId. Current lock owner: $lockOwner"
                    
                    releaseCellLocks(cellLockService, acquiredLocks, userId)
                    
                    throw IllegalStateException(errorMsg)
                }
                acquiredLocks.add(cellId)
            } catch (e: Exception) {
                if (e is IllegalStateException) {
                    throw e
                }
                
                releaseCellLocks(cellLockService, acquiredLocks, userId)
                
                throw IllegalStateException("Error acquiring lock on cell: $cellId: ${e.message}")
            }
        }
        
        return acquiredLocks
    }
    
    fun releaseCellLocks(cellLockService: CellLockService, cellIds: List<String>, userId: String) {
        for (cellId in cellIds) {
            try {
                cellLockService.releaseLock(cellId, userId)
            } catch (e: Exception) {
                // Continue with other locks even if one fails
            }
        }
    }
    
    fun acquireCellLock(cellLockService: CellLockService, cellId: String, userId: String) {
        if (!cellLockService.acquireLock(cellId, userId, LOCK_TIMEOUT_MS)) {
            val lockOwner = cellLockService.getLockOwner(cellId)
            val errorMsg = "Could not acquire lock on cell: $cellId. Current lock owner: $lockOwner"
            throw IllegalStateException(errorMsg)
        }
    }
    
    fun releaseCellLock(cellLockService: CellLockService, cellId: String, userId: String) {
        cellLockService.releaseLock(cellId, userId)
    }
    
    fun performCellDeletion(cellRedisRepository: CellRedisRepository, cellAsyncService: CellAsyncService, 
                           cellDependencyService: CellDependencyService, cellId: String) {
        cellRedisRepository.deleteCell(cellId)
        cellAsyncService.deleteCell(cellId)
        cellDependencyService.deleteBySourceCellId(cellId)
    }
    
    fun extractSheetId(cellId: String): Long {
        return try {
            cellId.split(":")[0].toLong()
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid cell ID format: $cellId")
        }
    }
    
    fun checkCellDependencies(cellDependencyService: CellDependencyService, cellId: String) {
        val dependencies = cellDependencyService.getDependenciesByTargetCellId(cellId)
        if (dependencies.isNotEmpty()) {
            val dependentCellIds = dependencies.map { it.sourceCellId }
            throw IllegalStateException("Cannot delete cell as it is used in expressions in cells: ${dependentCellIds.joinToString(", ")}")
        }
    }
    

    /**
     * Execute an operation with cell locks, handling all lock acquisition, dependency checking, and error handling
     * Returns immediately after Redis is updated, with MongoDB updates happening asynchronously
     */
    fun executeWithCellLocks(
        cell: Cell,
        userId: String,
        cellLockService: CellLockService,
        cellDependencyService: CellDependencyService,
        cellRedisRepository: CellRedisRepository,
        cellAsyncService: CellAsyncService,
        expressionParser: ExpressionParser,
        circularDependencyDetector: CircularDependencyDetector,
        operation: () -> Cell
    ): Cell {
        logger.debug("Executing operation with cell locks for cell: {} by user: {}", cell.id, userId)
        
        // First, acquire sheet-level lock
        acquireSheetLock(cellLockService, cell.sheetId, userId)
        
        try {
            // Build dependency graph and check for circular dependencies
            val (cellsToLock, allDependencies) = buildDependencyGraph(
                cell, 
                expressionParser, 
                cellDependencyService, 
                circularDependencyDetector
            )
            
            // Perform topological sort to determine lock acquisition order
            val sortedCellsToLock = topologicalSort(cellsToLock, allDependencies)
            logger.debug("Topologically sorted cells to lock: {}", sortedCellsToLock)
            
            // Acquire locks in topological order
            val acquiredLocks = acquireCellLocks(cellLockService, sortedCellsToLock, userId)
            
            try {
                // Perform the actual operation (cell update)
                val updatedCell = operation()
                
                // Return immediately after the cell is saved to Redis
                // MongoDB updates will happen asynchronously
                return updatedCell
            } finally {
                // Release all acquired cell locks
                releaseCellLocks(cellLockService, acquiredLocks, userId)
            }
        } catch (e: CircularDependencyException) {
            logger.error("Circular dependency detected: {}", e.message)
            throw e
        } catch (e: IllegalStateException) {
            // Let IllegalStateException (lock conflicts) propagate up to the controller
            logger.error("Lock acquisition failed: {}", e.message)
            throw e
        } catch (e: Exception) {
            logger.error("Unexpected error during cell operation: {}", e.message, e)
            throw e
        } finally {
            // Always release the sheet-level lock
            try {
                releaseSheetLock(cellLockService, cell.sheetId, userId)
            } catch (e: Exception) {
                logger.warn("Error releasing sheet lock: {}", e.message, e)
                // Don't rethrow as we don't want to mask the original exception
            }
        }
    }

    /**
     * Execute cell deletion with proper lock management and error handling
     * Returns immediately after Redis is updated, with MongoDB updates happening asynchronously
     */
    fun executeWithCellDeletion(
        cellId: String,
        userId: String,
        cellLockService: CellLockService,
        cellDependencyService: CellDependencyService,
        cellRedisRepository: CellRedisRepository,
        cellAsyncService: CellAsyncService
    ) {
        logger.debug("Executing cell deletion for cell: {} by user: {}", cellId, userId)
        
        // Get the cell to determine the sheet ID
        val cell = cellRedisRepository.getCell(cellId)
        
        if (cell == null) {
            logger.warn("Cell not found for deletion: {}", cellId)
            return
        }
        
        // First, acquire sheet-level lock
        acquireSheetLock(cellLockService, cell.sheetId, userId)
        
        try {
            // Find all cells that depend on this cell
            val dependentCells = cellDependencyService.getDependenciesByTargetCellId(cellId)
                .map { it.sourceCellId }
                .distinct()
            
            // Add the cell itself to the list of cells to lock
            val cellsToLock = mutableListOf(cellId)
            cellsToLock.addAll(dependentCells)
            
            // Acquire locks for all cells
            val acquiredLocks = acquireCellLocks(cellLockService, cellsToLock, userId)
            
            try {
                // Delete the cell from Redis immediately
                logger.debug("Deleting cell from Redis: {}", cellId)
                cellRedisRepository.deleteCell(cellId)
                
                // Delete dependencies asynchronously
                logger.debug("Deleting cell dependencies asynchronously: {}", cellId)
                cellAsyncService.updateDependencies(cellId, emptyList(), cell.sheetId, Instant.now())
                
                // Delete from MongoDB asynchronously
                logger.debug("Deleting cell from MongoDB asynchronously: {}", cellId)
                cellAsyncService.deleteCell(cellId)
                
                // Update dependent cells asynchronously
                if (dependentCells.isNotEmpty()) {
                    logger.debug("Updating {} dependent cells asynchronously", dependentCells.size)
                    dependentCells.forEach { dependentCellId ->
                        cellAsyncService.updateDependentCells(dependentCellId, userId)
                    }
                }
                
                logger.info("Cell deleted successfully: {}", cellId)
            } finally {
                // Release all acquired cell locks
                releaseCellLocks(cellLockService, acquiredLocks, userId)
            }
        } catch (e: Exception) {
            logger.error("Error deleting cell: {}: {}", cellId, e.message, e)
            throw e
        } finally {
            // Always release the sheet-level lock
            try {
                releaseSheetLock(cellLockService, cell.sheetId, userId)
            } catch (e: Exception) {
                logger.warn("Error releasing sheet lock: {}", e.message, e)
                // Don't rethrow as we don't want to mask the original exception
            }
        }
    }
}
