package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.repositories.CellRepository
import com.sheets.services.CellAsyncService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

/**
 * Service for asynchronous cell operations
 */
@Service
class CellAsyncServiceImpl(
    private val cellRepository: CellRepository
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
}
