package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.repositories.CellRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Service for asynchronous cell operations
 */
@Service
class CellAsyncServiceImpl(
    private val cellRepository: CellRepository
) {
    private val logger = LoggerFactory.getLogger(CellAsyncServiceImpl::class.java)
    
    /**
     * Asynchronously save a cell to MongoDB
     */
    @Async
    fun saveCell(cell: Cell) {
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
    fun saveCells(cells: List<Cell>) {
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
    fun deleteCell(cellId: String) {
        try {
            logger.debug("Asynchronously deleting cell from MongoDB: {}", cellId)
            cellRepository.deleteById(cellId)
            logger.debug("Cell deleted from MongoDB asynchronously: {}", cellId)
        } catch (e: Exception) {
            logger.error("Error deleting cell from MongoDB asynchronously: {}: {}", cellId, e.message, e)
        }
    }
}
