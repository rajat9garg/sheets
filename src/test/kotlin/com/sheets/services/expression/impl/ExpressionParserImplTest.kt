package com.sheets.services.expression.impl

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpressionParserImplTest {

    private lateinit var expressionParser: ExpressionParserImpl

    @BeforeEach
    fun setup() {
        expressionParser = ExpressionParserImpl()
    }

    @Test
    fun `test parse simple cell reference`() {
        // Given
        val expression = "=A1"

        // When
        val result = expressionParser.parse(expression)

        // Then
        assertEquals(1, result.size)
        assertTrue(result.contains("A1"))
    }

    @Test
    fun `test parse multiple cell references`() {
        // Given
        val expression = "=A1+B2+C3"

        // When
        val result = expressionParser.parse(expression)

        // Then
        assertEquals(3, result.size)
        assertTrue(result.contains("A1"))
        assertTrue(result.contains("B2"))
        assertTrue(result.contains("C3"))
    }

    @Test
    fun `test parse cell references in function`() {
        // Given
        val expression = "=SUM(A1,B2)"

        // When
        val result = expressionParser.parse(expression)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains("A1"))
        assertTrue(result.contains("B2"))
    }

    @Test
    fun `test parse cell references in nested functions`() {
        // Given
        val expression = "=SUM(A1,AVERAGE(B2,C3))"

        // When
        val result = expressionParser.parse(expression)

        // Then
        assertEquals(3, result.size)
        assertTrue(result.contains("A1"))
        assertTrue(result.contains("B2"))
        assertTrue(result.contains("C3"))
    }

    @Test
    fun `test parse cell references with numeric format`() {
        // Given
        val expression = "=1:2+3:4"

        // When
        val result = expressionParser.parse(expression)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains("1:2"))
        assertTrue(result.contains("3:4"))
    }

    @Test
    fun `test parse cell references with mixed formats`() {
        // Given
        val expression = "=A1+2:3"

        // When
        val result = expressionParser.parse(expression)

        // Then
        assertEquals(2, result.size)
        assertTrue(result.contains("A1"))
        assertTrue(result.contains("2:3"))
    }

    @Test
    fun `test parse cell range in function`() {
        // Given
        val expression = "=SUM(A1:A5)"

        // When
        val result = expressionParser.parse(expression)

        // Then
        // Should expand A1:A5 to A1,A2,A3,A4,A5
        assertEquals(5, result.size)
        assertTrue(result.contains("A1"))
        assertTrue(result.contains("A2"))
        assertTrue(result.contains("A3"))
        assertTrue(result.contains("A4"))
        assertTrue(result.contains("A5"))
    }

    @Test
    fun `test parse cell range with numeric format`() {
        // Given
        val expression = "=SUM(1:1:1:5)"

        // When
        val result = expressionParser.parse(expression)

        // Then
        // Should expand 1:1:1:5 to 1:1,1:2,1:3,1:4,1:5
        assertEquals(5, result.size)
        assertTrue(result.contains("1:1"))
        assertTrue(result.contains("1:2"))
        assertTrue(result.contains("1:3"))
        assertTrue(result.contains("1:4"))
        assertTrue(result.contains("1:5"))
    }

    @Test
    fun `test parse expression with no cell references`() {
        // Given
        val expression = "=2+3*4"

        // When
        val result = expressionParser.parse(expression)

        // Then
        assertEquals(0, result.size)
    }

    @Test
    fun `test parse non-expression`() {
        // Given
        val expression = "Hello World"

        // When
        val result = expressionParser.parse(expression)

        // Then
        assertEquals(0, result.size)
    }
}
