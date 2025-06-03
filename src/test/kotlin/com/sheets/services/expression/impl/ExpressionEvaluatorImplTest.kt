package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.FunctionRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals

class ExpressionEvaluatorImplTest {

    private lateinit var expressionParser: ExpressionParser
    private lateinit var functionRegistry: FunctionRegistry
    private lateinit var expressionEvaluator: ExpressionEvaluatorImpl
    private val logger = LoggerFactory.getLogger(ExpressionEvaluatorImplTest::class.java)
    private lateinit var sumFunction: ExpressionFunction

    @BeforeEach
    fun setup() {
        expressionParser = mockk<ExpressionParser>()
        functionRegistry = mockk<FunctionRegistry>()
        sumFunction = mockk<ExpressionFunction>()
        expressionEvaluator = ExpressionEvaluatorImpl(expressionParser, functionRegistry)
    }

    @Test
    fun `test evaluate simple arithmetic expression`() {
        // Given
        val expression = "=2+3"
        val context = mapOf<String, String>()
        
        every { expressionParser.parse(expression) } returns emptyList()

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("5.0", result)
    }

    @Test
    fun `test evaluate complex arithmetic expression with parentheses`() {
        // Given
        val expression = "=(2+3)*4"
        val context = mapOf<String, String>()
        
        every { expressionParser.parse(expression) } returns emptyList()

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("20.0", result)
    }

    @Test
    fun `test evaluate expression with cell references`() {
        // Given
        val expression = "=A1+B1"
        val context = mapOf(
            "A1" to "10",
            "B1" to "20"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("A1", "B1")

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("30.0", result)
    }

    @Test
    fun `test evaluate expression with cell references in complex expression`() {
        // Given
        val expression = "=(A1+B1)*C1"
        val context = mapOf(
            "A1" to "10",
            "B1" to "20",
            "C1" to "2"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("A1", "B1", "C1")

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("60.0", result)
    }

    @Test
    fun `test evaluate function call`() {
        // Given
        val expression = "=SUM(A1,B1)"
        val context = mapOf(
            "A1" to "10",
            "B1" to "20",
            "sheetId" to "1"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("A1", "B1")
        
        // Mock the function registry to handle the SUM function
        every { functionRegistry.getFunction("SUM") } returns sumFunction
        every { sumFunction.name() } returns "SUM"
        every { sumFunction.evaluate(listOf("A1", "B1")) } returns "30"

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("30", result)
    }

    @Test
    fun `test evaluate nested function calls`() {
        // Given
        val expression = "=SUM(A1,MULTIPLY(B1,C1))"
        val context = mapOf(
            "A1" to "10",
            "B1" to "20",
            "C1" to "3",
            "sheetId" to "1"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("A1", "B1", "C1")
        
        // Mock the function registry to handle the SUM and MULTIPLY functions
        val multiplyFunction = mockk<ExpressionFunction>()
        
        every { functionRegistry.getFunction("SUM") } returns sumFunction
        every { functionRegistry.getFunction("MULTIPLY") } returns multiplyFunction
        
        every { sumFunction.name() } returns "SUM"
        every { multiplyFunction.name() } returns "MULTIPLY"
        
        every { multiplyFunction.evaluate(listOf("B1", "C1")) } returns "60"
        every { sumFunction.evaluate(listOf("A1", "60")) } returns "70"

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("70", result)
    }

    @Test
    fun `test evaluate expression with mixed cell references and literals`() {
        // Given
        val expression = "=A1*2+5"
        val context = mapOf(
            "A1" to "10"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("A1")

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("25.0", result)
    }

    @Test
    fun `test evaluate expression with error in cell reference`() {
        // Given
        val expression = "=A1+B1"
        val context = mapOf(
            "A1" to "10",
            "B1" to "#ERROR!"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("A1", "B1")

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("#ERROR!", result)
    }

    @Test
    fun `test evaluate expression with division by zero`() {
        // Given
        val expression = "=A1/0"
        val context = mapOf(
            "A1" to "10"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("A1")

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("#DIV/0!", result)
    }

    @Test
    fun `test evaluate expression with invalid syntax`() {
        // Given
        val expression = "=A1+"
        val context = mapOf(
            "A1" to "10"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("A1")

        // When
        val result = expressionEvaluator.evaluate(expression, context)

        // Then
        assertEquals("#ERROR!", result)
    }
}
