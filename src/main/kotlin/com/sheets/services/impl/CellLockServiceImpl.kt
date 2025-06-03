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
                logger.debug("Failed to acquire lock for cell: {} by user: {}. Current lock owner: {}", 
                    cellId, userId, currentOwner)
            }
            
            return result
        } catch (e: Exception) {
            logger.error("Error acquiring lock for cell: {} by user: {}: {}", 
                cellId, userId, e.message, e)
            // Return true to allow operation to proceed if Redis is unavailable
            // This is a fallback mechanism to prevent the application from being completely unusable
            // if Redis is temporarily unavailable
            return true
        }
    }
    
    override fun releaseLock(cellId: String, userId: String): Boolean {
        val lockKey = getLockKey(cellId)
        val currentOwner = getLockOwner(cellId)
        
        logger.debug("Attempting to release lock for cell: {} by user: {}", cellId, userId)
        
        try {
            if (currentOwner == userId) {
                redisTemplate.delete(lockKey)
                logger.debug("Lock released for cell: {} by user: {}", cellId, userId)
                return true
            }
            
            logger.debug("Failed to release lock for cell: {} by user: {}. Current lock owner: {}", 
                cellId, userId, currentOwner)
            return false
        } catch (e: Exception) {
            logger.error("Error releasing lock for cell: {} by user: {}: {}", 
                cellId, userId, e.message, e)
            // Return true to allow operation to proceed if Redis is unavailable
            return true
        }
    }
    
    override fun isLocked(cellId: String): Boolean {
        val lockKey = getLockKey(cellId)
        try {
            return redisTemplate.hasKey(lockKey)
        } catch (e: Exception) {
            logger.error("Error checking lock status for cell: {}: {}", cellId, e.message, e)
            // Return false to allow operation to proceed if Redis is unavailable
            return false
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
    
    private fun getLockKey(cellId: String): String {
        return "$lockKeyPrefix$cellId"
    }
}
