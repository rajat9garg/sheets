package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.repositories.CellRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import com.sheets.services.`cell-management`.CellUtils
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for asynchronous cell operations
 */
@Service
class CellAsyncServiceImpl(
    private val cellRepository: CellRepository,
    @Lazy private val cellDependencyService: CellDependencyService
) : CellAsyncService {
    private val logger = LoggerFactory.getLogger(CellAsyncServiceImpl::class.java)
    
    /**
     * Asynchronously save a cell to MongoDB
     */
    @Async
    override fun saveCell(cell: Cell) {
        try {
            logger.debug("Asynchronously saving cell to MongoDB: {}", cell.id)
            cellRepository.save(cell)
            logger.debug("Cell saved to MongoDB asynchronously: {}", cell.id)
        } catch (e: Exception) {
            logger.error("Error saving cell to MongoDB asynchronously: {}: {}", cell.id, e.message, e)
        }
    }
    
    /**
     * Asynchronously save multiple cells to MongoDB
     */
    @Async
    override fun saveCells(cells: List<Cell>) {
        try {
            logger.debug("Asynchronously saving {} cells to MongoDB", cells.size)
            cellRepository.saveAll(cells)
            logger.debug("{} cells saved to MongoDB asynchronously", cells.size)
        } catch (e: Exception) {
            logger.error("Error saving cells to MongoDB asynchronously: {}", e.message, e)
        }
    }
    
    /**
     * Asynchronously delete a cell from MongoDB
     */
    @Async
    override fun deleteCell(id: String) {
        try {
            logger.debug("Asynchronously deleting cell from MongoDB: {}", id)
            cellRepository.deleteById(id)
            logger.debug("Cell deleted from MongoDB asynchronously: {}", id)
        } catch (e: Exception) {
            logger.error("Error deleting cell from MongoDB asynchronously: {}: {}", id, e.message, e)
        }
    }
    
    /**
     * Asynchronously update cell dependencies
     */
    @Async
    override fun updateDependencies(cellId: String, dependencies: List<String>, sheetId: Long, timestamp: Instant) {
        try {
            logger.debug("Asynchronously updating dependencies for cell: {}", cellId)
            
            // Delete existing dependencies
            cellDependencyService.deleteBySourceCellId(cellId)
            
            // Create new dependencies if there are any
            if (dependencies.isNotEmpty()) {
                val cellDependencies = CellUtils.createCellDependencies(
                    sheetId,
                    cellId,
                    dependencies,
                    timestamp
                )
                
                cellDependencyService.createDependencies(cellDependencies)
            }
            
            logger.debug("Dependencies updated asynchronously for cell: {}", cellId)
        } catch (e: Exception) {
            logger.error("Error updating dependencies asynchronously for cell: {}: {}", cellId, e.message, e)
        }
    }
    
    /**
     * Asynchronously update dependent cells
     */
    @Async
    override fun updateDependentCells(cellId: String, userId: String) {
        try {
            logger.debug("Asynchronously updating dependent cells for cell: {}", cellId)
            
            // Find all cells that depend on this cell
            val dependencies = cellDependencyService.getDependenciesByTargetCellId(cellId)
            
            if (dependencies.isNotEmpty()) {
                logger.debug("Found {} dependent cells to update", dependencies.size)
                
                // For each dependent cell, we would trigger a recalculation
                // This would be implemented based on your application's requirements
                // For example, you might publish events to a message queue
                
                logger.debug("Dependent cells update triggered asynchronously for cell: {}", cellId)
            } else {
                logger.debug("No dependent cells found for cell: {}", cellId)
            }
        } catch (e: Exception) {
            logger.error("Error updating dependent cells asynchronously for cell: {}: {}", cellId, e.message, e)
        }
    }
}
