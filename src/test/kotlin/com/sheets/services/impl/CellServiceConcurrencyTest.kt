package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.models.domain.CellDependency
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRedisRepository
import com.sheets.repositories.CellRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.`cell-management`.ExpressionDataProcessor
import com.sheets.services.`cell-management`.PrimitiveDataProcessor
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CellServiceConcurrencyTest {

    @MockK
    private lateinit var cellRepository: CellRepository

    @MockK
    private lateinit var cellRedisRepository: CellRedisRepository

    @MockK
    private lateinit var cellAsyncService: CellAsyncService

    @MockK
    private lateinit var cellDependencyService: CellDependencyService

    @MockK
    private lateinit var expressionParser: ExpressionParser

    @MockK
    private lateinit var expressionEvaluator: ExpressionEvaluator

    @MockK
    private lateinit var circularDependencyDetector: CircularDependencyDetector

    @MockK
    private lateinit var cellLockService: CellLockService

    @MockK
    private lateinit var primitiveDataProcessor: PrimitiveDataProcessor

    @MockK
    private lateinit var expressionDataProcessor: ExpressionDataProcessor

    private lateinit var cellService: CellServiceImpl

    private val testCellId = "1:1:1"
    private val testSheetId = 1L
    private val testUserId1 = "user1"
    private val testUserId2 = "user2"
    private val timestamp = Instant.now()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        
        cellService = CellServiceImpl(
            cellRepository,
            cellRedisRepository,
            cellAsyncService,
            cellDependencyService,
            expressionParser,
            expressionEvaluator,
            circularDependencyDetector,
            cellLockService,
            primitiveDataProcessor,
            expressionDataProcessor
        )
    }

    @Test
    fun `test concurrent cell updates with locking`() {
        // Given
        val existingCell = Cell(
            id = testCellId,
            sheetId = testSheetId,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = timestamp,
            updatedAt = timestamp
        )

        val newCellData1 = existingCell.copy(
            data = "20"
        )

        val newCellData2 = existingCell.copy(
            data = "30"
        )

        val updatedCell1 = newCellData1.copy(
            evaluatedValue = "20",
            updatedAt = timestamp.plusSeconds(1)
        )

        val updatedCell2 = newCellData2.copy(
            evaluatedValue = "30",
            updatedAt = timestamp.plusSeconds(2)
        )

        // Mock the cell lock service to simulate locking behavior
        val user1LockSuccess = true
        val user2LockSuccess = false
        
        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        
        every { primitiveDataProcessor.processExistingCell(any(), eq(newCellData1), any(), eq(testUserId1)) } returns updatedCell1
        every { primitiveDataProcessor.processExistingCell(any(), eq(newCellData2), any(), eq(testUserId2)) } returns updatedCell2
        
        // Simulate lock acquisition and release
        every { cellLockService.acquireLock(testCellId, testUserId1) } returns user1LockSuccess
        every { cellLockService.acquireLock(testCellId, testUserId2) } returns user2LockSuccess
        
        every { cellLockService.releaseLock(testCellId, any()) } returns true
        
        // When
        // Set up concurrent execution
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(1)
        val results = mutableListOf<Cell?>()
        val exceptions = mutableListOf<Exception>()
        
        // First user
        executor.submit {
            try {
                latch.await() // Wait for the signal to start
                val result = cellService.updateCell(newCellData1, testUserId1)
                synchronized(results) {
                    results.add(result)
                }
            } catch (e: Exception) {
                synchronized(exceptions) {
                    exceptions.add(e)
                }
            }
        }
        
        // Second user
        executor.submit {
            try {
                latch.await() // Wait for the signal to start
                cellService.updateCell(newCellData2, testUserId2)
            } catch (e: Exception) {
                synchronized(exceptions) {
                    exceptions.add(e)
                }
            }
        }
        
        // Start both threads at the same time
        latch.countDown()
        
        // Wait for both threads to complete
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        
        // Then
        // Verify that one update succeeded and one failed with an exception
        assertEquals(1, results.size, "Only one update should succeed")
        assertEquals(1, exceptions.size, "One update should fail with an exception")
        assertTrue(exceptions[0] is IllegalStateException, "Exception should be IllegalStateException")
        
        // Verify the lock was acquired and released
        verify(exactly = 2) { cellLockService.acquireLock(testCellId, any()) }
        verify(exactly = 1) { cellLockService.releaseLock(testCellId, any()) }
        
        // Verify that one of the processors was called
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
    }

    @Test
    fun `test cell lock is taken before updating dependent cells`() {
        // Given
        val cellId = "1:1:1"
        val dependentCellId = "1:2:1"
        val userId = "user1"
        
        val cell = Cell(
            id = cellId,
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val dependentCell = Cell(
            id = dependentCellId,
            sheetId = 1L,
            row = 2,
            column = "1",
            data = "=A1+5",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "15",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { cellRedisRepository.getCell(cellId) } returns cell
        every { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) } returns cell.copy(data = "20", evaluatedValue = "20")
        
        // Mock the dependency service to return the dependent cell
        val dependency = mockk<CellDependency> {
            every { sourceCellId } returns dependentCellId
            every { targetCellId } returns cellId
        }
        
        every { cellDependencyService.getDependenciesByTargetCellId(cellId) } returns listOf(dependency)
        every { cellDependencyService.updateDependentCells(cellId, userId) } just runs
        
        // Verify locks are acquired and released in the correct order
        val lockOrder = mutableListOf<String>()
        
        every { cellLockService.acquireLock(cellId, userId) } answers {
            lockOrder.add("acquire:$cellId")
            true
        }
        
        every { cellLockService.releaseLock(cellId, userId) } answers {
            lockOrder.add("release:$cellId")
            true
        }
        
        // When
        cellService.updateCell(cell.copy(data = "20"), userId)
        
        // Then
        // Verify the lock was acquired for the cell being updated
        assertTrue(lockOrder.contains("acquire:$cellId"), "Lock should be acquired for the cell")
        
        // Verify the lock was released for the cell being updated
        assertTrue(lockOrder.contains("release:$cellId"), "Lock should be released for the cell")
        
        // Verify that the dependency service was called to update dependent cells
        verify(exactly = 1) { cellDependencyService.updateDependentCells(cellId, userId) }
    }

    @Test
    fun `test concurrent deletion of the same cell`() {
        // Given
        val cellId = "1:1:1"
        val userId1 = "user1"
        val userId2 = "user2"
        
        // Mock the cell lock service to simulate locking behavior
        every { cellLockService.acquireLock(cellId, userId1) } returns true
        every { cellLockService.acquireLock(cellId, userId2) } returns false
        
        every { cellLockService.releaseLock(cellId, userId1) } returns true
        
        every { cellRedisRepository.deleteCell(cellId) } just runs
        every { cellAsyncService.deleteCell(cellId) } just runs
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 0
        
        // When
        // Set up concurrent execution
        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(1)
        val exceptions = mutableListOf<Exception>()
        
        // First user
        executor.submit {
            try {
                latch.await() // Wait for the signal to start
                cellService.deleteCell(cellId, userId1)
            } catch (e: Exception) {
                synchronized(exceptions) {
                    exceptions.add(e)
                }
            }
        }
        
        // Second user
        executor.submit {
            try {
                latch.await() // Wait for the signal to start
                cellService.deleteCell(cellId, userId2)
            } catch (e: Exception) {
                synchronized(exceptions) {
                    exceptions.add(e)
                }
            }
        }
        
        // Start both threads at the same time
        latch.countDown()
        
        // Wait for both threads to complete
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        
        // Then
        // Verify that one deletion succeeded and one failed with an exception
        assertEquals(1, exceptions.size, "One deletion should fail with an exception")
        assertTrue(exceptions[0] is IllegalStateException, "Exception should be IllegalStateException")
        
        // Verify the lock was acquired and released
        verify(exactly = 2) { cellLockService.acquireLock(cellId, any()) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, any()) }
        
        // Verify that the cell was deleted once
        verify(exactly = 1) { cellRedisRepository.deleteCell(cellId) }
        verify(exactly = 1) { cellAsyncService.deleteCell(cellId) }
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
    }

    @Test
    fun `test complex expression evaluation with locking`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        val expression = "=SUM(A1:A5) + AVERAGE(B1:B3) * 2"
        
        val dependencies = listOf("1:1:1", "1:2:1", "1:3:1", "1:4:1", "1:5:1", "1:1:2", "1:2:2", "1:3:2")
        val result = Triple(DataType.EXPRESSION, "100", dependencies)
        
        every { expressionDataProcessor.processExpression(expression, 1L, 1, "1") } returns result
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 0
        every { cellDependencyService.createDependencies(any()) } returns emptyList()
        
        // Mock lock acquisition and release
        every { cellLockService.acquireLock(cellId, userId) } returns true
        every { cellLockService.releaseLock(cellId, userId) } returns true
        
        // When
        val evaluatedValue = cellService.evaluateExpression(cellId, expression, userId)
        
        // Then
        assertEquals("100", evaluatedValue)
        
        // Verify that the expression was processed
        verify(exactly = 1) { expressionDataProcessor.processExpression(expression, 1L, 1, "1") }
        
        // Verify that dependencies were updated
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
        verify(exactly = 1) { cellDependencyService.createDependencies(any()) }
    }

    @Test
    fun `test changing from primitive to expression data type with locking`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        val existingCell = Cell(
            id = cellId,
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val newCellData = existingCell.copy(
            data = "=A2+5",
            dataType = DataType.EXPRESSION
        )
        
        val updatedCell = newCellData.copy(
            evaluatedValue = "15",
            updatedAt = timestamp.plusSeconds(1)
        )
        
        every { cellRedisRepository.getCell(cellId) } returns existingCell
        every { expressionDataProcessor.processExistingCell(existingCell, newCellData, any(), userId) } returns updatedCell
        
        // Mock lock acquisition and release
        every { cellLockService.acquireLock(cellId, userId) } returns true
        every { cellLockService.releaseLock(cellId, userId) } returns true
        
        // When
        val result = cellService.updateCell(newCellData, userId)
        
        // Then
        assertEquals(updatedCell, result)
        
        // Verify that the expression processor was used
        verify(exactly = 1) { expressionDataProcessor.processExistingCell(existingCell, newCellData, any(), userId) }
        
        // Verify that locks were acquired and released
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
    }
}
