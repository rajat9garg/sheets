package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.models.domain.CellDependency
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellDependencyRepository
import com.sheets.repositories.CellDependencyRedisRepository
import com.sheets.repositories.CellRedisRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CellDependencyServiceImpl(
    private val cellDependencyRepository: CellDependencyRepository,
    private val cellDependencyRedisRepository: CellDependencyRedisRepository,
    private val circularDependencyDetector: CircularDependencyDetector,
    private val cellRedisRepository: CellRedisRepository,
    private val cellLockService: CellLockService,
    private val cellAsyncService: CellAsyncService,
    private val expressionParser: ExpressionParser,
    private val expressionEvaluator: ExpressionEvaluator
) : CellDependencyService {
    
    private val logger = LoggerFactory.getLogger(CellDependencyServiceImpl::class.java)
    
    override fun getDependenciesBySourceCellId(sourceCellId: String): List<CellDependency> {
        // Try to get from Redis first
        val cachedDependencies = cellDependencyRedisRepository.getDependenciesBySourceCellId(sourceCellId)
        if (cachedDependencies.isNotEmpty()) {
            logger.debug("Found {} dependencies for source cell {} in Redis cache", cachedDependencies.size, sourceCellId)
            return cachedDependencies
        }
        
        // If not in Redis, get from MongoDB and cache in Redis
        logger.debug("Dependencies for source cell {} not found in Redis cache, fetching from MongoDB", sourceCellId)
        val dependencies = cellDependencyRepository.findBySourceCellId(sourceCellId)
        if (dependencies.isNotEmpty()) {
            logger.debug("Caching {} dependencies for source cell {} in Redis", dependencies.size, sourceCellId)
            cellDependencyRedisRepository.saveDependencies(dependencies)
        }
        return dependencies
    }
    
    override fun getDependenciesByTargetCellId(targetCellId: String): List<CellDependency> {
        // Try to get from Redis first
        val cachedDependencies = cellDependencyRedisRepository.getDependenciesByTargetCellId(targetCellId)
        if (cachedDependencies.isNotEmpty()) {
            logger.debug("Found {} dependencies for target cell {} in Redis cache", cachedDependencies.size, targetCellId)
            return cachedDependencies
        }
        
        // If not in Redis, get from MongoDB and cache in Redis
        logger.debug("Dependencies for target cell {} not found in Redis cache, fetching from MongoDB", targetCellId)
        val dependencies = cellDependencyRepository.findByTargetCellId(targetCellId)
        if (dependencies.isNotEmpty()) {
            logger.debug("Caching {} dependencies for target cell {} in Redis", dependencies.size, targetCellId)
            cellDependencyRedisRepository.saveDependencies(dependencies)
        }
        return dependencies
    }
    
    override fun getDependencyBySourceAndTargetCellId(sourceCellId: String, targetCellId: String): CellDependency? {
        // Try to get from Redis first
        val cachedDependency = cellDependencyRedisRepository.getDependency(sourceCellId, targetCellId)
        if (cachedDependency != null) {
            logger.debug("Found dependency {}:{} in Redis cache", sourceCellId, targetCellId)
            return cachedDependency
        }
        
        // If not in Redis, get from MongoDB and cache in Redis
        logger.debug("Dependency {}:{} not found in Redis cache, fetching from MongoDB", sourceCellId, targetCellId)
        val dependency = cellDependencyRepository.findBySourceCellIdAndTargetCellId(sourceCellId, targetCellId)
        if (dependency != null) {
            logger.debug("Caching dependency {}:{} in Redis", sourceCellId, targetCellId)
            cellDependencyRedisRepository.saveDependency(dependency)
        }
        return dependency
    }
    
    override fun getDependenciesBySheetId(sheetId: Long): List<CellDependency> {
        // Try to get from Redis first
        val cachedDependencies = cellDependencyRedisRepository.getDependenciesBySheetId(sheetId)
        if (cachedDependencies.isNotEmpty()) {
            logger.debug("Found {} dependencies for sheet {} in Redis cache", cachedDependencies.size, sheetId)
            return cachedDependencies
        }
        
        // If not in Redis, get from MongoDB and cache in Redis
        logger.debug("Dependencies for sheet {} not found in Redis cache, fetching from MongoDB", sheetId)
        val dependencies = cellDependencyRepository.findBySheetId(sheetId)
        if (dependencies.isNotEmpty()) {
            logger.debug("Caching {} dependencies for sheet {} in Redis", dependencies.size, sheetId)
            cellDependencyRedisRepository.saveDependencies(dependencies)
        }
        return dependencies
    }
    
    override fun createDependency(dependency: CellDependency): CellDependency {
        logger.info("Creating dependency: {} -> {}", dependency.sourceCellId, dependency.targetCellId)
        
        // Save to MongoDB
        val savedDependency = cellDependencyRepository.save(dependency)
        
        // Cache in Redis
        cellDependencyRedisRepository.saveDependency(savedDependency)
        
        return savedDependency
    }
    
    override fun createDependencies(dependencies: List<CellDependency>): List<CellDependency> {
        if (dependencies.isEmpty()) {
            return emptyList()
        }
        
        logger.info("Creating {} dependencies", dependencies.size)
        
        // Save to MongoDB
        val savedDependencies = cellDependencyRepository.saveAll(dependencies)
        
        // Cache in Redis
        cellDependencyRedisRepository.saveDependencies(savedDependencies)
        
        return savedDependencies
    }
    
    override fun deleteBySourceCellId(sourceCellId: String): Int {
        logger.info("Deleting dependencies by source cell ID: {}", sourceCellId)
        
        // Delete from Redis
        cellDependencyRedisRepository.deleteBySourceCellId(sourceCellId)
        
        // Delete from MongoDB
        return cellDependencyRepository.deleteBySourceCellId(sourceCellId)
    }
    
    override fun deleteByTargetCellId(targetCellId: String): Int {
        logger.info("Deleting dependencies by target cell ID: {}", targetCellId)
        
        // Delete from Redis
        cellDependencyRedisRepository.deleteByTargetCellId(targetCellId)
        
        // Delete from MongoDB
        return cellDependencyRepository.deleteByTargetCellId(targetCellId)
    }
    
    override fun deleteBySheetId(sheetId: Long): Int {
        logger.info("Deleting dependencies by sheet ID: {}", sheetId)
        
        // Delete from Redis
        cellDependencyRedisRepository.deleteBySheetId(sheetId)
        
        // Delete from MongoDB
        return cellDependencyRepository.deleteBySheetId(sheetId)
    }
    
    override fun detectCircularDependency(cellId: String): List<String>? {
        val allDependencies = getDependenciesBySheetId(extractSheetId(cellId))
        val dependencyMap = buildDependencyMap(allDependencies)
        return circularDependencyDetector.detectCircularDependency(cellId, dependencyMap)
    }
    
    private fun buildDependencyMap(dependencies: List<CellDependency>): Map<String, List<String>> {
        val dependencyMap = mutableMapOf<String, MutableList<String>>()
        
        for (dependency in dependencies) {
            val sourceCellId = dependency.sourceCellId
            val targetCellId = dependency.targetCellId
            
            if (sourceCellId !in dependencyMap) {
                dependencyMap[sourceCellId] = mutableListOf()
            }
            
            dependencyMap[sourceCellId]?.add(targetCellId)
        }
        
        return dependencyMap
    }
    
    private fun extractSheetId(cellId: String): Long {
        val parts = cellId.split(":")
        if (parts.size < 1) {
            throw IllegalArgumentException("Invalid cell ID format: $cellId")
        }
        return parts[0].toLong()
    }
    

    override fun updateDependentCells(cellId: String, userId: String) {
        logger.debug("Updating dependent cells for cell: {}", cellId)
        
        // Get cells that depend on this cell
        val dependencies = getDependenciesByTargetCellId(cellId)
        
        if (dependencies.isEmpty()) {
            logger.debug("No dependent cells found for cell: {}", cellId)
            return
        }
        
        logger.info("Found {} dependent cells for cell: {}", dependencies.size, cellId)
        
        // First, acquire a lock on the sheet to prevent concurrent updates
        val sheetId = cellId.split(":")[0]
        val sheetLockId = "sheet:$sheetId"
        
        if (!cellLockService.acquireLock(sheetLockId, userId)) {
            logger.warn("Failed to acquire lock on sheet: {} for user: {}", sheetId, userId)
            return
        }
        
        try {
            for (dependency in dependencies) {
                val sourceCellId = dependency.sourceCellId
                logger.debug("Updating dependent cell: {}", sourceCellId)
                
                if (!cellLockService.acquireLock(sourceCellId, userId)) {
                    logger.warn("Failed to acquire lock on dependent cell: {} for user: {}", sourceCellId, userId)
                    continue
                }
                
                try {
                    val dependentCell = cellRedisRepository.getCell(sourceCellId)
                    if (dependentCell == null) {
                        logger.warn("Dependent cell not found in Redis: {}", sourceCellId)
                        continue
                    }
                    
                    if (dependentCell.dataType != DataType.EXPRESSION) {
                        logger.debug("Dependent cell is not an expression, skipping: {}", sourceCellId)
                        continue
                    }
                    
                    val cellParts = sourceCellId.split(":")
                    if (cellParts.size != 3) {
                        logger.warn("Invalid cell ID format: {}", sourceCellId)
                        continue
                    }
                    
                    val depSheetId = cellParts[0].toLongOrNull()
                    val row = cellParts[1].toIntOrNull()
                    val column = cellParts[2]
                    
                    if (depSheetId == null || row == null) {
                        logger.warn("Invalid cell ID components: {}", sourceCellId)
                        continue
                    }
                    
                    val timestamp = Instant.now()
                    
                    val cellReferences = expressionParser.parse(dependentCell.data)
                    
                    // Build evaluation context with fresh cell values
                    val context = buildEvaluationContext(
                        cellReferences, 
                        depSheetId, 
                        row
                    )
                    
                    val evaluatedValue = try {
                        expressionEvaluator.evaluate(dependentCell.data, context)
                    } catch (e: Exception) {
                        logger.error("Error evaluating expression for dependent cell: {}: {}", sourceCellId, e.message, e)
                        "#ERROR!"
                    }
                    
                    // Update the dependent cell
                    val updatedCell = dependentCell.copy(
                        evaluatedValue = evaluatedValue,
                        updatedAt = timestamp
                    )
                    
                    // Save to Redis with TTL
                    logger.debug("Saving updated dependent cell to Redis: {}", updatedCell.id)
                    cellRedisRepository.saveCell(updatedCell)
                    
                    // Save to MongoDB asynchronously
                    logger.debug("Saving updated dependent cell to MongoDB asynchronously: {}", updatedCell.id)
                    cellAsyncService.saveCell(updatedCell)
                    
                    // Recursively update cells that depend on this cell
                    updateDependentCells(sourceCellId, userId)
                } catch (e: Exception) {
                    logger.error("Error updating dependent cell: {}: {}", sourceCellId, e.message, e)
                } finally {
                    // Release the lock on the dependent cell
                    cellLockService.releaseLock(sourceCellId, userId)
                }
            }
        } finally {
            // Release the lock on the sheet
            cellLockService.releaseLock(sheetLockId, userId)
        }
    }

    private fun buildEvaluationContext(
        cellReferences: List<String>, 
        sheetId: Long, 
        row: Int
    ): Map<String, String> {
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
            
            // Always get the latest value from Redis to ensure we have the most up-to-date data
            val cell = cellRedisRepository.getCell(cellId)
            context[cellRef] = cell?.evaluatedValue ?: "#REF!"
        }
        
        context["row"] = row.toString()
        
        return context
    }
}
