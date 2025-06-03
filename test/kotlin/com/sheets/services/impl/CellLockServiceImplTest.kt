package com.sheets.services.impl

import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

class CellLockServiceImplTest {

    @MockK
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @MockK
    private lateinit var valueOperations: ValueOperations<String, String>

    @InjectMockKs
    private lateinit var cellLockService: CellLockServiceImpl

    private val testCellId = "1:1:1"
    private val testUserId = "user1"
    private val lockKey = "cell:lock:1:1:1"
    private val timeoutMs = 5000L

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { redisTemplate.opsForValue() } returns valueOperations
    }

    @Test
    fun `test acquireLock successfully acquires lock when not already locked`() {
        // Given
        every { valueOperations.setIfAbsent(lockKey, testUserId, timeoutMs / 1000, TimeUnit.SECONDS) } returns true

        // When
        val result = cellLockService.acquireLock(testCellId, testUserId, timeoutMs)

        // Then
        verify(exactly = 1) { valueOperations.setIfAbsent(lockKey, testUserId, timeoutMs / 1000, TimeUnit.SECONDS) }
        assertTrue(result)
    }

    @Test
    fun `test acquireLock fails when lock is already taken`() {
        // Given
        every { valueOperations.setIfAbsent(lockKey, testUserId, timeoutMs / 1000, TimeUnit.SECONDS) } returns false

        // When
        val result = cellLockService.acquireLock(testCellId, testUserId, timeoutMs)

        // Then
        verify(exactly = 1) { valueOperations.setIfAbsent(lockKey, testUserId, timeoutMs / 1000, TimeUnit.SECONDS) }
        assertFalse(result)
    }

    @Test
    fun `test acquireLock handles null response from Redis`() {
        // Given
        every { valueOperations.setIfAbsent(lockKey, testUserId, timeoutMs / 1000, TimeUnit.SECONDS) } returns null

        // When
        val result = cellLockService.acquireLock(testCellId, testUserId, timeoutMs)

        // Then
        verify(exactly = 1) { valueOperations.setIfAbsent(lockKey, testUserId, timeoutMs / 1000, TimeUnit.SECONDS) }
        assertFalse(result)
    }

    @Test
    fun `test releaseLock successfully releases lock when user is the owner`() {
        // Given
        every { valueOperations.get(lockKey) } returns testUserId
        
        // Use a slot to capture the key parameter
        val keySlot = slot<String>()
        every { redisTemplate.delete(capture(keySlot)) } returns true
        
        // When
        val result = cellLockService.releaseLock(testCellId, testUserId)
        
        // Then
        verify(exactly = 1) { valueOperations.get(lockKey) }
        verify(exactly = 1) { redisTemplate.delete(lockKey) }
        assertEquals(lockKey, keySlot.captured)
        assertTrue(result)
    }

    @Test
    fun `test releaseLock fails when user is not the owner`() {
        // Given
        val otherUserId = "user2"
        every { valueOperations.get(lockKey) } returns otherUserId

        // When
        val result = cellLockService.releaseLock(testCellId, testUserId)

        // Then
        verify(exactly = 1) { valueOperations.get(lockKey) }
        verify(exactly = 0) { redisTemplate.delete(lockKey) }
        assertFalse(result)
    }

    @Test
    fun `test releaseLock fails when lock does not exist`() {
        // Given
        every { valueOperations.get(lockKey) } returns null

        // When
        val result = cellLockService.releaseLock(testCellId, testUserId)

        // Then
        verify(exactly = 1) { valueOperations.get(lockKey) }
        verify(exactly = 0) { redisTemplate.delete(lockKey) }
        assertFalse(result)
    }

    @Test
    fun `test isLocked returns true when lock exists`() {
        // Given
        every { redisTemplate.hasKey(lockKey) } returns true

        // When
        val result = cellLockService.isLocked(testCellId)

        // Then
        verify(exactly = 1) { redisTemplate.hasKey(lockKey) }
        assertTrue(result)
    }

    @Test
    fun `test isLocked returns false when lock does not exist`() {
        // Given
        every { redisTemplate.hasKey(lockKey) } returns false

        // When
        val result = cellLockService.isLocked(testCellId)

        // Then
        verify(exactly = 1) { redisTemplate.hasKey(lockKey) }
        assertFalse(result)
    }

    @Test
    fun `test getLockOwner returns owner ID when lock exists`() {
        // Given
        every { valueOperations.get(lockKey) } returns testUserId

        // When
        val result = cellLockService.getLockOwner(testCellId)

        // Then
        verify(exactly = 1) { valueOperations.get(lockKey) }
        assertEquals(testUserId, result)
    }

    @Test
    fun `test getLockOwner returns null when lock does not exist`() {
        // Given
        every { valueOperations.get(lockKey) } returns null

        // When
        val result = cellLockService.getLockOwner(testCellId)

        // Then
        verify(exactly = 1) { valueOperations.get(lockKey) }
        assertNull(result)
    }
}
