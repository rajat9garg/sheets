package com.sheets.repositories

import com.fasterxml.jackson.databind.ObjectMapper
import com.sheets.models.domain.CellDependency
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Repository
import java.util.concurrent.TimeUnit

/**
 * Repository for storing and retrieving cell dependencies from Redis cache
 */
@Repository
class CellDependencyRedisRepository(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(CellDependencyRedisRepository::class.java)
    
    // TTL for dependency cache (24 hours)
    private val DEPENDENCY_CACHE_TTL = 24L
    
    // Key prefixes
    private val DEPENDENCY_KEY_PREFIX = "dependency:"
    private val SOURCE_DEPENDENCIES_KEY_PREFIX = "source:dependencies:"
    private val TARGET_DEPENDENCIES_KEY_PREFIX = "target:dependencies:"
    private val SHEET_DEPENDENCIES_KEY_PREFIX = "sheet:dependencies:"
    
    /**
     * Save a cell dependency to Redis cache
     */
    fun saveDependency(dependency: CellDependency) {
        try {
            val key = getDependencyKey(dependency.sourceCellId, dependency.targetCellId)
            val value = objectMapper.writeValueAsString(dependency)
            
            logger.debug("Saving dependency to Redis cache: {}", key)
            redisTemplate.opsForValue().set(key, value, DEPENDENCY_CACHE_TTL, TimeUnit.HOURS)
            
            // Add dependency to source cell's dependencies set
            val sourceDependenciesKey = getSourceDependenciesKey(dependency.sourceCellId)
            redisTemplate.opsForSet().add(sourceDependenciesKey, key)
            redisTemplate.expire(sourceDependenciesKey, DEPENDENCY_CACHE_TTL, TimeUnit.HOURS)
            
            // Add dependency to target cell's dependencies set
            val targetDependenciesKey = getTargetDependenciesKey(dependency.targetCellId)
            redisTemplate.opsForSet().add(targetDependenciesKey, key)
            redisTemplate.expire(targetDependenciesKey, DEPENDENCY_CACHE_TTL, TimeUnit.HOURS)
            
            // Add dependency to sheet's dependencies set
            val sheetDependenciesKey = getSheetDependenciesKey(dependency.sheetId)
            redisTemplate.opsForSet().add(sheetDependenciesKey, key)
            redisTemplate.expire(sheetDependenciesKey, DEPENDENCY_CACHE_TTL, TimeUnit.HOURS)
            
            logger.debug("Dependency saved to Redis cache: {}", key)
        } catch (e: Exception) {
            logger.error("Error saving dependency to Redis cache: {}:{}: {}", 
                dependency.sourceCellId, dependency.targetCellId, e.message, e)
        }
    }
    
    /**
     * Save multiple cell dependencies to Redis cache
     */
    fun saveDependencies(dependencies: List<CellDependency>) {
        dependencies.forEach { saveDependency(it) }
    }
    
    /**
     * Get a cell dependency from Redis cache by source and target cell IDs
     */
    fun getDependency(sourceCellId: String, targetCellId: String): CellDependency? {
        try {
            val key = getDependencyKey(sourceCellId, targetCellId)
            logger.debug("Getting dependency from Redis cache: {}", key)
            
            val value = redisTemplate.opsForValue().get(key)
            
            return if (value != null) {
                val dependency = objectMapper.readValue(value, CellDependency::class.java)
                logger.debug("Dependency found in Redis cache: {}", key)
                dependency
            } else {
                logger.debug("Dependency not found in Redis cache: {}", key)
                null
            }
        } catch (e: Exception) {
            logger.error("Error getting dependency from Redis cache: {}:{}: {}", 
                sourceCellId, targetCellId, e.message, e)
            return null
        }
    }
    
    /**
     * Get all dependencies for a source cell from Redis cache
     */
    fun getDependenciesBySourceCellId(sourceCellId: String): List<CellDependency> {
        try {
            val sourceDependenciesKey = getSourceDependenciesKey(sourceCellId)
            logger.debug("Getting dependencies for source cell from Redis cache: {}", sourceDependenciesKey)
            
            val dependencyKeys = redisTemplate.opsForSet().members(sourceDependenciesKey) ?: emptySet()
            
            return dependencyKeys.mapNotNull { key ->
                val value = redisTemplate.opsForValue().get(key)
                if (value != null) {
                    objectMapper.readValue(value, CellDependency::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting dependencies for source cell from Redis cache: {}: {}", 
                sourceCellId, e.message, e)
            return emptyList()
        }
    }
    
    /**
     * Get all dependencies for a target cell from Redis cache
     */
    fun getDependenciesByTargetCellId(targetCellId: String): List<CellDependency> {
        try {
            val targetDependenciesKey = getTargetDependenciesKey(targetCellId)
            logger.debug("Getting dependencies for target cell from Redis cache: {}", targetDependenciesKey)
            
            val dependencyKeys = redisTemplate.opsForSet().members(targetDependenciesKey) ?: emptySet()
            
            return dependencyKeys.mapNotNull { key ->
                val value = redisTemplate.opsForValue().get(key)
                if (value != null) {
                    objectMapper.readValue(value, CellDependency::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting dependencies for target cell from Redis cache: {}: {}", 
                targetCellId, e.message, e)
            return emptyList()
        }
    }
    
    /**
     * Get all dependencies for a sheet from Redis cache
     */
    fun getDependenciesBySheetId(sheetId: Long): List<CellDependency> {
        try {
            val sheetDependenciesKey = getSheetDependenciesKey(sheetId)
            logger.debug("Getting dependencies for sheet from Redis cache: {}", sheetDependenciesKey)
            
            val dependencyKeys = redisTemplate.opsForSet().members(sheetDependenciesKey) ?: emptySet()
            
            return dependencyKeys.mapNotNull { key ->
                val value = redisTemplate.opsForValue().get(key)
                if (value != null) {
                    objectMapper.readValue(value, CellDependency::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            logger.error("Error getting dependencies for sheet from Redis cache: {}: {}", 
                sheetId, e.message, e)
            return emptyList()
        }
    }
    
    /**
     * Delete a cell dependency from Redis cache
     */
    fun deleteDependency(sourceCellId: String, targetCellId: String) {
        try {
            val key = getDependencyKey(sourceCellId, targetCellId)
            logger.debug("Deleting dependency from Redis cache: {}", key)
            
            // Get the dependency to find its sheet ID
            val dependency = getDependency(sourceCellId, targetCellId)
            
            if (dependency != null) {
                // Remove dependency from source cell's dependencies set
                val sourceDependenciesKey = getSourceDependenciesKey(dependency.sourceCellId)
                redisTemplate.opsForSet().remove(sourceDependenciesKey, key)
                
                // Remove dependency from target cell's dependencies set
                val targetDependenciesKey = getTargetDependenciesKey(dependency.targetCellId)
                redisTemplate.opsForSet().remove(targetDependenciesKey, key)
                
                // Remove dependency from sheet's dependencies set
                val sheetDependenciesKey = getSheetDependenciesKey(dependency.sheetId)
                redisTemplate.opsForSet().remove(sheetDependenciesKey, key)
            }
            
            // Delete the dependency
            redisTemplate.delete(key)
            
            logger.debug("Dependency deleted from Redis cache: {}", key)
        } catch (e: Exception) {
            logger.error("Error deleting dependency from Redis cache: {}:{}: {}", 
                sourceCellId, targetCellId, e.message, e)
        }
    }
    
    /**
     * Delete all dependencies for a source cell from Redis cache
     */
    fun deleteBySourceCellId(sourceCellId: String): Int {
        try {
            val sourceDependenciesKey = getSourceDependenciesKey(sourceCellId)
            logger.debug("Deleting dependencies for source cell from Redis cache: {}", sourceDependenciesKey)
            
            val dependencies = getDependenciesBySourceCellId(sourceCellId)
            
            dependencies.forEach { dependency ->
                deleteDependency(dependency.sourceCellId, dependency.targetCellId)
            }
            
            // Delete the source dependencies set
            redisTemplate.delete(sourceDependenciesKey)
            
            logger.debug("Dependencies deleted for source cell from Redis cache: {}", sourceDependenciesKey)
            return dependencies.size
        } catch (e: Exception) {
            logger.error("Error deleting dependencies for source cell from Redis cache: {}: {}", 
                sourceCellId, e.message, e)
            return 0
        }
    }
    
    /**
     * Delete all dependencies for a target cell from Redis cache
     */
    fun deleteByTargetCellId(targetCellId: String): Int {
        try {
            val targetDependenciesKey = getTargetDependenciesKey(targetCellId)
            logger.debug("Deleting dependencies for target cell from Redis cache: {}", targetDependenciesKey)
            
            val dependencies = getDependenciesByTargetCellId(targetCellId)
            
            dependencies.forEach { dependency ->
                deleteDependency(dependency.sourceCellId, dependency.targetCellId)
            }
            
            // Delete the target dependencies set
            redisTemplate.delete(targetDependenciesKey)
            
            logger.debug("Dependencies deleted for target cell from Redis cache: {}", targetDependenciesKey)
            return dependencies.size
        } catch (e: Exception) {
            logger.error("Error deleting dependencies for target cell from Redis cache: {}: {}", 
                targetCellId, e.message, e)
            return 0
        }
    }
    
    /**
     * Delete all dependencies for a sheet from Redis cache
     */
    fun deleteBySheetId(sheetId: Long): Int {
        try {
            val sheetDependenciesKey = getSheetDependenciesKey(sheetId)
            logger.debug("Deleting dependencies for sheet from Redis cache: {}", sheetDependenciesKey)
            
            val dependencies = getDependenciesBySheetId(sheetId)
            
            dependencies.forEach { dependency ->
                deleteDependency(dependency.sourceCellId, dependency.targetCellId)
            }
            
            // Delete the sheet dependencies set
            redisTemplate.delete(sheetDependenciesKey)
            
            logger.debug("Dependencies deleted for sheet from Redis cache: {}", sheetDependenciesKey)
            return dependencies.size
        } catch (e: Exception) {
            logger.error("Error deleting dependencies for sheet from Redis cache: {}: {}", 
                sheetId, e.message, e)
            return 0
        }
    }
    
    /**
     * Generate a key for a dependency
     */
    private fun getDependencyKey(sourceCellId: String, targetCellId: String): String {
        return "$DEPENDENCY_KEY_PREFIX$sourceCellId:$targetCellId"
    }
    
    /**
     * Generate a key for a source cell's dependencies
     */
    private fun getSourceDependenciesKey(sourceCellId: String): String {
        return "$SOURCE_DEPENDENCIES_KEY_PREFIX$sourceCellId"
    }
    
    /**
     * Generate a key for a target cell's dependencies
     */
    private fun getTargetDependenciesKey(targetCellId: String): String {
        return "$TARGET_DEPENDENCIES_KEY_PREFIX$targetCellId"
    }
    
    /**
     * Generate a key for a sheet's dependencies
     */
    private fun getSheetDependenciesKey(sheetId: Long): String {
        return "$SHEET_DEPENDENCIES_KEY_PREFIX$sheetId"
    }
}
