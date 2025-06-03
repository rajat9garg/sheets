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

    override fun getCell(id: String): Cell? {
        // Try to get the cell from Redis first for fast retrieval
        val cellFromRedis = cellRedisRepository.getCell(id)
        if (cellFromRedis != null) {
            return cellFromRedis
        }
        
        // If not in Redis, get from MongoDB
        val cellFromMongo = cellRepository.findById(id)
        
        // If found in MongoDB, cache it in Redis for future fast access
        if (cellFromMongo != null) {
            cellRedisRepository.saveCell(cellFromMongo)
        }
        
        return cellFromMongo
    }

    override fun getCellsBySheetId(sheetId: Long): List<Cell> {
        val cellsFromMongo = cellRepository.findBySheetId(sheetId)
        
        // Cache all cells in Redis for future fast access
        cellsFromMongo.forEach { cell ->
            cellRedisRepository.saveCell(cell)
        }
        
        return cellsFromMongo
    }

    override fun updateCell(cell: Cell, userId: String): Cell {
        logger.info("Updating cell: {} by user: {}", cell.id, userId)
        
        // Use the enhanced executeWithCellLocks method that implements Redis-first approach
        return CellUtils.executeWithCellLocks(
            cell,
            userId,
            cellLockService,
            cellDependencyService,
            cellRedisRepository,
            cellAsyncService,
            expressionParser,
            circularDependencyDetector
        ) {
            performCellUpdate(cell, userId)
        }
    }

    private fun performCellUpdate(cell: Cell, userId: String): Cell {
        val existingCell = getCell(cell.id)
        val now = Instant.now()
        
        return if (existingCell == null) {
            createNewCell(cell, now, userId)
        } else {
            updateExistingCell(existingCell, cell, now, userId)
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
        
        CellUtils.executeWithCellDeletion(
            id,
            userId,
            cellLockService,
            cellDependencyService,
            cellRedisRepository,
            cellAsyncService
        )
    }
}
