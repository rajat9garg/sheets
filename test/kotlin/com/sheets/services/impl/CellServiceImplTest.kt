package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.models.domain.CellDependency
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRedisRepository
import com.sheets.repositories.CellRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.`cell-management`.CellUtils
import com.sheets.services.`cell-management`.ExpressionDataProcessor
import com.sheets.services.`cell-management`.PrimitiveDataProcessor
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import org.springframework.scheduling.annotation.Async
import java.util.concurrent.CompletableFuture

class CellServiceImplTest {

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

    @InjectMockKs
    private lateinit var cellService: CellServiceImpl

    private val testCellId = "1:1:1"
    private val testSheetId = 1L
    private val testUserId = "user1"
    private val timestamp = Instant.now()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `test getCell returns cell from Redis cache when available`() {
        // Given
        val cell = Cell(
            id = testCellId,
            sheetId = testSheetId,
            row = 1,
            column = "1",
            data = "Test Data",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "Test Data",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { cellRedisRepository.getCell(testCellId) } returns cell
        
        // When
        val result = cellService.getCell(testCellId)
        
        // Then
        verify(exactly = 1) { cellRedisRepository.getCell(testCellId) }
        verify(exactly = 0) { cellRepository.findById(any()) }
        assertEquals(cell, result)
    }
    
    @Test
    fun `test getCell fetches from MongoDB and caches in Redis when not in cache`() {
        // Given
        val cell = Cell(
            id = testCellId,
            sheetId = testSheetId,
            row = 1,
            column = "1",
            data = "Test Data",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "Test Data",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns cell
        every { cellRedisRepository.saveCell(cell) } just runs
        
        // When
        val result = cellService.getCell(testCellId)
        
        // Then
        verify(exactly = 1) { cellRedisRepository.getCell(testCellId) }
        verify(exactly = 1) { cellRepository.findById(testCellId) }
        verify(exactly = 1) { cellRedisRepository.saveCell(cell) }
        assertEquals(cell, result)
    }
    
    @Test
    fun `test getCell returns null when cell not found in Redis or MongoDB`() {
        // Given
        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        
        // When
        val result = cellService.getCell(testCellId)
        
        // Then
        verify(exactly = 1) { cellRedisRepository.getCell(testCellId) }
        verify(exactly = 1) { cellRepository.findById(testCellId) }
        assertNull(result)
    }
    
    @Test
    fun `test getCellsBySheetId returns cells from Redis when available`() {
        // Given
        val cells = listOf(
            Cell(
                id = "1:1:1",
                sheetId = testSheetId,
                row = 1,
                column = "1",
                data = "Data 1",
                dataType = DataType.PRIMITIVE,
                evaluatedValue = "Data 1",
                createdAt = timestamp,
                updatedAt = timestamp
            ),
            Cell(
                id = "1:1:2",
                sheetId = testSheetId,
                row = 1,
                column = "2",
                data = "Data 2",
                dataType = DataType.PRIMITIVE,
                evaluatedValue = "Data 2",
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
        
        every { cellRedisRepository.getCellsBySheetId(testSheetId) } returns cells
        
        // When
        val result = cellService.getCellsBySheetId(testSheetId)
        
        // Then
        verify(exactly = 1) { cellRedisRepository.getCellsBySheetId(testSheetId) }
        verify(exactly = 0) { cellRepository.findBySheetId(any()) }
        assertEquals(cells, result)
    }
    
    @Test
    fun `test getCellsBySheetId fetches from MongoDB and caches in Redis when not in cache`() {
        // Given
        val cells = listOf(
            Cell(
                id = "1:1:1",
                sheetId = testSheetId,
                row = 1,
                column = "1",
                data = "Data 1",
                dataType = DataType.PRIMITIVE,
                evaluatedValue = "Data 1",
                createdAt = timestamp,
                updatedAt = timestamp
            ),
            Cell(
                id = "1:1:2",
                sheetId = testSheetId,
                row = 1,
                column = "2",
                data = "Data 2",
                dataType = DataType.PRIMITIVE,
                evaluatedValue = "Data 2",
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
        
        every { cellRedisRepository.getCellsBySheetId(testSheetId) } returns emptyList()
        every { cellRepository.findBySheetId(testSheetId) } returns cells
        every { cellRedisRepository.saveCell(any()) } just runs
        
        // When
        val result = cellService.getCellsBySheetId(testSheetId)
        
        // Then
        verify(exactly = 1) { cellRedisRepository.getCellsBySheetId(testSheetId) }
        verify(exactly = 1) { cellRepository.findBySheetId(testSheetId) }
        verify(exactly = 2) { cellRedisRepository.saveCell(any()) }
        assertEquals(cells, result)
    }
    
    @Test
    fun `test updateCell creates new primitive cell when cell does not exist`() {
        // Given
        val newCell = Cell(
            id = testCellId,
            sheetId = testSheetId,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val processedCell = newCell.copy(
            evaluatedValue = "10",
            dataType = DataType.PRIMITIVE
        )
        
        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { primitiveDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        
        // When
        val result = cellService.updateCell(newCell, testUserId)
        
        // Then
        verify(exactly = 1) { primitiveDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }
    
    @Test
    fun `test updateCell creates new expression cell when cell does not exist`() {
        // Given
        val newCell = Cell(
            id = testCellId,
            sheetId = testSheetId,
            row = 1,
            column = "1",
            data = "=A1+10",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val processedCell = newCell.copy(
            evaluatedValue = "10",
            dataType = DataType.EXPRESSION
        )
        
        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        
        // When
        val result = cellService.updateCell(newCell, testUserId)
        
        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }
    
    @Test
    fun `test updateCell updates existing primitive cell`() {
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
        
        val newCellData = existingCell.copy(
            data = "20"
        )
        
        val updatedCell = newCellData.copy(
            evaluatedValue = "20"
        )
        
        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        
        // When
        val result = cellService.updateCell(newCellData, testUserId)
        
        // Then
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
        assertEquals(updatedCell, result)
    }
    
    @Test
    fun `test updateCell updates existing expression cell`() {
        // Given
        val existingCell = Cell(
            id = testCellId,
            sheetId = testSheetId,
            row = 1,
            column = "1",
            data = "=A2+10",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "20",
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        val newCellData = existingCell.copy(
            data = "=A2+20"
        )
        
        val updatedCell = newCellData.copy(
            evaluatedValue = "30"
        )
        
        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        
        // When
        val result = cellService.updateCell(newCellData, testUserId)
        
        // Then
        verify(exactly = 1) { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) }
        assertEquals(updatedCell, result)
    }
    
    @Test
    fun `test deleteCell deletes cell and dependencies`() {
        // Given
        every { cellLockService.acquireLock(testCellId, testUserId) } returns true
        every { cellLockService.releaseLock(testCellId, testUserId) } returns true
        every { cellRedisRepository.deleteCell(testCellId) } just runs
        every { cellAsyncService.deleteCell(any()) } answers { CompletableFuture.completedFuture(null) }
        every { cellDependencyService.deleteBySourceCellId(testCellId) } returns 1
        
        // When
        cellService.deleteCell(testCellId, testUserId)
        
        // Then
        verify(exactly = 1) { cellLockService.acquireLock(testCellId, testUserId) }
        verify(exactly = 1) { cellRedisRepository.deleteCell(testCellId) }
        verify(exactly = 1) { cellAsyncService.deleteCell(any()) }
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(testCellId) }
        verify(exactly = 1) { cellLockService.releaseLock(testCellId, testUserId) }
    }
    
    @Test
    fun `test deleteCell throws exception when lock cannot be acquired`() {
        // Given
        every { cellLockService.acquireLock(testCellId, testUserId) } returns false
        
        // When/Then
        assertThrows<IllegalStateException> {
            cellService.deleteCell(testCellId, testUserId)
        }
        
        verify(exactly = 0) { cellRedisRepository.deleteCell(any()) }
        verify(exactly = 0) { cellAsyncService.deleteCell(any()) }
        verify(exactly = 0) { cellDependencyService.deleteBySourceCellId(any()) }
    }
    
    @Test
    fun `test evaluateExpression processes expression and updates dependencies`() {
        // Given
        val cellId = "1:1:1"
        val expression = "=A2+10"
        val userId = "user1"
        val dependencies = listOf("1:2:1")
        val now = Instant.now()
        
        val result = Triple(DataType.EXPRESSION, "20", dependencies)
        
        every { expressionDataProcessor.processExpression(expression, 1L, 1, "1") } returns result
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 1
        val cellDependencies = listOf(
            CellDependency(
                id = "dep1",
                sourceCellId = cellId,
                targetCellId = dependencies[0],
                sheetId = testSheetId,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
        every { cellDependencyService.createDependencies(any()) } returns cellDependencies
        
        // When
        val evaluatedValue = cellService.evaluateExpression(cellId, expression, userId)
        
        // Then
        verify(exactly = 1) { expressionDataProcessor.processExpression(expression, 1L, 1, "1") }
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
        verify(exactly = 1) { cellDependencyService.createDependencies(any()) }
        assertEquals("20", evaluatedValue)
    }
    
    @Test
    fun `test evaluateExpression throws exception for invalid cell ID format`() {
        // Given
        val invalidCellId = "invalid"
        val expression = "=A2+10"
        val userId = "user1"
        
        // When/Then
        assertThrows<IllegalArgumentException> {
            cellService.evaluateExpression(invalidCellId, expression, userId)
        }
    }
    
    @Test
    fun `test evaluateExpression does not create dependencies when none exist`() {
        // Given
        val cellId = "1:1:1"
        val expression = "10" // Not an expression, no dependencies
        val userId = "user1"
        val dependencies = emptyList<String>()
        
        val result = Triple(DataType.PRIMITIVE, "10", dependencies)
        
        every { expressionDataProcessor.processExpression(expression, 1L, 1, "1") } returns result
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 1
        
        // When
        val evaluatedValue = cellService.evaluateExpression(cellId, expression, userId)
        
        // Then
        verify(exactly = 1) { expressionDataProcessor.processExpression(expression, 1L, 1, "1") }
        verify(exactly = 1) { cellDependencyService.deleteBySourceCellId(cellId) }
        verify(exactly = 0) { cellDependencyService.createDependencies(any()) }
        assertEquals("10", evaluatedValue)
    }
}
