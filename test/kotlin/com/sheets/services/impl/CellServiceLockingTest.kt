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
        
        val newCell = Cell(
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
        
        every { cellLockService.acquireLock(cellId, userId) } returns true
        every { cellLockService.releaseLock(cellId, userId) } returns true
        
        every { cellRedisRepository.getCell(cellId) } returns null 
        every { cellRepository.findById(cellId) } returns null      

        every { primitiveDataProcessor.processNewCell(any(), any(), any()) } returns newCell
        
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs
        every { cellDependencyService.updateDependentCells(cellId, userId) } just runs

        // When
        cellService.updateCell(newCell, userId)

        // Then
        verifySequence {
            cellLockService.acquireLock(cellId, userId)
            cellRedisRepository.getCell(cellId)
            cellRepository.findById(cellId) // This is called within getCell if Redis is empty
            primitiveDataProcessor.processNewCell(any(), any(), any())
            cellRedisRepository.saveCell(any())
            cellAsyncService.saveCell(any())
            cellDependencyService.updateDependentCells(cellId, userId) // This is called after saving
            cellLockService.releaseLock(cellId, userId)
        }

        // Verify all the expected operations were called (redundant if sequence is correct, but good for clarity)
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
        verify(exactly = 1) { cellRedisRepository.getCell(cellId) }
        verify(exactly = 1) { cellRepository.findById(cellId) }
        verify(exactly = 1) { primitiveDataProcessor.processNewCell(any(), any(), any()) }
        verify(exactly = 1) { cellRedisRepository.saveCell(any()) }
        verify(exactly = 1) { cellAsyncService.saveCell(any()) }
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
        
        every { cellLockService.acquireLock(cellId, userId) } returns false
        
        // When/Then
        val exception = assertThrows<IllegalStateException> {
            cellService.updateCell(cell.copy(data = "20"), userId)
        }
        
        assertTrue(exception.message!!.contains("Could not acquire lock"), 
            "Exception should mention lock acquisition failure")
        
        verifySequence {
            cellLockService.acquireLock(cellId, userId)
        }
        
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 0) { cellRedisRepository.getCell(any()) }
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
        
        every { cellLockService.acquireLock(cellId, userId) } returns true
        every { cellRedisRepository.deleteCell(cellId) } just runs
        every { cellAsyncService.deleteCell(cellId) } just runs
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 0
        every { cellLockService.releaseLock(cellId, userId) } returns true
        
        // When
        cellService.deleteCell(cellId, userId)
        
        // Then
        verifySequence {
            cellLockService.acquireLock(cellId, userId)
            cellRedisRepository.deleteCell(cellId)
            cellAsyncService.deleteCell(cellId)
            cellDependencyService.deleteBySourceCellId(cellId)
            cellLockService.releaseLock(cellId, userId)
        }
        
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
        
        assertTrue(exception.message!!.contains("Could not acquire lock"), 
            "Exception should mention lock acquisition failure")
        
        verifySequence {
            cellLockService.acquireLock(cellId, userId)
        }
        
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 0) { cellRedisRepository.deleteCell(any()) }
        verify(exactly = 0) { cellAsyncService.deleteCell(any()) }
        verify(exactly = 0) { cellDependencyService.deleteBySourceCellId(any()) }
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
        every { cellRedisRepository.saveCell(updatedCell) } returns mockk()
        every { cellAsyncService.saveCell(updatedCell) } just runs
        every { cellDependencyService.updateDependentCells(cellId, userId) } just runs
        
        // When
        val result = cellService.updateCell(newCellData, userId)
        
        // Then
        assertEquals(updatedCell, result)
        
        verifySequence {
            cellLockService.acquireLock(cellId, userId)
            cellRedisRepository.getCell(cellId)
            expressionDataProcessor.processExistingCell(existingCell, newCellData, any(), userId)
            cellRedisRepository.saveCell(updatedCell)
            cellAsyncService.saveCell(updatedCell)
            cellDependencyService.updateDependentCells(cellId, userId)
            cellLockService.releaseLock(cellId, userId)
        }
        
        verify(exactly = 0) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
        verify(exactly = 1) { expressionDataProcessor.processExistingCell(existingCell, newCellData, any(), userId) }
        
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
        
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
        every { cellRedisRepository.saveCell(updatedCell) } returns mockk()
        every { cellAsyncService.saveCell(updatedCell) } just runs
        every { cellDependencyService.updateDependentCells(cellId, userId) } just runs
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 1
        
        // When
        val result = cellService.updateCell(newCellData, userId)
        
        // Then
        assertEquals(updatedCell, result)
        
        verifySequence {
            cellLockService.acquireLock(cellId, userId)
            cellRedisRepository.getCell(cellId)
            primitiveDataProcessor.processExistingCell(existingCell, newCellData, any(), userId)
            cellRedisRepository.saveCell(updatedCell)
            cellAsyncService.saveCell(updatedCell)
            cellDependencyService.updateDependentCells(cellId, userId)
            cellLockService.releaseLock(cellId, userId)
        }
        
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(existingCell, newCellData, any(), userId) }
        verify(exactly = 0) { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) }
        
        verify(exactly = 1) { cellLockService.acquireLock(cellId, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(cellId, userId) }
        
        verify(exactly = 1) { cellRedisRepository.saveCell(updatedCell) }
        verify(exactly = 1) { cellAsyncService.saveCell(updatedCell) }
        verify(exactly = 1) { cellDependencyService.updateDependentCells(cellId, userId) }
        
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
    }
    
    @Test
    fun `test updateDependentCells properly updates all dependent cells`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        val sheetId = "1"
        val sheetLockId = "sheet:$sheetId"
        
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
        every { cellLockService.acquireLock(sheetLockId, userId) } returns true
        every { cellLockService.releaseLock(sheetLockId, userId) } returns true
        
        every { cellLockService.acquireLock(dependentCellId1, userId) } returns true
        every { cellLockService.acquireLock(dependentCellId2, userId) } returns true
        every { cellLockService.releaseLock(dependentCellId1, userId) } returns true
        every { cellLockService.releaseLock(dependentCellId2, userId) } returns true
        
        every { cellRedisRepository.getCell(dependentCellId1) } returns dependentCell1
        every { cellRedisRepository.getCell(dependentCellId2) } returns dependentCell2
        
        every { expressionParser.parse("=A1+10") } returns listOf("A1")
        every { expressionParser.parse("=A1*2") } returns listOf("A1")
        
        every { expressionEvaluator.evaluate("=A1+10", any()) } returns "30"
        every { expressionEvaluator.evaluate("=A1*2", any()) } returns "40"
        
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs
        
        every { cellDependencyService.updateDependentCells(dependentCellId1, userId) } just runs
        every { cellDependencyService.updateDependentCells(dependentCellId2, userId) } just runs
        
        // When
        cellDependencyService.updateDependentCells(cellId, userId)
        
        // Then
        verifySequence {
            cellDependencyService.getDependenciesByTargetCellId(cellId)
            cellLockService.acquireLock(sheetLockId, userId)
            cellRedisRepository.getCell(dependentCellId1)
            cellRedisRepository.getCell(dependentCellId2)
            cellLockService.acquireLock(dependentCellId1, userId)
            cellLockService.acquireLock(dependentCellId2, userId)
            expressionParser.parse("=A1+10")
            expressionParser.parse("=A1*2")
            expressionEvaluator.evaluate("=A1+10", any())
            expressionEvaluator.evaluate("=A1*2", any())
            cellRedisRepository.saveCell(updatedDependentCell1)
            cellRedisRepository.saveCell(updatedDependentCell2)
            cellAsyncService.saveCell(updatedDependentCell1)
            cellAsyncService.saveCell(updatedDependentCell2)
            cellDependencyService.updateDependentCells(dependentCellId1, userId)
            cellDependencyService.updateDependentCells(dependentCellId2, userId)
            cellLockService.releaseLock(dependentCellId1, userId)
            cellLockService.releaseLock(dependentCellId2, userId)
            cellLockService.releaseLock(sheetLockId, userId)
        }
        
        verify(exactly = 1) { cellDependencyService.getDependenciesByTargetCellId(cellId) }
        verify(exactly = 1) { cellLockService.acquireLock(sheetLockId, userId) }
        
        verify(exactly = 1) { cellRedisRepository.getCell(dependentCellId1) }
        verify(exactly = 1) { cellRedisRepository.getCell(dependentCellId2) }
        
        verify(exactly = 1) { cellLockService.acquireLock(dependentCellId1, userId) }
        verify(exactly = 1) { cellLockService.acquireLock(dependentCellId2, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(dependentCellId1, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(dependentCellId2, userId) }
        
        verify(exactly = 1) { expressionParser.parse("=A1+10") }
        verify(exactly = 1) { expressionParser.parse("=A1*2") }
        verify(exactly = 1) { expressionEvaluator.evaluate("=A1+10", any()) }
        verify(exactly = 1) { expressionEvaluator.evaluate("=A1*2", any()) }
        
        verify(atLeast = 2) { cellRedisRepository.saveCell(any()) }
        verify(atLeast = 2) { cellAsyncService.saveCell(any()) }
        
        verify(exactly = 1) { cellDependencyService.updateDependentCells(dependentCellId1, userId) }
        verify(exactly = 1) { cellDependencyService.updateDependentCells(dependentCellId2, userId) }
        verify(exactly = 1) { cellLockService.releaseLock(sheetLockId, userId) }
    }
    
    @Test
    fun `test complex expression evaluation with multiple dependencies`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        val expression = "=SUM(A1:A5) + AVERAGE(B1:B3) * 2"
        
        val dependencies = listOf("1:1:1", "1:2:1", "1:3:1", "1:4:1", "1:5:1", "1:1:2", "1:2:2", "1:3:2")
        val result = Triple(DataType.EXPRESSION, "100", dependencies)
        
        every { expressionDataProcessor.processExpression(expression, 1L, 1, "1") } returns result
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 0
        
        val dependencySlot = slot<List<CellDependency>>()
        every { cellDependencyService.createDependencies(capture(dependencySlot)) } returns emptyList()
        
        // When
        val evaluatedValue = cellService.evaluateExpression(cellId, expression, userId)
        
        // Then
        assertEquals("100", evaluatedValue)
        
        verifySequence {
            expressionDataProcessor.processExpression(expression, 1L, 1, "1")
            cellDependencyService.deleteBySourceCellId(cellId)
            cellDependencyService.createDependencies(any())
        }
        
        verify(exactly = 1) { expressionDataProcessor.processExpression(expression, 1L, 1, "1") }
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
        verify(exactly = 1) { cellDependencyService.createDependencies(any()) }
        
        assertEquals(dependencies.size, dependencySlot.captured.size, 
            "Should create the correct number of dependencies")
    }
}
