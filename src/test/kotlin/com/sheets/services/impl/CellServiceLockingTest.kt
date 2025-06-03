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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CellServiceLockingTest {

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
    fun `test updateCell acquires lock before processing`() {
        // Given
        val cellId = "1:1:1"
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
        
        val updatedCell = cell.copy(
            data = "20",
            evaluatedValue = "20",
            updatedAt = timestamp.plusSeconds(1)
        )
        
        every { cellRedisRepository.getCell(cellId) } returns cell
        every { primitiveDataProcessor.processExistingCell(cell, any(), any(), userId) } returns updatedCell
        every { cellDependencyService.updateDependentCells(cellId, userId) } just runs
        
        // Capture the order of operations
        val operationOrder = mutableListOf<String>()
        
        every { cellLockService.acquireLock(cellId, userId) } answers {
            operationOrder.add("acquireLock")
            true
        }
        
        every { cellRedisRepository.saveCell(any()) } answers {
            operationOrder.add("saveCell")
            updatedCell
        }
        
        every { cellAsyncService.saveCell(any()) } answers {
            operationOrder.add("asyncSaveCell")
            Unit
        }
        
        every { cellLockService.releaseLock(cellId, userId) } answers {
            operationOrder.add("releaseLock")
            true
        }
        
        // When
        cellService.updateCell(updatedCell, userId)
        
        // Then
        // Verify the correct order of operations
        assertEquals("acquireLock", operationOrder[0], "Lock should be acquired first")
        assertTrue(operationOrder.indexOf("saveCell") < operationOrder.indexOf("releaseLock"), 
            "Cell should be saved before releasing the lock")
        assertTrue(operationOrder.indexOf("asyncSaveCell") < operationOrder.indexOf("releaseLock"), 
            "Async save should happen before releasing the lock")
        assertEquals("releaseLock", operationOrder.last(), "Lock should be released last")
        
        // Verify all the expected operations were called
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellRedisRepository.saveCell(any()) }
        verify(exactly = 1) { cellAsyncService.saveCell(any()) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
        verify(exactly = 1) { cellDependencyService.updateDependentCells(cellId, userId) }
    }
    
    @Test
    fun `test updateCell throws exception when lock cannot be acquired`() {
        // Given
        val cellId = "1:1:1"
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
        
        every { cellRedisRepository.getCell(cellId) } returns cell
        every { cellLockService.acquireLock(cellId, userId) } returns false
        
        // When/Then
        val exception = assertThrows<IllegalStateException> {
            cellService.updateCell(cell.copy(data = "20"), userId)
        }
        
        // Verify the exception message
        assertTrue(exception.message!!.contains("Could not acquire lock"), 
            "Exception should mention lock acquisition failure")
        
        // Verify that no further processing occurred
        verify(exactly = 0) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
        verify(exactly = 0) { cellRedisRepository.saveCell(any()) }
        verify(exactly = 0) { cellAsyncService.saveCell(any()) }
        verify(exactly = 0) { cellLockService.releaseLock(any(), any()) }
    }
    
    @Test
    fun `test deleteCell acquires lock before deleting`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        // Capture the order of operations
        val operationOrder = mutableListOf<String>()
        
        every { cellLockService.acquireLock(cellId, userId) } answers {
            operationOrder.add("acquireLock")
            true
        }
        
        every { cellRedisRepository.deleteCell(cellId) } answers {
            operationOrder.add("deleteCell")
            Unit
        }
        
        every { cellAsyncService.deleteCell(cellId) } answers {
            operationOrder.add("asyncDeleteCell")
            Unit
        }
        
        every { cellDependencyService.deleteBySourceCellId(cellId) } answers {
            operationOrder.add("deleteDependencies")
            0
        }
        
        every { cellLockService.releaseLock(cellId, userId) } answers {
            operationOrder.add("releaseLock")
            true
        }
        
        // When
        cellService.deleteCell(cellId, userId)
        
        // Then
        // Verify the correct order of operations
        assertEquals("acquireLock", operationOrder[0], "Lock should be acquired first")
        assertTrue(operationOrder.indexOf("deleteCell") < operationOrder.indexOf("releaseLock"), 
            "Cell should be deleted before releasing the lock")
        assertTrue(operationOrder.indexOf("asyncDeleteCell") < operationOrder.indexOf("releaseLock"), 
            "Async delete should happen before releasing the lock")
        assertEquals("releaseLock", operationOrder.last(), "Lock should be released last")
        
        // Verify all the expected operations were called
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellRedisRepository.deleteCell(cellId) }
        verify(exactly = 1) { cellAsyncService.deleteCell(cellId) }
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
    }
    
    @Test
    fun `test deleteCell throws exception when lock cannot be acquired`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        every { cellLockService.acquireLock(cellId, userId) } returns false
        
        // When/Then
        val exception = assertThrows<IllegalStateException> {
            cellService.deleteCell(cellId, userId)
        }
        
        // Verify the exception message
        assertTrue(exception.message!!.contains("Could not acquire lock"), 
            "Exception should mention lock acquisition failure")
        
        // Verify that no further processing occurred
        verify(exactly = 0) { cellRedisRepository.deleteCell(any()) }
        verify(exactly = 0) { cellAsyncService.deleteCell(any()) }
        verify(exactly = 0) { cellDependencyService.deleteBySourceCellId(any()) }
        verify(exactly = 0) { cellLockService.releaseLock(any(), any()) }
    }
    
    @Test
    fun `test evaluateExpression acquires lock before processing`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        val expression = "=A2+B3"
        
        val dependencies = listOf("1:2:1", "1:3:2")
        val result = Triple(DataType.EXPRESSION, "30", dependencies)
        
        // Capture the order of operations
        val operationOrder = mutableListOf<String>()
        
        every { cellLockService.acquireLock(cellId, userId) } answers {
            operationOrder.add("acquireLock")
            true
        }
        
        every { expressionDataProcessor.processExpression(expression, 1L, 1, "1") } answers {
            operationOrder.add("processExpression")
            result
        }
        
        every { cellDependencyService.deleteBySourceCellId(cellId) } answers {
            operationOrder.add("deleteDependencies")
            0
        }
        
        every { cellDependencyService.createDependencies(any()) } answers {
            operationOrder.add("createDependencies")
            emptyList()
        }
        
        every { cellLockService.releaseLock(cellId, userId) } answers {
            operationOrder.add("releaseLock")
            true
        }
        
        // When
        val evaluatedValue = cellService.evaluateExpression(cellId, expression, userId)
        
        // Then
        assertEquals("30", evaluatedValue)
        
        // Verify the correct order of operations
        assertEquals("acquireLock", operationOrder[0], "Lock should be acquired first")
        assertTrue(operationOrder.indexOf("processExpression") < operationOrder.indexOf("releaseLock"), 
            "Expression should be processed before releasing the lock")
        assertTrue(operationOrder.indexOf("deleteDependencies") < operationOrder.indexOf("createDependencies"), 
            "Dependencies should be deleted before creating new ones")
        assertEquals("releaseLock", operationOrder.last(), "Lock should be released last")
        
        // Verify all the expected operations were called
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { expressionDataProcessor.processExpression(expression, 1L, 1, "1") }
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
        verify(exactly = 1) { cellDependencyService.createDependencies(any()) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
    }
    
    @Test
    fun `test evaluateExpression throws exception when lock cannot be acquired`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        val expression = "=A2+B3"
        
        every { cellLockService.acquireLock(cellId, userId) } returns false
        
        // When/Then
        val exception = assertThrows<IllegalStateException> {
            cellService.evaluateExpression(cellId, expression, userId)
        }
        
        // Verify the exception message
        assertTrue(exception.message!!.contains("Could not acquire lock"), 
            "Exception should mention lock acquisition failure")
        
        // Verify that no further processing occurred
        verify(exactly = 0) { expressionDataProcessor.processExpression(any(), any(), any(), any()) }
        verify(exactly = 0) { cellDependencyService.deleteBySourceCellId(any()) }
        verify(exactly = 0) { cellDependencyService.createDependencies(any()) }
        verify(exactly = 0) { cellLockService.releaseLock(any(), any()) }
    }
    
    @Test
    fun `test updateCell with primitive to expression type change`() {
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
        every { cellLockService.acquireLock(cellId, userId) } returns true
        every { cellLockService.releaseLock(cellId, userId) } returns true
        
        every { expressionDataProcessor.processExistingCell(existingCell, newCellData, any(), userId) } returns updatedCell
        every { cellRedisRepository.saveCell(updatedCell) } returns updatedCell
        every { cellAsyncService.saveCell(updatedCell) } just runs
        every { cellDependencyService.updateDependentCells(cellId, userId) } just runs
        
        // When
        val result = cellService.updateCell(newCellData, userId)
        
        // Then
        assertEquals(updatedCell, result)
        
        // Verify that the expression processor was used instead of primitive processor
        verify(exactly = 0) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
        verify(exactly = 1) { expressionDataProcessor.processExistingCell(existingCell, newCellData, any(), userId) }
        
        // Verify that locks were acquired and released
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
        
        // Verify that the cell was saved and dependent cells were updated
        verify(exactly = 1) { cellRedisRepository.saveCell(updatedCell) }
        verify(exactly = 1) { cellAsyncService.saveCell(updatedCell) }
        verify(exactly = 1) { cellDependencyService.updateDependentCells(cellId, userId) }
    }
    
    @Test
    fun `test updateCell with expression to primitive type change`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        val existingCell = Cell(
            id = cellId,
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "=A2+5",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "15",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val newCellData = existingCell.copy(
            data = "20",
            dataType = DataType.PRIMITIVE
        )
        
        val updatedCell = newCellData.copy(
            evaluatedValue = "20",
            updatedAt = timestamp.plusSeconds(1)
        )
        
        every { cellRedisRepository.getCell(cellId) } returns existingCell
        every { cellLockService.acquireLock(cellId, userId) } returns true
        every { cellLockService.releaseLock(cellId, userId) } returns true
        
        every { primitiveDataProcessor.processExistingCell(existingCell, newCellData, any(), userId) } returns updatedCell
        every { cellRedisRepository.saveCell(updatedCell) } returns updatedCell
        every { cellAsyncService.saveCell(updatedCell) } just runs
        every { cellDependencyService.updateDependentCells(cellId, userId) } just runs
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 1
        
        // When
        val result = cellService.updateCell(newCellData, userId)
        
        // Then
        assertEquals(updatedCell, result)
        
        // Verify that the primitive processor was used instead of expression processor
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(existingCell, newCellData, any(), userId) }
        verify(exactly = 0) { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) }
        
        // Verify that locks were acquired and released
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
        
        // Verify that the cell was saved and dependent cells were updated
        verify(exactly = 1) { cellRedisRepository.saveCell(updatedCell) }
        verify(exactly = 1) { cellAsyncService.saveCell(updatedCell) }
        verify(exactly = 1) { cellDependencyService.updateDependentCells(cellId, userId) }
        
        // Verify that source dependencies were deleted since we're changing from expression to primitive
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
    }
    
    @Test
    fun `test updateDependentCells properly updates all dependent cells`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        val dependentCellId1 = "1:2:1"
        val dependentCellId2 = "1:3:1"
        
        val timestamp = Instant.now()
        
        val dependencies = listOf(
            CellDependency(
                id = "dep1",
                sheetId = 1L,
                sourceCellId = dependentCellId1,
                targetCellId = cellId,
                createdAt = timestamp,
                updatedAt = timestamp
            ),
            CellDependency(
                id = "dep2",
                sheetId = 1L,
                sourceCellId = dependentCellId2,
                targetCellId = cellId,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
        
        val dependentCell1 = Cell(
            id = dependentCellId1,
            sheetId = 1L,
            row = 2,
            column = "1",
            data = "=A1+10",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "20",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val dependentCell2 = Cell(
            id = dependentCellId2,
            sheetId = 1L,
            row = 3,
            column = "1",
            data = "=A1*2",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "20",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val updatedDependentCell1 = dependentCell1.copy(
            evaluatedValue = "30",
            updatedAt = timestamp.plusSeconds(1)
        )
        
        val updatedDependentCell2 = dependentCell2.copy(
            evaluatedValue = "40",
            updatedAt = timestamp.plusSeconds(1)
        )
        
        every { cellDependencyService.getDependenciesByTargetCellId(cellId) } returns dependencies
        every { cellRedisRepository.getCell(dependentCellId1) } returns dependentCell1
        every { cellRedisRepository.getCell(dependentCellId2) } returns dependentCell2
        
        every { cellLockService.acquireLock(dependentCellId1, userId) } returns true
        every { cellLockService.acquireLock(dependentCellId2, userId) } returns true
        every { cellLockService.releaseLock(dependentCellId1, userId) } returns true
        every { cellLockService.releaseLock(dependentCellId2, userId) } returns true
        
        every { expressionParser.parse("=A1+10") } returns listOf("A1")
        every { expressionParser.parse("=A1*2") } returns listOf("A1")
        
        every { expressionEvaluator.evaluate("=A1+10", any()) } returns "30"
        every { expressionEvaluator.evaluate("=A1*2", any()) } returns "40"
        
        every { cellRedisRepository.saveCell(any()) } answers { firstArg() }
        every { cellAsyncService.saveCell(any()) } just runs
        
        every { cellDependencyService.updateDependentCells(dependentCellId1, userId) } just runs
        every { cellDependencyService.updateDependentCells(dependentCellId2, userId) } just runs
        
        // When
        cellDependencyService.updateDependentCells(cellId, userId)
        
        // Then
        // Verify that dependencies were retrieved
        verify(exactly = 1) { cellDependencyService.getDependenciesByTargetCellId(cellId) }
        
        // Verify that dependent cells were retrieved
        verify(exactly = 1) { cellRedisRepository.getCell(dependentCellId1) }
        verify(exactly = 1) { cellRedisRepository.getCell(dependentCellId2) }
        
        // Verify that locks were acquired and released for each dependent cell
        verify(exactly = 1) { cellLockService.acquireLock(dependentCellId1, userId) }
        verify(exactly = 1) { cellLockService.acquireLock(dependentCellId2, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(dependentCellId1, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(dependentCellId2, userId) }
        
        // Verify that expressions were parsed and evaluated
        verify(exactly = 1) { expressionParser.parse("=A1+10") }
        verify(exactly = 1) { expressionParser.parse("=A1*2") }
        verify(exactly = 1) { expressionEvaluator.evaluate("=A1+10", any()) }
        verify(exactly = 1) { expressionEvaluator.evaluate("=A1*2", any()) }
        
        // Verify that dependent cells were saved
        verify(atLeast = 2) { cellRedisRepository.saveCell(any()) }
        verify(atLeast = 2) { cellAsyncService.saveCell(any()) }
        
        // Verify that further dependent cells were updated recursively
        verify(exactly = 1) { cellDependencyService.updateDependentCells(dependentCellId1, userId) }
        verify(exactly = 1) { cellDependencyService.updateDependentCells(dependentCellId2, userId) }
    }
    
    @Test
    fun `test complex expression evaluation with multiple dependencies`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        val expression = "=SUM(A1:A5) + AVERAGE(B1:B3) * 2"
        
        val dependencies = listOf("1:1:1", "1:2:1", "1:3:1", "1:4:1", "1:5:1", "1:1:2", "1:2:2", "1:3:2")
        val result = Triple(DataType.EXPRESSION, "100", dependencies)
        
        every { cellLockService.acquireLock(cellId, userId) } returns true
        every { cellLockService.releaseLock(cellId, userId) } returns true
        
        every { expressionDataProcessor.processExpression(expression, 1L, 1, "1") } returns result
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 0
        
        val dependencySlot = slot<List<CellDependency>>()
        every { cellDependencyService.createDependencies(capture(dependencySlot)) } returns emptyList()
        
        // When
        val evaluatedValue = cellService.evaluateExpression(cellId, expression, userId)
        
        // Then
        assertEquals("100", evaluatedValue)
        
        // Verify that the expression was processed
        verify(exactly = 1) { expressionDataProcessor.processExpression(expression, 1L, 1, "1") }
        
        // Verify that dependencies were updated
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
        verify(exactly = 1) { cellDependencyService.createDependencies(any()) }
        
        // Verify that the correct dependencies were created
        assertEquals(dependencies.size, dependencySlot.captured.size, 
            "Should create the correct number of dependencies")
        
        // Verify that locks were acquired and released
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
    }
}
