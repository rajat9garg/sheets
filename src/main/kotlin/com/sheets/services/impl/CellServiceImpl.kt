package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.models.domain.CellDependency
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRedisRepository
import com.sheets.repositories.CellRepository
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.CellService
import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.exception.CircularDependencyException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CellServiceImpl(
    private val cellRepository: CellRepository,
    private val cellRedisRepository: CellRedisRepository,
    private val cellDependencyService: CellDependencyService,
    private val cellLockService: CellLockService,
    private val expressionParser: ExpressionParser,
    private val expressionEvaluator: ExpressionEvaluator,
    private val cellAsyncService: CellAsyncServiceImpl,
    private val circularDependencyDetector: CircularDependencyDetector
) : CellService {

    private val logger = LoggerFactory.getLogger(CellServiceImpl::class.java)

    /**
     * Get a cell by ID, first checking Redis cache, then falling back to MongoDB
     */
    override fun getCell(id: String): Cell? {
        logger.debug("Getting cell: {}", id)
        
        // First try to get from Redis
        val cachedCell = cellRedisRepository.getCell(id)
        if (cachedCell != null) {
            logger.debug("Cell found in Redis cache: {}", id)
            return cachedCell
        }
        
        // If not in Redis, get from MongoDB and cache it
        logger.debug("Cell not found in Redis cache, checking MongoDB: {}", id)
        val cell = cellRepository.findById(id)
        
        if (cell != null) {
            logger.debug("Cell found in MongoDB, caching in Redis: {}", id)
            cellRedisRepository.saveCell(cell)
        } else {
            logger.debug("Cell not found in MongoDB: {}", id)
        }
        
        return cell
    }

    /**
     * Get all cells for a sheet, first checking Redis cache, then falling back to MongoDB
     */
    override fun getCellsBySheetId(sheetId: Long): List<Cell> {
        logger.debug("Getting all cells for sheet: {}", sheetId)
        
        // First try to get from Redis
        val cachedCells = cellRedisRepository.getCellsBySheetId(sheetId)
        if (cachedCells.isNotEmpty()) {
            logger.debug("Found {} cells in Redis cache for sheet: {}", cachedCells.size, sheetId)
            return cachedCells
        }
        
        // If not in Redis or incomplete, get from MongoDB and cache them
        logger.debug("Cells not found in Redis cache or incomplete, checking MongoDB for sheet: {}", sheetId)
        val cells = cellRepository.findBySheetId(sheetId)
        
        if (cells.isNotEmpty()) {
            logger.debug("Found {} cells in MongoDB for sheet: {}, caching in Redis", cells.size, sheetId)
            cells.forEach { cellRedisRepository.saveCell(it) }
        } else {
            logger.debug("No cells found in MongoDB for sheet: {}", sheetId)
        }
        
        return cells
    }

    override fun updateCell(cell: Cell, userId: String): Cell {
        logger.info("Updating cell: {} by user: {}", cell.id, userId)
        
        // Check if cell exists in Redis or MongoDB
        val existingCell = getCell(cell.id)
        val now = Instant.now()
        
        // If cell doesn't exist, create a new one
        if (existingCell == null) {
            logger.info("Cell not found: {}. Creating new cell.", cell.id)
            // Process the expression and get dependencies
            val (dataType, evaluatedValue, dependencies) = processExpression(cell.data, cell.sheetId)
            
            val newCell = cell.copy(
                dataType = dataType,
                evaluatedValue = evaluatedValue,
                createdAt = now,
                updatedAt = now
            )
            
            // For primitive data types, just store and update
            if (dataType == DataType.PRIMITIVE) {
                // Save to Redis immediately
                logger.debug("Saving new cell to Redis: {}", cell.id)
                cellRedisRepository.saveCell(newCell)
                
                // Save to MongoDB asynchronously
                logger.debug("Saving new cell to MongoDB asynchronously: {}", cell.id)
                cellAsyncService.saveCell(newCell)
                
                logger.info("Successfully created cell: {} by user: {}", cell.id, userId)
                return newCell
            }
            
            // For expressions, handle dependencies
            if (dependencies.isNotEmpty()) {
                logger.debug("Creating {} new dependencies for cell: {}", dependencies.size, cell.id)
                val cellDependencies = dependencies.map { targetCellId ->
                    CellDependency(
                        sheetId = cell.sheetId,
                        sourceCellId = cell.id,
                        targetCellId = targetCellId,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                cellDependencyService.createDependencies(cellDependencies)
            }
            
            // Save to Redis immediately
            logger.debug("Saving new cell to Redis: {}", cell.id)
            cellRedisRepository.saveCell(newCell)
            
            // Save to MongoDB asynchronously
            logger.debug("Saving new cell to MongoDB asynchronously: {}", cell.id)
            cellAsyncService.saveCell(newCell)
            
            logger.info("Successfully created cell: {} by user: {}", cell.id, userId)
            return newCell
        }
        
        try {
            // Process the expression and get dependencies
            logger.info("Processing expression for cell: {}", cell.id)
            val (dataType, evaluatedValue, dependencies) = processExpression(cell.data, cell.sheetId)
            logger.info("Expression processed. Data type: {}, Dependencies: {}", dataType, dependencies)
            
            // For primitive data types, just update without locking
            if (dataType == DataType.PRIMITIVE && existingCell.dataType == DataType.PRIMITIVE) {
                logger.debug("Updating primitive cell data for cell: {}", cell.id)
                val updatedCell = existingCell.copy(
                    data = cell.data,
                    dataType = dataType,
                    evaluatedValue = evaluatedValue,
                    updatedAt = now
                )
                
                // Save to Redis immediately
                logger.debug("Saving updated cell to Redis: {}", cell.id)
                cellRedisRepository.saveCell(updatedCell)
                
                // Save to MongoDB asynchronously
                logger.debug("Saving updated cell to MongoDB asynchronously: {}", cell.id)
                cellAsyncService.saveCell(updatedCell)
                
                logger.info("Successfully updated primitive cell: {} by user: {}", cell.id, userId)
                return updatedCell
            }
            
            // Acquire locks on the cell and all its dependencies
            val allCellsToLock = mutableListOf(cell.id)
            
            // Get all cells that depend on this cell
            logger.info("Getting dependent cells for cell: {}", cell.id)
            val dependentCells = cellDependencyService.getDependenciesByTargetCellId(cell.id)
                .map { it.sourceCellId }
            logger.info("Found {} dependent cells for cell: {}", dependentCells.size, cell.id)
            
            allCellsToLock.addAll(dependentCells)
            
            // Add new dependencies to the lock list
            allCellsToLock.addAll(dependencies)
            
            // Try to acquire locks on all cells
            // TODO take lock on sheet for taking lock on the cell
            logger.debug("Attempting to acquire locks on {} cells", allCellsToLock.size)
            val lockedCells = mutableListOf<String>()
            try {
                for (cellId in allCellsToLock) {
                    if (!cellLockService.acquireLock(cellId, userId)) {
                        // If we can't acquire a lock, release all locks we've acquired so far
                        logger.warn("Failed to acquire lock on cell: {} for user: {}", cellId, userId)
                        for (lockedCellId in lockedCells) {
                            logger.debug("Releasing lock on cell: {} for user: {}", lockedCellId, userId)
                            cellLockService.releaseLock(lockedCellId, userId)
                        }
                        throw IllegalStateException("Could not acquire lock on cell: $cellId")
                    }
                    logger.debug("Acquired lock on cell: {} for user: {}", cellId, userId)
                    lockedCells.add(cellId)
                }
                
                // Update the cell
                logger.debug("Updating cell data for cell: {}", cell.id)
                val updatedCell = existingCell.copy(
                    data = cell.data,
                    dataType = dataType,
                    evaluatedValue = evaluatedValue,
                    updatedAt = now
                )
                
                // Update dependencies
                logger.debug("Deleting existing dependencies for cell: {}", cell.id)
                cellDependencyService.deleteBySourceCellId(cell.id)
                
                if (dependencies.isNotEmpty()) {
                    logger.debug("Creating {} new dependencies for cell: {}", dependencies.size, cell.id)
                    val cellDependencies = dependencies.map { targetCellId ->
                        CellDependency(
                            sheetId = cell.sheetId,
                            sourceCellId = cell.id,
                            targetCellId = targetCellId,
                            createdAt = now,
                            updatedAt = now
                        )
                    }
                    cellDependencyService.createDependencies(cellDependencies)
                }
                
                // Save the updated cell to Redis
                logger.debug("Saving updated cell to Redis: {}", cell.id)
                cellRedisRepository.saveCell(updatedCell)
                
                // Save to MongoDB asynchronously
                logger.debug("Saving updated cell to MongoDB asynchronously: {}", cell.id)
                cellAsyncService.saveCell(updatedCell)
                
                // Update dependent cells synchronously
                if (dependentCells.isNotEmpty()) {
                    logger.info("Updating dependent cells for cell: {}", cell.id)
                    updateDependentCellsSync(cell.id, userId)
                }
                
                logger.info("Successfully updated cell: {} by user: {}", cell.id, userId)
                return updatedCell
            } finally {
                // Release all locks
                logger.debug("Releasing all locks acquired for updating cell: {}", cell.id)
                for (lockedCellId in lockedCells) {
                    logger.debug("Releasing lock on cell: {} for user: {}", lockedCellId, userId)
                    cellLockService.releaseLock(lockedCellId, userId)
                }
            }
        } catch (e: Exception) {
            logger.error("Error updating cell: {} by user: {} - {}: {}", cell.id, userId, e.javaClass.simpleName, e.message, e)
            throw e
        }
    }

    /**
     * Delete a cell
     */
    override fun deleteCell(id: String, userId: String) {
        logger.info("Deleting cell: {} by user: {}", id, userId)
        
        try {
            // Check if the cell exists
            val cell = getCell(id)
            if (cell == null) {
                logger.warn("Cell not found for deletion: {}", id)
                return
            }
            
            // Try to acquire a lock on the cell
            if (!cellLockService.acquireLock(id, userId)) {
                logger.warn("Failed to acquire lock on cell: {} for user: {}", id, userId)
                throw IllegalStateException("Could not acquire lock on cell: $id")
            }
            
            try {
                // Delete dependencies
                logger.debug("Deleting dependencies for cell: {}", id)
                cellDependencyService.deleteBySourceCellId(id)
                
                // Delete from Redis
                logger.debug("Deleting cell from Redis: {}", id)
                cellRedisRepository.deleteCell(id)
                
                // Delete from MongoDB asynchronously
                logger.debug("Deleting cell from MongoDB asynchronously: {}", id)
                cellAsyncService.deleteCell(id)
                
                logger.info("Successfully deleted cell: {} by user: {}", id, userId)
            } finally {
                // Release the lock
                logger.debug("Releasing lock on cell: {} for user: {}", id, userId)
                cellLockService.releaseLock(id, userId)
            }
        } catch (e: Exception) {
            logger.error("Error deleting cell: {} by user: {} - {}: {}", id, userId, e.javaClass.simpleName, e.message)
            throw e
        }
    }

    /**
     * Process an expression and return its data type, evaluated value, and dependencies
     */
    private fun processExpression(data: String, sheetId: Long): Triple<DataType, String, List<String>> {
        if (data.isBlank()) {
            return Triple(DataType.PRIMITIVE, "", emptyList())
        }
        
        if (!expressionParser.isExpression(data)) {
            return Triple(DataType.PRIMITIVE, data, emptyList())
        }
        
        // Parse the expression
        logger.debug("Parsing expression: {}", data)
        val cellReferences = expressionParser.parse(data)
        
        // Get the dependencies
        logger.debug("Getting dependencies for expression: {}", cellReferences)
        val dependencies = cellReferences.map { "$sheetId:$it" }
        
        // Check for circular dependencies
        logger.debug("Checking for circular dependencies")
        val dependencyMap = buildDependencyMap(sheetId, dependencies)
        val cellId = "$sheetId:${data}" // This is not a valid cell ID, but we need something for circular dependency detection
        val cycle = circularDependencyDetector.detectCircularDependency(cellId, dependencyMap)
        
        if (cycle != null) {
            val pathStr = cycle.joinToString(" -> ")
            logger.warn("Circular dependency detected: {}", pathStr)
            throw CircularDependencyException(listOf(pathStr))
        }
        
        // Evaluate the expression
        logger.debug("Evaluating expression: {}", cellReferences)
        val context = buildEvaluationContext(cellReferences, sheetId)
        val evaluatedValue = expressionEvaluator.evaluate(data, context)
        
        return Triple(DataType.EXPRESSION, evaluatedValue, dependencies)
    }

    /**
     * Build a map of dependencies for circular dependency detection
     */
    private fun buildDependencyMap(sheetId: Long, dependencies: List<String>): Map<String, List<String>> {
        val dependencyMap = mutableMapOf<String, List<String>>()
        
        for (dependency in dependencies) {
            val dependentCellDependencies = cellDependencyService.getDependenciesBySourceCellId(dependency)
                .map { it.targetCellId }
            
            dependencyMap[dependency] = dependentCellDependencies
        }
        
        return dependencyMap
    }
    
    /**
     * Build evaluation context for expression evaluation
     */
    private fun buildEvaluationContext(cellReferences: List<String>, sheetId: Long): Map<String, String> {
        val context = mutableMapOf<String, String>()
        
        for (cellRef in cellReferences) {
            val cellId = "$sheetId:$cellRef"
            val cell = getCell(cellId)
            
            if (cell != null) {
                context[cellRef] = cell.evaluatedValue
            } else {
                context[cellRef] = "#REF!"
            }
        }
        
        return context
    }

    /**
     * Evaluate an expression and update dependencies
     */
    fun evaluateExpression(cellId: String, expression: String, userId: String): String {
        logger.info("Evaluating expression for cell: {}", cellId)
        
        val parts = cellId.split(":")
        if (parts.size < 3) {
            throw IllegalArgumentException("Invalid cell ID format: $cellId")
        }
        
        val sheetId = parts[0].toLong()
        val now = Instant.now()
        
        try {
            // Process the expression
            val (dataType, evaluatedValue, dependencies) = processExpression(expression, sheetId)
            
            // Update dependencies in the database
            logger.debug("Updating dependencies for cell: {}", cellId)
            cellDependencyService.deleteBySourceCellId(cellId)
            
            if (dependencies.isNotEmpty()) {
                logger.debug("Creating {} new dependencies for cell: {}", dependencies.size, cellId)
                val cellDependencies = dependencies.map { targetCellId ->
                    CellDependency(
                        sheetId = sheetId,
                        sourceCellId = cellId,
                        targetCellId = targetCellId,
                        createdAt = now,
                        updatedAt = now
                    )
                }
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
    private fun updateDependentCellsSync(cellId: String, userId: String) {
        logger.info("Updating dependent cells synchronously for cell: {}", cellId)
        
        try {
            // Get cells that depend on this cell
            val dependentCellIds = cellDependencyService.getDependenciesByTargetCellId(cellId)
                .map { it.sourceCellId }
            
            if (dependentCellIds.isEmpty()) {
                logger.debug("No dependent cells found for cell: {}", cellId)
                return
            }
            
            logger.info("Found {} dependent cells for cell: {}", dependentCellIds.size, cellId)
            
            // Update each dependent cell
            for (dependentCellId in dependentCellIds) {
                logger.debug("Updating dependent cell: {}", dependentCellId)
                
                // Get the dependent cell
                val dependentCell = getCell(dependentCellId)
                if (dependentCell == null) {
                    logger.warn("Dependent cell not found: {}", dependentCellId)
                    continue
                }
                
                // Only update if it's an expression
                if (dependentCell.dataType != DataType.EXPRESSION) {
                    logger.debug("Dependent cell is not an expression, skipping: {}", dependentCellId)
                    continue
                }
                
                try {
                    // Re-evaluate the expression
                    val evaluatedValue = evaluateExpression(dependentCellId, dependentCell.data, userId)
                    
                    // Update the cell
                    val updatedCell = dependentCell.copy(
                        evaluatedValue = evaluatedValue,
                        updatedAt = Instant.now()
                    )
                    
                    // Save to Redis
                    logger.debug("Saving updated dependent cell to Redis: {}", dependentCellId)
                    cellRedisRepository.saveCell(updatedCell)
                    
                    // Save to MongoDB asynchronously
                    logger.debug("Saving updated dependent cell to MongoDB asynchronously: {}", dependentCellId)
                    cellAsyncService.saveCell(updatedCell)
                    
                    logger.info("Successfully updated dependent cell: {}", dependentCellId)
                } catch (e: Exception) {
                    logger.error("Error updating dependent cell: {} - {}: {}", dependentCellId, e.javaClass.simpleName, e.message, e)
                    // Continue with other dependent cells
                }
            }
        } catch (e: Exception) {
            logger.error("Error updating dependent cells for cell: {} - {}: {}", cellId, e.javaClass.simpleName, e.message, e)
            throw e
        }
    }

    /**
     * Update dependent cells asynchronously
     */
    private fun updateDependentCellsAsync(cellId: String, userId: String) {
        logger.info("Updating dependent cells asynchronously for cell: {}", cellId)
        
        try {
            // Get cells that depend on this cell
            val dependentCellIds = cellDependencyService.getDependenciesByTargetCellId(cellId)
                .map { it.sourceCellId }
            
            if (dependentCellIds.isEmpty()) {
                logger.debug("No dependent cells found for cell: {}", cellId)
                return
            }
            
            logger.info("Found {} dependent cells for cell: {}", dependentCellIds.size, cellId)
            
            // Update each dependent cell
            for (dependentCellId in dependentCellIds) {
                logger.debug("Updating dependent cell: {}", dependentCellId)
                
                // Get the dependent cell
                val dependentCell = getCell(dependentCellId)
                if (dependentCell == null) {
                    logger.warn("Dependent cell not found: {}", dependentCellId)
                    continue
                }
                
                // Only update if it's an expression
                if (dependentCell.dataType != DataType.EXPRESSION) {
                    logger.debug("Dependent cell is not an expression, skipping: {}", dependentCellId)
                    continue
                }
                
                try {
                    // Re-evaluate the expression
                    val evaluatedValue = evaluateExpression(dependentCellId, dependentCell.data, userId)
                    
                    // Update the cell
                    val updatedCell = dependentCell.copy(
                        evaluatedValue = evaluatedValue,
                        updatedAt = Instant.now()
                    )
                    
                    // Save to Redis
                    logger.debug("Saving updated dependent cell to Redis: {}", dependentCellId)
                    cellRedisRepository.saveCell(updatedCell)
                    
                    // Save to MongoDB asynchronously
                    logger.debug("Saving updated dependent cell to MongoDB asynchronously: {}", dependentCellId)
                    cellAsyncService.saveCell(updatedCell)
                    
                    logger.info("Successfully updated dependent cell: {}", dependentCellId)
                    
                    // Recursively update cells that depend on this cell
                    updateDependentCellsAsync(dependentCellId, userId)
                } catch (e: Exception) {
                    logger.error("Error updating dependent cell: {} - {}: {}", dependentCellId, e.javaClass.simpleName, e.message, e)
                    // Continue with other dependent cells
                }
            }
        } catch (e: Exception) {
            logger.error("Error updating dependent cells for cell: {} - {}: {}", cellId, e.javaClass.simpleName, e.message, e)
            // Don't rethrow in async method
        }
    }
}
