package com.sheets.services.impl

import com.sheets.services.CellLockService
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class CellLockServiceImpl(
    private val redisTemplate: RedisTemplate<String, String>
) : CellLockService {
    
    private val lockKeyPrefix = "cell:lock:"
    
    override fun acquireLock(cellId: String, userId: String, timeoutMs: Long): Boolean {
        val lockKey = getLockKey(cellId)
        val expireTime = timeoutMs / 1000
        
        val setIfAbsent = redisTemplate.opsForValue().setIfAbsent(lockKey, userId, expireTime, TimeUnit.SECONDS)
        return setIfAbsent ?: false
    }
    
    override fun releaseLock(cellId: String, userId: String): Boolean {
        val lockKey = getLockKey(cellId)
        val currentOwner = getLockOwner(cellId)
        
        if (currentOwner == userId) {
            redisTemplate.delete(lockKey)
            return true
        }
        
        return false
    }
    
    override fun isLocked(cellId: String): Boolean {
        val lockKey = getLockKey(cellId)
        return redisTemplate.hasKey(lockKey)
    }
    
    override fun getLockOwner(cellId: String): String? {
        val lockKey = getLockKey(cellId)
        return redisTemplate.opsForValue().get(lockKey)
    }
    
    private fun getLockKey(cellId: String): String {
        return "$lockKeyPrefix$cellId"
    }
}
