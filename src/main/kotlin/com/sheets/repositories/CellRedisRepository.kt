package com.sheets.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import com.sheets.models.domain.Cell
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

/**
 * Repository for storing and retrieving cells from Redis cache
 */
@Repository
class CellRedisRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(CellRedisRepository::class.java)
    
    // TTL for cell cache (24 hours)
    private val CELL_CACHE_TTL = 24L
    
    // Key prefixes
    private val CELL_KEY_PREFIX = "cell:"
    private val SHEET_CELLS_KEY_PREFIX = "sheet:cells:"
    private val CELL_DEPENDENCIES_KEY_PREFIX = "cell:dependencies:"
    
    /**
     * Save a cell to Redis cache
     */
    fun saveCell(cell: Cell) {
        try {
            val key = getCellKey(cell.id)
            val value = objectMapper.writeValueAsString(cell)
            
            logger.debug("Saving cell to Redis cache: {}", key)
            redisTemplate.opsForValue().set(key, value, CELL_CACHE_TTL, TimeUnit.HOURS)
            
            // Add cell ID to the sheet's cell set
            val sheetCellsKey = getSheetCellsKey(cell.sheetId)
            redisTemplate.opsForSet().add(sheetCellsKey, cell.id)
            redisTemplate.expire(sheetCellsKey, CELL_CACHE_TTL, TimeUnit.HOURS)
            
            logger.debug("Cell saved to Redis cache: {}", key)
        } catch (e: Exception) {
            logger.error("Error saving cell to Redis cache: {}: {}", cell.id, e.message, e)
        }
    }
    
    /**
     * Save a cell to Redis cache with a custom TTL (in seconds)
     */
    fun saveCellWithTTL(cell: Cell, ttlSeconds: Long) {
        try {
            val key = getCellKey(cell.id)
            val value = objectMapper.writeValueAsString(cell)
            
            logger.debug("Saving cell to Redis cache with TTL of {} seconds: {}", ttlSeconds, key)
            redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS)
            
            // Add cell ID to the sheet's cell set
            val sheetCellsKey = getSheetCellsKey(cell.sheetId)
            redisTemplate.opsForSet().add(sheetCellsKey, cell.id)
            redisTemplate.expire(sheetCellsKey, ttlSeconds, TimeUnit.SECONDS)
            
            logger.debug("Cell saved to Redis cache with custom TTL: {}", key)
        } catch (e: Exception) {
            logger.error("Error saving cell to Redis cache with TTL: {}: {}", cell.id, e.message, e)
        }
    }
    
    /**
     * Get a cell from Redis cache by ID
     */
    fun getCell(cellId: String): Cell? {
        try {
            val key = getCellKey(cellId)
            logger.debug("Getting cell from Redis cache: {}", key)
            
            val value = redisTemplate.opsForValue().get(key)
            
            return if (value != null) {
                val cell = objectMapper.readValue(value, Cell::class.java)
                logger.debug("Cell found in Redis cache: {}", key)
                cell
            } else {
                logger.debug("Cell not found in Redis cache: {}", key)
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting cell from Redis cache: {}: {}", cellId, e.message, e)
            return null
        }
    }
    
    /**
     * Delete a cell from Redis cache
     */
    fun deleteCell(cellId: String) {
        try {
            val key = getCellKey(cellId)
            logger.debug("Deleting cell from Redis cache: {}", key)
            
            // Get the cell to find its sheet ID
            val cell = getCell(cellId)
            
            // Delete the cell
            redisTemplate.delete(key)
            
            // Remove cell ID from the sheet's cell set
            if (cell != null) {
                val sheetCellsKey = getSheetCellsKey(cell.sheetId)
                redisTemplate.opsForSet().remove(sheetCellsKey, cellId)
            }
            
            logger.debug("Cell deleted from Redis cache: {}", key)
        } catch (e: Exception) {
            logger.error("Error deleting cell from Redis cache: {}: {}", cellId, e.message, e)
        }
    }
    
    /**
     * Get all cells for a sheet from Redis cache
     */
    fun getCellsBySheetId(sheetId: Long): List<Cell> {
        try {
            val sheetCellsKey = getSheetCellsKey(sheetId)
            logger.debug("Getting all cells for sheet from Redis cache: {}", sheetCellsKey)
            
            val cellIds = redisTemplate.opsForSet().members(sheetCellsKey)
            
            if (cellIds.isNullOrEmpty()) {
                logger.debug("No cells found in Redis cache for sheet: {}", sheetId)
                return emptyList()
            }
            
            val cells = mutableListOf<Cell>()
            
            for (cellId in cellIds) {
                val cell = getCell(cellId)
                if (cell != null) {
                    cells.add(cell)
                }
            }
            
            logger.debug("Found {} cells in Redis cache for sheet: {}", cells.size, sheetId)
            return cells
        } catch (e: Exception) {
            logger.error("Error getting cells for sheet from Redis cache: {}: {}", sheetId, e.message, e)
            return emptyList()
        }
    }
    
    /**
     * Check if a cell exists in Redis cache
     */
    fun existsById(cellId: String): Boolean {
        try {
            val key = getCellKey(cellId)
            return redisTemplate.hasKey(key)
        } catch (e: Exception) {
            logger.error("Error checking if cell exists in Redis cache: {}: {}", cellId, e.message, e)
            return false
        }
    }
    
    /**
     * Save cell dependencies to Redis cache with a custom TTL (in seconds)
     */
    fun saveDependenciesWithTTL(cellId: String, dependencies: List<String>, ttlSeconds: Long) {
        try {
            if (dependencies.isEmpty()) {
                return
            }
            
            val key = getCellDependenciesKey(cellId)
            logger.debug("Saving {} dependencies for cell to Redis cache with TTL of {} seconds: {}", 
                dependencies.size, ttlSeconds, key)
            
            // Convert dependencies list to array for Redis
            val dependenciesArray = dependencies.toTypedArray()
            
            // Save dependencies as a set
            redisTemplate.opsForSet().add(key, *dependenciesArray)
            redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS)
            
            logger.debug("Dependencies saved to Redis cache with custom TTL: {}", key)
        } catch (e: Exception) {
            logger.error("Error saving dependencies to Redis cache with TTL: {}: {}", cellId, e.message, e)
        }
    }
    
    /**
     * Get dependencies for a cell from Redis cache
     */
    fun getDependencies(cellId: String): List<String> {
        try {
            val key = getCellDependenciesKey(cellId)
            logger.debug("Getting dependencies for cell from Redis cache: {}", key)
            
            val dependencies = redisTemplate.opsForSet().members(key)
            
            if (dependencies.isNullOrEmpty()) {
                logger.debug("No dependencies found in Redis cache for cell: {}", cellId)
                return emptyList()
            }
            
            logger.debug("Found {} dependencies in Redis cache for cell: {}", dependencies.size, cellId)
            return dependencies.toList()
        } catch (e: Exception) {
            logger.error("Error getting dependencies for cell from Redis cache: {}: {}", cellId, e.message, e)
            return emptyList()
        }
    }
    
    /**
     * Delete dependencies for a cell from Redis cache
     */
    fun deleteDependencies(cellId: String) {
        try {
            val key = getCellDependenciesKey(cellId)
            logger.debug("Deleting dependencies for cell from Redis cache: {}", key)
            
            redisTemplate.delete(key)
            
            logger.debug("Dependencies deleted from Redis cache: {}", key)
        } catch (e: Exception) {
            logger.error("Error deleting dependencies for cell from Redis cache: {}: {}", cellId, e.message, e)
        }
    }
    
    /**
     * Get Redis key for a cell
     */
    private fun getCellKey(cellId: String): String {
        return "$CELL_KEY_PREFIX$cellId"
    }
    
    /**
     * Get Redis key for a sheet's cell set
     */
    private fun getSheetCellsKey(sheetId: Long): String {
        return "$SHEET_CELLS_KEY_PREFIX$sheetId"
    }
    
    /**
     * Get Redis key for a cell's dependencies
     */
    private fun getCellDependenciesKey(cellId: String): String {
        return "$CELL_DEPENDENCIES_KEY_PREFIX$cellId"
    }
}
