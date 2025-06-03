package com.sheets.services.impl

import com.sheets.models.domain.Cell
import com.sheets.models.domain.CellDependency
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellDependencyRedisRepository
import com.sheets.repositories.CellDependencyRepository
import com.sheets.repositories.CellRedisRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellLockService
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CellDependencyServiceImplTest {

    private lateinit var cellDependencyRepository: CellDependencyRepository
    private lateinit var cellDependencyRedisRepository: CellDependencyRedisRepository
    private lateinit var circularDependencyDetector: CircularDependencyDetector
    private lateinit var cellRedisRepository: CellRedisRepository
    private lateinit var cellLockService: CellLockService
    private lateinit var cellAsyncService: CellAsyncService
    private lateinit var expressionParser: ExpressionParser
    private lateinit var expressionEvaluator: ExpressionEvaluator
    private lateinit var cellDependencyService: CellDependencyServiceImpl

    @BeforeEach
    fun setup() {
        cellDependencyRepository = mockk(relaxed = true)
        cellDependencyRedisRepository = mockk(relaxed = true)
        circularDependencyDetector = mockk(relaxed = true)
        cellRedisRepository = mockk(relaxed = true)
        cellLockService = mockk(relaxed = true)
        cellAsyncService = mockk(relaxed = true)
        expressionParser = mockk(relaxed = true)
        expressionEvaluator = mockk(relaxed = true)
        
        cellDependencyService = CellDependencyServiceImpl(
            cellDependencyRepository,
            cellDependencyRedisRepository,
            circularDependencyDetector,
            cellRedisRepository,
            cellLockService,
            cellAsyncService,
            expressionParser,
            expressionEvaluator
        )
    }

    @Test
    fun `test getDependenciesBySourceCellId returns cached dependencies from Redis`() {
        // Given
        val sourceCellId = "1:1:1"
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sourceCellId = sourceCellId,
                targetCellId = "1:2:1",
                createdAt = Instant.now()
            )
        )
        
        every { cellDependencyRedisRepository.getDependenciesBySourceCellId(sourceCellId) } returns dependencies
        
        // When
        val result = cellDependencyService.getDependenciesBySourceCellId(sourceCellId)
        
        // Then
        assertEquals(dependencies, result)
        verify(exactly = 1) { cellDependencyRedisRepository.getDependenciesBySourceCellId(sourceCellId) }
        verify(exactly = 0) { cellDependencyRepository.findBySourceCellId(any()) }
    }
    
    @Test
    fun `test getDependenciesBySourceCellId fetches from MongoDB when Redis cache is empty`() {
        // Given
        val sourceCellId = "1:1:1"
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sourceCellId = sourceCellId,
                targetCellId = "1:2:1",
                createdAt = Instant.now()
            )
        )
        
        every { cellDependencyRedisRepository.getDependenciesBySourceCellId(sourceCellId) } returns emptyList()
        every { cellDependencyRepository.findBySourceCellId(sourceCellId) } returns dependencies
        
        // When
        val result = cellDependencyService.getDependenciesBySourceCellId(sourceCellId)
        
        // Then
        assertEquals(dependencies, result)
        verify(exactly = 1) { cellDependencyRedisRepository.getDependenciesBySourceCellId(sourceCellId) }
        verify(exactly = 1) { cellDependencyRepository.findBySourceCellId(sourceCellId) }
        verify(exactly = 1) { cellDependencyRedisRepository.saveDependencies(dependencies) }
    }
    
    @Test
    fun `test getDependenciesByTargetCellId returns cached dependencies from Redis`() {
        // Given
        val targetCellId = "1:2:1"
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sourceCellId = "1:1:1",
                targetCellId = targetCellId,
                createdAt = Instant.now()
            )
        )
        
        every { cellDependencyRedisRepository.getDependenciesByTargetCellId(targetCellId) } returns dependencies
        
        // When
        val result = cellDependencyService.getDependenciesByTargetCellId(targetCellId)
        
        // Then
        assertEquals(dependencies, result)
        verify(exactly = 1) { cellDependencyRedisRepository.getDependenciesByTargetCellId(targetCellId) }
        verify(exactly = 0) { cellDependencyRepository.findByTargetCellId(any()) }
    }
    
    @Test
    fun `test getDependenciesByTargetCellId fetches from MongoDB when Redis cache is empty`() {
        // Given
        val targetCellId = "1:2:1"
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sourceCellId = "1:1:1",
                targetCellId = targetCellId,
                createdAt = Instant.now()
            )
        )
        
        every { cellDependencyRedisRepository.getDependenciesByTargetCellId(targetCellId) } returns emptyList()
        every { cellDependencyRepository.findByTargetCellId(targetCellId) } returns dependencies
        
        // When
        val result = cellDependencyService.getDependenciesByTargetCellId(targetCellId)
        
        // Then
        assertEquals(dependencies, result)
        verify(exactly = 1) { cellDependencyRedisRepository.getDependenciesByTargetCellId(targetCellId) }
        verify(exactly = 1) { cellDependencyRepository.findByTargetCellId(targetCellId) }
        verify(exactly = 1) { cellDependencyRedisRepository.saveDependencies(dependencies) }
    }
    
    @Test
    fun `test createDependency creates and saves dependency`() {
        // Given
        val sourceCellId = "1:1:1"
        val targetCellId = "1:2:1"
        val timestamp = Instant.now()
        
        val dependencySlot = slot<CellDependency>()
        
        every { cellDependencyRepository.save(capture(dependencySlot)) } answers { dependencySlot.captured.copy(id = "1") }
        
        // When
        val result = cellDependencyService.createDependency(sourceCellId, targetCellId, timestamp)
        
        // Then
        assertEquals("1", result.id)
        assertEquals(sourceCellId, result.sourceCellId)
        assertEquals(targetCellId, result.targetCellId)
        assertEquals(timestamp, result.createdAt)
        
        verify(exactly = 1) { cellDependencyRepository.save(any()) }
        verify(exactly = 1) { cellDependencyRedisRepository.saveDependency(result) }
    }
    
    @Test
    fun `test deleteDependenciesBySourceCellId deletes dependencies`() {
        // Given
        val sourceCellId = "1:1:1"
        
        // When
        cellDependencyService.deleteDependenciesBySourceCellId(sourceCellId)
        
        // Then
        verify(exactly = 1) { cellDependencyRepository.deleteBySourceCellId(sourceCellId) }
        verify(exactly = 1) { cellDependencyRedisRepository.deleteDependenciesBySourceCellId(sourceCellId) }
    }
    
    @Test
    fun `test updateDependentCells updates dependent cells`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sourceCellId = "1:2:1",
                targetCellId = cellId,
                createdAt = Instant.now()
            ),
            CellDependency(
                id = "2",
                sourceCellId = "1:3:1",
                targetCellId = cellId,
                createdAt = Instant.now()
            )
        )
        
        val dependentCell1 = Cell(
            id = "1:2:1",
            sheetId = 1,
            row = 2,
            column = 1,
            data = "=A1+10",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "20",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val dependentCell2 = Cell(
            id = "1:3:1",
            sheetId = 1,
            row = 3,
            column = 1,
            data = "=A1*2",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "20",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cellReferences1 = listOf("A1")
        val cellReferences2 = listOf("A1")
        
        val context1 = mapOf("A1" to "10")
        val context2 = mapOf("A1" to "10")
        
        every { cellDependencyService.getDependenciesByTargetCellId(cellId) } returns dependencies
        every { cellRedisRepository.getCell("1:2:1") } returns dependentCell1
        every { cellRedisRepository.getCell("1:3:1") } returns dependentCell2
        
        every { expressionParser.parse("=A1+10") } returns cellReferences1
        every { expressionParser.parse("=A1*2") } returns cellReferences2
        
        every { expressionEvaluator.evaluate("=A1+10", any()) } returns "20"
        every { expressionEvaluator.evaluate("=A1*2", any()) } returns "20"
        
        every { cellLockService.acquireLock(any(), any()) } returns true
        every { cellLockService.releaseLock(any(), any()) } returns true
        
        // When
        cellDependencyService.updateDependentCells(cellId, userId)
        
        // Then
        verify(exactly = 1) { cellDependencyService.getDependenciesByTargetCellId(cellId) }
        verify(exactly = 1) { cellRedisRepository.getCell("1:2:1") }
        verify(exactly = 1) { cellRedisRepository.getCell("1:3:1") }
        verify(exactly = 1) { expressionParser.parse("=A1+10") }
        verify(exactly = 1) { expressionParser.parse("=A1*2") }
        verify(exactly = 2) { cellLockService.acquireLock(any(), any()) }
        verify(exactly = 2) { cellLockService.releaseLock(any(), any()) }
        verify(exactly = 2) { cellRedisRepository.saveCell(any()) }
        verify(exactly = 2) { cellAsyncService.saveCell(any()) }
    }
    
    @Test
    fun `test updateDependentCells handles circular dependencies`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sourceCellId = "1:2:1",
                targetCellId = cellId,
                createdAt = Instant.now()
            )
        )
        
        val dependentCell = Cell(
            id = "1:2:1",
            sheetId = 1,
            row = 2,
            column = 1,
            data = "=A1+B1",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "30",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellDependencyService.getDependenciesByTargetCellId(cellId) } returns dependencies
        every { cellRedisRepository.getCell("1:2:1") } returns dependentCell
        every { expressionParser.parse("=A1+B1") } returns listOf("A1", "B1")
        every { cellLockService.acquireLock(any(), any()) } returns true
        every { cellLockService.releaseLock(any(), any()) } returns true
        every { expressionEvaluator.evaluate(any(), any()) } throws RuntimeException("#CIRCULAR!")
        
        // When
        cellDependencyService.updateDependentCells(cellId, userId)
        
        // Then
        val cellSlot = slot<Cell>()
        verify(exactly = 1) { cellRedisRepository.saveCell(capture(cellSlot)) }
        
        assertTrue(cellSlot.captured.evaluatedValue.contains("#CIRCULAR!"))
    }
    
    @Test
    fun `test updateDependentCells handles null dependent cell`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sourceCellId = "1:2:1",
                targetCellId = cellId,
                createdAt = Instant.now()
            )
        )
        
        every { cellDependencyService.getDependenciesByTargetCellId(cellId) } returns dependencies
        every { cellRedisRepository.getCell("1:2:1") } returns null
        
        // When
        cellDependencyService.updateDependentCells(cellId, userId)
        
        // Then
        verify(exactly = 0) { expressionParser.parse(any()) }
        verify(exactly = 0) { cellRedisRepository.saveCell(any()) }
    }
    
    @Test
    fun `test updateDependentCells handles non-expression dependent cell`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        val dependencies = listOf(
            CellDependency(
                id = "1",
                sourceCellId = "1:2:1",
                targetCellId = cellId,
                createdAt = Instant.now()
            )
        )
        
        val dependentCell = Cell(
            id = "1:2:1",
            sheetId = 1,
            row = 2,
            column = 1,
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellDependencyService.getDependenciesByTargetCellId(cellId) } returns dependencies
        every { cellRedisRepository.getCell("1:2:1") } returns dependentCell
        
        // When
        cellDependencyService.updateDependentCells(cellId, userId)
        
        // Then
        verify(exactly = 0) { expressionParser.parse(any()) }
        verify(exactly = 0) { cellRedisRepository.saveCell(any()) }
    }
    
    @Test
    fun `test updateDependentCells recursively updates dependent cells`() {
        // Given
        val cellId = "1:1:1"
        val userId = "user1"
        
        // First level dependency
        val dependencies1 = listOf(
            CellDependency(
                id = "1",
                sourceCellId = "1:2:1",
                targetCellId = cellId,
                createdAt = Instant.now()
            )
        )
        
        // Second level dependency
        val dependencies2 = listOf(
            CellDependency(
                id = "2",
                sourceCellId = "1:3:1",
                targetCellId = "1:2:1",
                createdAt = Instant.now()
            )
        )
        
        val dependentCell1 = Cell(
            id = "1:2:1",
            sheetId = 1,
            row = 2,
            column = 1,
            data = "=A1+10",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "20",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val dependentCell2 = Cell(
            id = "1:3:1",
            sheetId = 1,
            row = 3,
            column = 1,
            data = "=B1*2",
            dataType = DataType.EXPRESSION,
            evaluatedValue = "40",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellDependencyService.getDependenciesByTargetCellId(cellId) } returns dependencies1
        every { cellDependencyService.getDependenciesByTargetCellId("1:2:1") } returns dependencies2
        every { cellRedisRepository.getCell("1:2:1") } returns dependentCell1
        every { cellRedisRepository.getCell("1:3:1") } returns dependentCell2
        
        every { expressionParser.parse("=A1+10") } returns listOf("A1")
        every { expressionParser.parse("=B1*2") } returns listOf("B1")
        
        every { expressionEvaluator.evaluate("=A1+10", any()) } returns "20"
        every { expressionEvaluator.evaluate("=B1*2", any()) } returns "40"
        
        every { cellLockService.acquireLock(any(), any()) } returns true
        every { cellLockService.releaseLock(any(), any()) } returns true
        
        // When
        cellDependencyService.updateDependentCells(cellId, userId)
        
        // Then
        verify(exactly = 1) { cellDependencyService.getDependenciesByTargetCellId(cellId) }
        verify(exactly = 1) { cellDependencyService.getDependenciesByTargetCellId("1:2:1") }
        verify(exactly = 1) { cellRedisRepository.getCell("1:2:1") }
        verify(exactly = 1) { cellRedisRepository.getCell("1:3:1") }
        verify(exactly = 1) { expressionParser.parse("=A1+10") }
        verify(exactly = 1) { expressionParser.parse("=B1*2") }
        verify(exactly = 2) { cellLockService.acquireLock(any(), any()) }
        verify(exactly = 2) { cellLockService.releaseLock(any(), any()) }
        verify(exactly = 2) { cellRedisRepository.saveCell(any()) }
        verify(exactly = 2) { cellAsyncService.saveCell(any()) }
    }
}
