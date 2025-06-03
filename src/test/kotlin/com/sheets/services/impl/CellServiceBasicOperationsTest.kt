package com.sheets.services.impl

import com.sheets.exceptions.CircularReferenceException
import com.sheets.exceptions.CellLockException
import com.sheets.exceptions.SheetLockException
import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRedisRepository
import com.sheets.repositories.CellRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import com.sheets.services.CellLockService
import com.sheets.services.`cell-management`.ExpressionDataProcessor
import com.sheets.services.`cell-management`.PrimitiveDataProcessor
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.ExpressionParser
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.*
import java.time.Instant
import java.util.concurrent.CompletableFuture

/**
 * Test Suite for CellServiceImpl Basic Operations
 * 
 * Test Categories:
 * 1. Cell Retrieval Tests - Tests for getCell method
 * 2. Sheet Cell Retrieval Tests - Tests for getCellsBySheetId method
 * 3. Cell Creation Tests - Tests for creating new cells
 * 4. Cell Update Tests - Tests for updating existing cells
 * 5. Cell Deletion Tests - Tests for deleting cells
 */
class CellServiceBasicOperationsTest {

    @MockK
    private lateinit var cellRepository: CellRepository

    @MockK
    private lateinit var cellRedisRepository: CellRedisRepository

    @MockK
    private lateinit var cellAsyncService: CellAsyncService

    @MockK
    private lateinit var cellLockService: CellLockService

    @MockK
    private lateinit var cellDependencyService: CellDependencyService

    @MockK
    private lateinit var expressionParser: ExpressionParser

    @MockK
    private lateinit var expressionDataProcessor: ExpressionDataProcessor

    @MockK
    private lateinit var primitiveDataProcessor: PrimitiveDataProcessor

    @MockK
    private lateinit var circularDependencyDetector: CircularDependencyDetector

    private lateinit var cellService: CellServiceImpl

    private val testCellId = "1:1:A"
    private val testSheetId = 1L
    private val testUserId = "user1"
    private val timestamp = Instant.now()

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        
        cellService = CellServiceImpl(
            cellRepository,
            cellRedisRepository,
            cellAsyncService,
            cellLockService,
            cellDependencyService,
            expressionParser,
            expressionDataProcessor,
            primitiveDataProcessor,
            circularDependencyDetector
        )
        
        // Mock sheet lock acquisition for all tests
        every { cellLockService.acquireSheetLock(any(), any(), any()) } returns true
        every { cellLockService.releaseSheetLock(any(), any()) } returns true
        every { cellLockService.getSheetLockOwner(any()) } returns null
    }

    // Helper function to create test cells
    private fun createTestCell(
        id: String,
        data: String,
        dataType: DataType,
        evaluatedValue: String = data,
        row: Int = 1,
        column: String = "A"
    ): Cell {
        val parts = id.split(":")
        val sheetId = parts[0].toLong()
        
        return Cell(
            id = id,
            sheetId = sheetId,
            row = row,
            column = column,
            data = data,
            dataType = dataType,
            evaluatedValue = evaluatedValue,
            createdAt = timestamp,
            updatedAt = timestamp
        )
    }

    // ===== 1. Cell Retrieval Tests =====

    @Test
    fun `test getCell returns cell from Redis when available`() {
        // Given
        val cell = createTestCell(testCellId, "test data", DataType.PRIMITIVE)
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
        val cell = createTestCell(testCellId, "test data", DataType.PRIMITIVE)
        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns cell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        
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
            createTestCell("1:1:A", "data1", DataType.PRIMITIVE),
            createTestCell("1:2:B", "data2", DataType.PRIMITIVE)
        )
        every { cellRedisRepository.getCell("1:1:A") } returns cells[0]
        every { cellRedisRepository.getCell("1:2:B") } returns cells[1]
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellRepository.findBySheetId(testSheetId) } returns cells
        
        // When
        val result = cellService.getCellsBySheetId(testSheetId)
        
        // Then
        verify(exactly = 1) { cellRepository.findBySheetId(testSheetId) }
        verify(exactly = 2) { cellRedisRepository.saveCell(any()) }
        assertEquals(cells, result)
    }
    
    @Test
    fun `test getCellsBySheetId fetches from MongoDB and caches in Redis when not in cache`() {
        // Given
        val cells = listOf(
            createTestCell("1:1:A", "data1", DataType.PRIMITIVE),
            createTestCell("1:2:B", "data2", DataType.PRIMITIVE)
        )
        every { cellRepository.findBySheetId(testSheetId) } returns cells
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        
        // When
        val result = cellService.getCellsBySheetId(testSheetId)
        
        // Then
        verify(exactly = 1) { cellRepository.findBySheetId(testSheetId) }
        verify(exactly = 2) { cellRedisRepository.saveCell(any()) }
        assertEquals(cells, result)
    }
    
    @Test
    fun `test getCellsBySheetId returns empty list when no cells found`() {
        // Given
        every { cellRepository.findBySheetId(testSheetId) } returns emptyList()
        
        // When
        val result = cellService.getCellsBySheetId(testSheetId)
        
        // Then
        verify(exactly = 1) { cellRepository.findBySheetId(testSheetId) }
        verify(exactly = 0) { cellRedisRepository.saveCell(any()) }
        assertTrue(result.isEmpty())
    }
    
    @Test
    fun `test getCellsBySheetId with non-existent sheet ID`() {
        // Given
        val nonExistentSheetId = 999L
        every { cellRepository.findBySheetId(nonExistentSheetId) } returns emptyList()
        
        // When
        val result = cellService.getCellsBySheetId(nonExistentSheetId)
        
        // Then
        verify(exactly = 1) { cellRepository.findBySheetId(nonExistentSheetId) }
        verify(exactly = 0) { cellRedisRepository.saveCell(any()) }
        assertTrue(result.isEmpty())
    }

    // ===== 3. Cell Creation Tests =====
    
    @Test
    fun `test updateCell creates new primitive cell when cell does not exist`() {
        // Given
        val newCell = createTestCell(testCellId, "test data", DataType.PRIMITIVE)
        val processedCell = newCell.copy(evaluatedValue = "test data")
        
        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { cellLockService.acquireLock(testCellId, testUserId) } returns true
        every { cellLockService.releaseLock(testCellId, testUserId) } returns true
        every { primitiveDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs
        
        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        
        // When
        val result = cellService.updateCell(newCell, testUserId)
        
        // Then
        verify(exactly = 1) { primitiveDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    
    @Test
    fun `test updateCell creates new cell with A1 notation`() {
        // Given
        val cellId = "1:1:A"
        val newCell = createTestCell(cellId, "test data", DataType.PRIMITIVE)
        val processedCell = newCell.copy(evaluatedValue = "test data")
        
        every { cellRedisRepository.getCell(cellId) } returns null
        every { cellRepository.findById(cellId) } returns null
        every { cellLockService.acquireLock(cellId, testUserId) } returns true
        every { cellLockService.releaseLock(cellId, testUserId) } returns true
        every { primitiveDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs
        
        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        
        // When
        val result = cellService.updateCell(newCell, testUserId)
        
        // Then
        verify(exactly = 1) { primitiveDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    // ===== 4. Cell Update Tests =====
    
    @Test
    fun `test updateCell updates existing primitive cell`() {
        // Given
        val existingCell = createTestCell(testCellId, "old data", DataType.PRIMITIVE)
        val newCellData = existingCell.copy(data = "new data")
        val updatedCell = newCellData.copy(evaluatedValue = "new data")
        
        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { cellLockService.acquireLock(testCellId, testUserId) } returns true
        every { cellLockService.releaseLock(testCellId, testUserId) } returns true
        every { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs
        
        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        
        // When
        val result = cellService.updateCell(newCellData, testUserId)
        
        // Then
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
        assertEquals(updatedCell, result)
    }


    
    @Test
    fun `test updateCell changes cell type from expression to primitive`() {
        // Given
        val existingCell = createTestCell(testCellId, "=B1+10", DataType.EXPRESSION, "20")
        val newCellData = existingCell.copy(data = "30", dataType = DataType.PRIMITIVE)
        val updatedCell = newCellData.copy(evaluatedValue = "30")
        
        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { cellLockService.acquireLock(testCellId, testUserId) } returns true
        every { cellLockService.releaseLock(testCellId, testUserId) } returns true
        every { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs
        every { cellDependencyService.deleteBySourceCellId(testCellId) } returns 1
        
        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        
        // When
        val result = cellService.updateCell(newCellData, testUserId)
        
        // Then
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
        assertEquals(updatedCell, result)
    }
    
    @Test
    fun `test updateCell throws CellLockException when cell lock cannot be acquired`() {
        // Given
        val cellId = "1:1:A"
        val newCell = createTestCell(cellId, "test data", DataType.PRIMITIVE)
        
        every { cellRedisRepository.getCell(cellId) } returns null
        every { cellRepository.findById(cellId) } returns null
        every { cellLockService.acquireSheetLock(any(), any(), any()) } returns true
        every { cellLockService.releaseSheetLock(any(), any()) } returns true
        every { cellLockService.acquireLock(cellId, testUserId, any()) } returns false
        every { cellLockService.getLockOwner(cellId) } returns "anotherUser"
        
        // When/Then
        val exception = assertThrows<CellLockException> {
            cellService.updateCell(newCell, testUserId)
        }
        
        // Verify the exception contains the lock owner
        assertTrue(exception.message?.contains("anotherUser") == true)
        assertEquals(HttpStatus.CONFLICT.value(), 409)
    }
    
    @Test
    fun `test updateCell throws SheetLockException when sheet lock cannot be acquired`() {
        // Given
        val cellId = "1:1:A"
        val newCell = createTestCell(cellId, "test data", DataType.PRIMITIVE)
        
        every { cellLockService.acquireSheetLock(1L, testUserId, any()) } returns false
        every { cellLockService.getSheetLockOwner(any()) } returns "anotherUser"
        
        // When/Then
        val exception = assertThrows<SheetLockException> {
            cellService.updateCell(newCell, testUserId)
        }
        
        // Verify the exception contains the lock owner
        assertTrue(exception.message?.contains("anotherUser") == true)
        assertEquals(HttpStatus.CONFLICT.value(), 409)
    }
    
    @Test
    fun `test updateCell throws CircularReferenceException when circular dependency detected`() {
        // Given
        val cellId = "1:1:A"
        val newCell = createTestCell(cellId, "=B1", DataType.EXPRESSION)
        
        every { cellRedisRepository.getCell(cellId) } returns null
        every { cellRepository.findById(cellId) } returns null
        every { cellLockService.acquireSheetLock(any(), any(), any()) } returns true
        every { cellLockService.releaseSheetLock(any(), any()) } returns true
        every { cellLockService.acquireLock(any(), any(), any()) } returns true
        every { cellLockService.releaseLock(any(), any()) } returns true
        
        // Mock dependency graph building with circular dependency
        every { expressionParser.parse(any()) } returns listOf("1:1:B")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySheetId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns listOf("1:1:A", "1:1:B", "1:1:A")
        
        // When/Then
        val exception = assertThrows<CircularReferenceException> {
            cellService.updateCell(newCell, testUserId)
        }
        
        // Verify the exception contains the circular path
        assertTrue(exception.message?.contains("Circular dependency") == true)
        assertEquals(HttpStatus.BAD_REQUEST.value(), 400)
    }
    
    @Test
    fun `test deleteCell with A1 notation cell ID`() {
        // Given
        val cellId = "1:1:A"
        val testCell = createTestCell(cellId, "test data", DataType.PRIMITIVE)
        
        every { cellRedisRepository.getCell(cellId) } returns testCell
        every { cellLockService.acquireLock(cellId, testUserId) } returns true
        every { cellLockService.releaseLock(cellId, testUserId) } returns true
        every { cellRedisRepository.deleteCell(cellId) } just runs
        every { cellAsyncService.deleteCell(cellId) } just runs
        every { cellDependencyService.deleteBySourceCellId(cellId) } returns 1
        every { cellDependencyService.deleteByTargetCellId(cellId) } returns 1
        
        // Mock dependency graph building
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesByTargetCellId(cellId) } returns emptyList()
        every { cellAsyncService.deleteCell(any()) } just runs
        every { cellAsyncService.updateDependencies (cellId, emptyList(), 1,  any()) } just runs
        
        // When
        cellService.deleteCell(cellId, testUserId)
        
        // Then
        verify(exactly = 1) { cellRedisRepository.deleteCell(cellId) }
        verify(exactly = 1) { cellAsyncService.deleteCell(cellId) }
    }
}
