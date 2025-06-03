package com.sheets.services.expression.functions

import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.services.CellService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class SumFunctionTest {

    private lateinit var cellService: CellService
    private lateinit var sumFunction: SumFunction

    @BeforeEach
    fun setup() {
        cellService = mockk<CellService>()
        sumFunction = SumFunction(cellService)
    }

    @Test
    fun `test name returns SUM`() {
        assertEquals("SUM", sumFunction.name())
    }

    @Test
    fun `test evaluate with simple numeric arguments`() {
        // Given
        val args = listOf("10", "20", "30", "sheetId=1")

        // When
        val result = sumFunction.evaluate(args)

        // Then
        assertEquals("60.0", result)
    }

    @Test
    fun `test evaluate with cell references`() {
        // Given
        val args = listOf("A1", "B1", "sheetId=1")
        
        // Mock the cell service
        val cellA1 = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cellB1 = Cell(
            id = "1:1:2",
            sheetId = 1L,
            row = 1,
            column = "2",
            data = "20",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "20",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellService.getCell("1:1:1") } returns cellA1
        every { cellService.getCell("1:1:2") } returns cellB1

        // When
        val result = sumFunction.evaluate(args)

        // Then
        assertEquals("30.0", result)
    }

    @Test
    fun `test evaluate with mixed numeric and cell references`() {
        // Given
        val args = listOf("A1", "10", "B1", "sheetId=1")
        
        // Mock the cell service
        val cellA1 = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "5",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "5",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cellB1 = Cell(
            id = "1:1:2",
            sheetId = 1L,
            row = 1,
            column = "2",
            data = "15",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "15",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellService.getCell("1:1:1") } returns cellA1
        every { cellService.getCell("1:1:2") } returns cellB1

        // When
        val result = sumFunction.evaluate(args)

        // Then
        assertEquals("30.0", result)
    }

    @Test
    fun `test evaluate with cell references in numeric format`() {
        // Given
        val args = listOf("1:1", "1:2", "sheetId=1")
        
        // Mock the cell service
        val cell1 = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cell2 = Cell(
            id = "1:1:2",
            sheetId = 1L,
            row = 1,
            column = "2",
            data = "20",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "20",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellService.getCell("1:1:1") } returns cell1
        every { cellService.getCell("1:1:2") } returns cell2

        // When
        val result = sumFunction.evaluate(args)

        // Then
        assertEquals("30.0", result)
    }

    @Test
    fun `test evaluate with cell references in A1 notation`() {
        // Given
        val args = listOf("A1", "B1", "sheetId=1")
        
        // Mock the cell service
        val cell1 = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cell2 = Cell(
            id = "1:1:2",
            sheetId = 1L,
            row = 1,
            column = "2",
            data = "20",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "20",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellService.getCell("1:1:1") } returns cell1
        every { cellService.getCell("1:1:2") } returns cell2

        // When
        val result = sumFunction.evaluate(args)

        // Then
        assertEquals("30.0", result)
    }

    @Test
    fun `test evaluate with cell range`() {
        // Given
        val args = listOf("A1:A3", "sheetId=1")
        
        // Mock the cell service
        val cell1 = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cell2 = Cell(
            id = "1:2:1",
            sheetId = 1L,
            row = 2,
            column = "1",
            data = "20",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "20",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cell3 = Cell(
            id = "1:3:1",
            sheetId = 1L,
            row = 3,
            column = "1",
            data = "30",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "30",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellService.getCell("1:1:1") } returns cell1
        every { cellService.getCell("1:2:1") } returns cell2
        every { cellService.getCell("1:3:1") } returns cell3

        // When
        val result = sumFunction.evaluate(args)

        // Then
        assertEquals("60.0", result)
    }

    @Test
    fun `test evaluate with cell references containing errors`() {
        // Given
        val args = listOf("A1", "B1", "sheetId=1")
        
        // Mock the cell service
        val cellA1 = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cellB1 = Cell(
            id = "1:1:2",
            sheetId = 1L,
            row = 1,
            column = "2",
            data = "#ERROR!",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "#ERROR!",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellService.getCell("1:1:1") } returns cellA1
        every { cellService.getCell("1:1:2") } returns cellB1

        // When
        val result = sumFunction.evaluate(args)

        // Then
        assertEquals("#ERROR!", result)
    }

    @Test
    fun `test evaluate with non-numeric cell references`() {
        // Given
        val args = listOf("A1", "B1", "sheetId=1")
        
        // Mock the cell service
        val cellA1 = Cell(
            id = "1:1:1",
            sheetId = 1L,
            row = 1,
            column = "1",
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cellB1 = Cell(
            id = "1:1:2",
            sheetId = 1L,
            row = 1,
            column = "2",
            data = "text",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "text",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        every { cellService.getCell("1:1:1") } returns cellA1
        every { cellService.getCell("1:1:2") } returns cellB1

        // When
        val result = sumFunction.evaluate(args)

        // Then
        assertEquals("#ERROR!", result)
    }

    @Test
    fun `test evaluate with empty arguments`() {
        // Given
        val args = listOf("sheetId=1")

        // When/Then
        try {
            sumFunction.evaluate(args)
        } catch (e: Exception) {
            assertEquals("SUM: No arguments provided", e.message)
        }
    }
}
