package com.sheets.services.impl

import com.sheets.services.CellLockService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class CellLockServiceImpl(
    private val redisTemplate: RedisTemplate<String, String>
) : CellLockService {
    
    private val logger = LoggerFactory.getLogger(CellLockServiceImpl::class.java)
    private val lockKeyPrefix = "cell:lock:"
    private val sheetLockKeyPrefix = "sheet:lock:"

    override fun acquireLock(cellId: String, userId: String, timeoutMs: Long): Boolean {
        val lockKey = getLockKey(cellId)
        val expireTime = timeoutMs / 1000
        
        logger.debug("Attempting to acquire lock for cell: {} by user: {}", cellId, userId)
        
        try {
            val setIfAbsent = redisTemplate.opsForValue().setIfAbsent(lockKey, userId, expireTime, TimeUnit.SECONDS)
            val result = setIfAbsent ?: false
            
            if (result) {
                logger.debug("Lock acquired for cell: {} by user: {}", cellId, userId)
            } else {
                val currentOwner = getLockOwner(cellId)
                logger.warn("Failed to acquire lock for cell: {} by user: {}. Current lock owner: {}", 
                    cellId, userId, currentOwner)
            }
            
            return result
        } catch (e: Exception) {
            logger.error("Error acquiring lock for cell: {} by user: {}: {}", cellId, userId, e.message, e)
            throw e
        }
    }
    
    override fun acquireSheetLock(sheetId: Long, userId: String, timeoutMs: Long): Boolean {
        val lockKey = getSheetLockKey(sheetId)
        val expireTime = timeoutMs / 1000
        
        logger.debug("Attempting to acquire lock for sheet: {} by user: {}", sheetId, userId)
        
        try {
            val setIfAbsent = redisTemplate.opsForValue().setIfAbsent(lockKey, userId, expireTime, TimeUnit.SECONDS)
            val result = setIfAbsent ?: false
            
            if (result) {
                logger.debug("Lock acquired for sheet: {} by user: {}", sheetId, userId)
            } else {
                val currentOwner = getSheetLockOwner(sheetId)
                logger.warn("Failed to acquire lock for sheet: {} by user: {}. Current lock owner: {}", 
                    sheetId, userId, currentOwner)
            }
            
            return result
        } catch (e: Exception) {
            logger.error("Error acquiring lock for sheet: {} by user: {}: {}", sheetId, userId, e.message, e)
            throw e
        }
    }
    
    override fun releaseLock(cellId: String, userId: String): Boolean {
        val lockKey = getLockKey(cellId)
        val currentOwner = getLockOwner(cellId)
        
        logger.debug("Attempting to release lock for cell: {} by user: {}", cellId, userId)
        
        try {
            // If the lock owner is null (possibly due to Redis reconnection), allow the release
            if (currentOwner == null || currentOwner == userId) {
                redisTemplate.delete(lockKey)
                logger.debug("Lock released for cell: {} by user: {}", cellId, userId)
                return true
            }
            
            logger.warn("Failed to release lock for cell: {} by user: {}. Current lock owner: {}", 
                cellId, userId, currentOwner)
            return false
        } catch (e: Exception) {
            logger.error("Error releasing lock for cell: {} by user: {}: {}", 
                cellId, userId, e.message, e)
            // Return false to indicate failure
            return false
        }
    }
    
    override fun releaseSheetLock(sheetId: Long, userId: String): Boolean {
        val lockKey = getSheetLockKey(sheetId)
        val currentOwner = getSheetLockOwner(sheetId)
        
        logger.debug("Attempting to release lock for sheet: {} by user: {}", sheetId, userId)
        
        try {
            // If the lock owner is null (possibly due to Redis reconnection), allow the release
            if (currentOwner == null || currentOwner == userId) {
                redisTemplate.delete(lockKey)
                logger.debug("Lock released for sheet: {} by user: {}", sheetId, userId)
                return true
            }
            
            logger.warn("Failed to release lock for sheet: {} by user: {}. Current lock owner: {}", 
                sheetId, userId, currentOwner)
            return false
        } catch (e: Exception) {
            logger.error("Error releasing lock for sheet: {} by user: {}: {}", 
                sheetId, userId, e.message, e)
            // Return false to indicate failure
            return false
        }
    }
    
    override fun isLocked(cellId: String): Boolean {
        val lockKey = getLockKey(cellId)
        try {
            return redisTemplate.hasKey(lockKey)
        } catch (e: Exception) {
            logger.error("Error checking lock status for cell: {}: {}", cellId, e.message, e)
            // Assume it's locked if we can't check, to be safe
            return true
        }
    }
    
    override fun isSheetLocked(sheetId: Long): Boolean {
        val lockKey = getSheetLockKey(sheetId)
        try {
            return redisTemplate.hasKey(lockKey)
        } catch (e: Exception) {
            logger.error("Error checking lock status for sheet: {}: {}", sheetId, e.message, e)
            // Assume it's locked if we can't check, to be safe
            return true
        }
    }
    
    override fun getLockOwner(cellId: String): String? {
        val lockKey = getLockKey(cellId)
        try {
            return redisTemplate.opsForValue().get(lockKey)
        } catch (e: Exception) {
            logger.error("Error getting lock owner for cell: {}: {}", cellId, e.message, e)
            return null
        }
    }
    
    override fun getSheetLockOwner(sheetId: Long): String? {
        val lockKey = getSheetLockKey(sheetId)
        try {
            return redisTemplate.opsForValue().get(lockKey)
        } catch (e: Exception) {
            logger.error("Error getting lock owner for sheet: {}: {}", sheetId, e.message, e)
            return null
        }
    }
    
    private fun getLockKey(cellId: String): String {
        return "$lockKeyPrefix$cellId"
    }
    
    private fun getSheetLockKey(sheetId: Long): String {
        return "$sheetLockKeyPrefix$sheetId"
    }
}
