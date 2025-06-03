package com.sheets.services.impl

import com.sheets.exceptions.CircularReferenceException
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
import com.sheets.services.expression.ExpressionParser
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant

/**
 * Test Suite for CellServiceImpl Expression Handling
 * 
 * Test Categories:
 * 1. Expression Cell Creation Tests - Tests for creating cells with expressions
 * 2. Expression Cell Update Tests - Tests for updating cells with expressions
 * 3. Formula Function Tests - Tests for formula functions (SUM, AVERAGE, MIN, MAX)
 * 4. Dependency Management Tests - Tests for cell dependencies
 * 5. Circular Dependency Tests - Tests for circular dependency detection
 * 6. A1 Notation Tests - Tests for A1 notation in expressions
 */
class CellServiceExpressionTest {

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

    private val testCellId = "1:A:1"
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
        
        // Mock cell lock acquisition for all tests
        every { cellLockService.acquireLock(any(), any(), any()) } returns true
        every { cellLockService.releaseLock(any(), any()) } returns true
        every { cellLockService.getLockOwner(any()) } returns null
        
        // Mock the dependency graph building by default
        every { expressionParser.parse(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySheetId(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesByTargetCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()
        every { cellDependencyService.deleteBySourceCellId(any()) } returns 0
        
        // Generic mocks for cell retrieval - can be overridden by specific tests
        every { cellRedisRepository.getCell(any()) } returns null
        every { cellRepository.findById(any()) } returns null
    }

    // Helper function to create test cells
    private fun createTestCell(
        id: String,
        data: String,
        dataType: DataType,
        evaluatedValue: String = data,
        sheetId: Long = 1L,
        row: Int = 1,
        column: String = "A"
    ): Cell {
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

    // ===== 1. Expression Cell Creation Tests =====

    @Test
    fun `test updateCell creates new expression cell with arithmetic operation`() {
        // Given
        val newCell = createTestCell(testCellId, "=B1+10", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "15")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependent cell B1
        val b1Cell = createTestCell("1:B:1", "5", DataType.PRIMITIVE, "5", column = "B")
        every { cellRedisRepository.getCell("1:B:1") } returns b1Cell
        every { cellRepository.findById("1:B:1") } returns b1Cell

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:B:1")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell creates new expression cell with SUM function`() {
        // Given
        val newCell = createTestCell(testCellId, "=SUM(A1:A5)", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "50")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:A:1", "1:A:2", "1:A:3", "1:A:4", "1:A:5")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell creates new expression cell with AVERAGE function`() {
        // Given
        val newCell = createTestCell(testCellId, "=AVERAGE(A1:A5)", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "10")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:A:1", "1:A:2", "1:A:3", "1:A:4", "1:A:5")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell creates new expression cell with MIN function`() {
        // Given
        val newCell = createTestCell(testCellId, "=MIN(A1:A5)", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "1")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:A:1", "1:A:2", "1:A:3", "1:A:4", "1:A:5")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell creates new expression cell with MAX function`() {
        // Given
        val newCell = createTestCell(testCellId, "=MAX(A1:A5)", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "5")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:A:1", "1:A:2", "1:A:3", "1:A:4", "1:A:5")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell creates new expression cell with combined functions`() {
        // Given
        val newCell = createTestCell(testCellId, "=SUM(A1:A5)+AVERAGE(B1:B3)", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "60")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:A:1", "1:A:2", "1:A:3", "1:A:4", "1:A:5", "1:B:1", "1:B:2", "1:B:3")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    // ===== 2. Expression Cell Update Tests =====

    @Test
    fun `test updateCell updates existing expression cell`() {
        // Given
        val existingCell = createTestCell(testCellId, "=B1+10", DataType.EXPRESSION, "15")
        val newCellData = existingCell.copy(data = "=B1+20")
        val updatedCell = newCellData.copy(evaluatedValue = "25")

        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:B:1")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCellData, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) }
        assertEquals(updatedCell, result)
    }

    @Test
    fun `test updateCell changes cell type from primitive to expression`() {
        // Given
        val existingCell = createTestCell(testCellId, "10", DataType.PRIMITIVE, "10")
        val newCellData = existingCell.copy(data = "=B1+10", dataType = DataType.EXPRESSION)
        val updatedCell = newCellData.copy(evaluatedValue = "15")

        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:B:1")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCellData, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) }
        assertEquals(updatedCell, result)
    }

    @Test
    fun `test updateCell changes cell type from expression to primitive`() {
        // Given
        val existingCell = createTestCell(testCellId, "=B1+10", DataType.EXPRESSION, "15")
        val newCellData = existingCell.copy(data = "20", dataType = DataType.PRIMITIVE)
        val updatedCell = newCellData.copy(evaluatedValue = "20")
        val oldDependencies = listOf(
            CellDependency(
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:B:1",
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )

        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySourceCellId(testCellId) } returns oldDependencies
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.deleteBySourceCellId(testCellId) } returns 1

        // When
        val result = cellService.updateCell(newCellData, testUserId)

        // Then
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
    }

    @Test
    fun `test updateCell removes dependencies when changing from expression to primitive`() {
        // Given
        val existingCell = createTestCell(testCellId, "=B1+C1", DataType.EXPRESSION, "30")
        val newCellData = createTestCell(testCellId, "50", DataType.PRIMITIVE, "")
        val updatedCell = newCellData.copy(evaluatedValue = "50")

        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock existing dependencies to be deleted
        val now = Instant.now()
        val existingDependencies = listOf(
            CellDependency(
                id = "1",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:B:1",
                createdAt = now,
                updatedAt = now
            ),
            CellDependency(
                id = "2",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:C:1",
                createdAt = now,
                updatedAt = now
            )
        )
        every { cellDependencyService.getDependenciesBySourceCellId(testCellId) } returns existingDependencies
        every { cellDependencyService.deleteBySourceCellId(testCellId) } returns 2

        // When
        val result = cellService.updateCell(newCellData, testUserId)

        // Then
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
    }

    @Test
    fun `test updateCell creates dependencies for expression cells`() {
        // Given
        val newCell = createTestCell(testCellId, "=B1+C1", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "30")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency creation
        val now = Instant.now()
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:B:1",
                createdAt = now,
                updatedAt = now
            ),
            CellDependency(
                id = "2",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:C:1",
                createdAt = now,
                updatedAt = now
            )
        )
        every { expressionParser.parse("=B1+C1") } returns listOf("1:B:1", "1:C:1")
        every { cellDependencyService.createDependencies(any()) } returns dependencies

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }

    }

    @Test
    fun `test updateCell updates dependencies when expression changes`() {
        // Given
        val existingCell = createTestCell(testCellId, "=B1+10", DataType.EXPRESSION, "20")
        val newCellData = existingCell.copy(data = "=C1+D1")
        val updatedCell = newCellData.copy(evaluatedValue = "40")

        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock existing dependencies to be deleted
        val now = Instant.now()
        val existingDependencies = listOf(
            CellDependency(
                id = "1",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:B:1",
                createdAt = now,
                updatedAt = now
            )
        )
        every { cellDependencyService.getDependenciesBySourceCellId(testCellId) } returns existingDependencies
        every { cellDependencyService.deleteBySourceCellId(testCellId) } returns 1

        // Mock new dependencies to be created
        val newDependencies = listOf(
            CellDependency(
                id = "2",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:C:1",
                createdAt = now,
                updatedAt = now
            ),
            CellDependency(
                id = "3",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:D:1",
                createdAt = now,
                updatedAt = now
            )
        )
        every { expressionParser.parse("=C1+D1") } returns listOf("1:C:1", "1:D:1")
        every { cellDependencyService.createDependencies(any()) } returns newDependencies

        // When
        val result = cellService.updateCell(newCellData, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) }
    }

    // ===== 3. Formula Function Tests =====

    @ParameterizedTest
    @CsvSource(
        "=B1+10, 20",
        "=B1-5, 5",
        "=B1*2, 20",
        "=B1/2, 5"
    )
    fun `test updateCell with different arithmetic operations`(expression: String, expected: String) {
        // Given
        val newCell = createTestCell(testCellId, expression, DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = expected)

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:B:1")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    // ===== 4. Dependency Management Tests =====

    @Test
    fun `test updateCell creates dependencies for expression cells v2`() {
        // Given
        val newCell = createTestCell(testCellId, "=B1+C1", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "30")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency creation
        val now = Instant.now()
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:B:1",
                createdAt = now,
                updatedAt = now
            ),
            CellDependency(
                id = "2",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:C:1",
                createdAt = now,
                updatedAt = now
            )
        )
        every { expressionParser.parse("=B1+C1") } returns listOf("1:B:1", "1:C:1")
        every { cellDependencyService.createDependencies(any()) } returns dependencies

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell updates dependencies when expression changes v2`() {
        // Given
        val existingCell = createTestCell(testCellId, "=B1+10", DataType.EXPRESSION, "20")
        val newCellData = existingCell.copy(data = "=C1+D1")
        val updatedCell = newCellData.copy(evaluatedValue = "40")

        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock existing dependencies to be deleted
        val now = Instant.now()
        val existingDependencies = listOf(
            CellDependency(
                id = "1",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:B:1",
                createdAt = now,
                updatedAt = now
            )
        )
        every { cellDependencyService.getDependenciesBySourceCellId(testCellId) } returns existingDependencies
        every { cellDependencyService.deleteBySourceCellId(testCellId) } returns 1

        // Mock new dependencies to be created
        val newDependencies = listOf(
            CellDependency(
                id = "2",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:C:1",
                createdAt = now,
                updatedAt = now
            ),
            CellDependency(
                id = "3",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:D:1",
                createdAt = now,
                updatedAt = now
            )
        )
        every { expressionParser.parse("=C1+D1") } returns listOf("1:C:1", "1:D:1")
        every { cellDependencyService.createDependencies(any()) } returns newDependencies

        // When
        val result = cellService.updateCell(newCellData, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processExistingCell(any(), any(), any(), any()) }
        assertEquals(updatedCell, result)
    }

    @Test
    fun `test updateCell removes dependencies when changing from expression to primitive v2`() {
        // Given
        val existingCell = createTestCell(testCellId, "=B1+C1", DataType.EXPRESSION, "30")
        val newCellData = createTestCell(testCellId, "50", DataType.PRIMITIVE, "")
        val updatedCell = newCellData.copy(evaluatedValue = "50")

        every { cellRedisRepository.getCell(testCellId) } returns existingCell
        every { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) } returns updatedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock existing dependencies to be deleted
        val now = Instant.now()
        val existingDependencies = listOf(
            CellDependency(
                id = "1",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:B:1",
                createdAt = now,
                updatedAt = now
            ),
            CellDependency(
                id = "2",
                sheetId = 1L,
                sourceCellId = testCellId,
                targetCellId = "1:C:1",
                createdAt = now,
                updatedAt = now
            )
        )
        every { cellDependencyService.getDependenciesBySourceCellId(testCellId) } returns existingDependencies
        every { cellDependencyService.deleteBySourceCellId(testCellId) } returns 2

        // When
        val result = cellService.updateCell(newCellData, testUserId)

        // Then
        verify(exactly = 1) { primitiveDataProcessor.processExistingCell(any(), any(), any(), any()) }
        assertEquals(updatedCell, result)
    }

    // ===== 5. Circular Dependency Tests =====

    @Test
    fun `test updateCell detects circular dependencies`() {
        // Given
        val newCell = createTestCell(testCellId, "=B1+C1", DataType.EXPRESSION, "")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null

        // Mock dependency graph building for circular dependency
        every { expressionParser.parse(any()) } returns listOf("1:B:1", "1:C:1")
        val circularPath = listOf("1:A:1", "1:B:1", "1:C:1", "1:A:1")
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns circularPath

        // When / Then
        val exception = assertThrows<CircularReferenceException> {
            cellService.updateCell(newCell, testUserId)
        }

        assertTrue(exception.message!!.contains("Circular dependency detected"))
        assertTrue(exception.message!!.contains("1:A:1 -> 1:B:1 -> 1:C:1 -> 1:A:1"))
    }

    @Test
    fun `test updateCell with self-reference circular dependency`() {
        // Given
        val newCell = createTestCell(testCellId, "=A1", DataType.EXPRESSION, "")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null

        // Mock dependency graph building for self-reference
        every { expressionParser.parse(any()) } returns listOf("1:A:1")
        val circularPath = listOf("1:A:1", "1:A:1")
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns circularPath

        // When / Then
        val exception = assertThrows<CircularReferenceException> {
            cellService.updateCell(newCell, testUserId)
        }

        assertTrue(exception.message!!.contains("Circular dependency detected"))
        assertTrue(exception.message!!.contains("1:A:1 -> 1:A:1"))
    }

    @Test
    fun `test updateCell with complex circular dependency`() {
        // Given
        val newCell = createTestCell(testCellId, "=B1+C1", DataType.EXPRESSION, "")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null

        // Mock dependency graph building for complex circular dependency
        every { expressionParser.parse(any()) } returns listOf("1:B:1", "1:C:1")
        val circularPath = listOf("1:A:1", "1:B:1", "1:D:1", "1:E:1", "1:C:1", "1:A:1")
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns circularPath

        // When / Then
        val exception = assertThrows<CircularReferenceException> {
            cellService.updateCell(newCell, testUserId)
        }

        assertTrue(exception.message!!.contains("Circular dependency detected"))
        assertTrue(exception.message!!.contains("1:A:1 -> 1:B:1 -> 1:D:1 -> 1:E:1 -> 1:C:1 -> 1:A:1"))
    }

    // ===== 6. A1 Notation Tests =====

    @Test
    fun `test updateCell with A1 notation cell references`() {
        // Given
        val newCell = createTestCell(testCellId, "=A2+B3", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "30")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("1:A:2", "1:B:3")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell with A1 notation range`() {
        // Given
        val newCell = createTestCell(testCellId, "=SUM(A1:C3)", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "100")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        val dependencies = listOf(
            "1:A:1", "1:A:2", "1:A:3",
            "1:B:1", "1:B:2", "1:B:3",
            "1:C:1", "1:C:2", "1:C:3"
        )
        every { expressionParser.parse(any()) } returns dependencies
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell with mixed notation (legacy and A1)`() {
        // Given
        val newCell = createTestCell(testCellId, "=SUM(A1:C3) + SUM(1:1-3:3)", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "200")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        val dependencies = listOf(
            "1:A:1", "1:A:2", "1:A:3",
            "1:B:1", "1:B:2", "1:B:3",
            "1:C:1", "1:C:2", "1:C:3",
            "1:1:1", "1:2:1", "1:3:1",
            "1:1:2", "1:2:2", "1:3:2",
            "1:1:3", "1:2:3", "1:3:3"
        )
        every { expressionParser.parse(any()) } returns dependencies
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell with column letters beyond Z`() {
        // Given
        val newCell = createTestCell(testCellId, "=AA1+AB2", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "50")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("AA1", "AB2")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { cellDependencyService.getDependenciesBySheetId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }

    @Test
    fun `test updateCell with column letters beyond Z and A1 notation`() {
        // Given
        val newCell = createTestCell(testCellId, "=AA1+AB2+AC3", DataType.EXPRESSION, "")
        val processedCell = newCell.copy(evaluatedValue = "150")

        every { cellRedisRepository.getCell(testCellId) } returns null
        every { cellRepository.findById(testCellId) } returns null
        every { expressionDataProcessor.processNewCell(any(), any(), any()) } returns processedCell
        every { cellRedisRepository.saveCell(any()) } returns mockk()
        every { cellAsyncService.saveCell(any()) } just runs

        // Mock dependency graph building
        every { expressionParser.parse(any()) } returns listOf("AA1", "AB2", "AC3")
        every { cellDependencyService.getDependenciesBySourceCellId(any()) } returns emptyList()
        every { circularDependencyDetector.detectCircularDependency(any(), any()) } returns null
        every { cellDependencyService.createDependencies(any()) } returns emptyList()

        // When
        val result = cellService.updateCell(newCell, testUserId)

        // Then
        verify(exactly = 1) { expressionDataProcessor.processNewCell(any(), any(), any()) }
        assertEquals(processedCell, result)
    }
    

}
