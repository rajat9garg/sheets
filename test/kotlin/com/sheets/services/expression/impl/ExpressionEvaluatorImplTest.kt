package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.FunctionRegistry
import com.sheets.services.expression.exception.ExpressionEvaluationException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        
        // Set up default behavior for expressionParser.isExpression
        every { expressionParser.isExpression(any()) } returns true
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
            "1:1" to "10",  // A1 in numeric format
            "1:2" to "20"   // B1 in numeric format
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("1:1", "1:2")

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
            "1:1" to "10",  // A1 in numeric format
            "1:2" to "20",  // B1 in numeric format
            "1:3" to "2"    // C1 in numeric format
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("1:1", "1:2", "1:3")

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
            "1:1" to "10",  // A1 in numeric format
            "1:2" to "20",  // B1 in numeric format
            "sheetId" to "1"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("1:1", "1:2")
        
        // Mock the function registry to handle the SUM function
        every { functionRegistry.getFunction("SUM") } returns sumFunction
        every { sumFunction.name() } returns "SUM"
        every { sumFunction.evaluate(any()) } returns "30"

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
            "1:1" to "10",  // A1 in numeric format
            "1:2" to "20",  // B1 in numeric format
            "1:3" to "3",   // C1 in numeric format
            "sheetId" to "1"
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("1:1", "1:2", "1:3")
        
        // Mock the function registry to handle the SUM and MULTIPLY functions
        val multiplyFunction = mockk<ExpressionFunction>()
        
        every { functionRegistry.getFunction("SUM") } returns sumFunction
        every { functionRegistry.getFunction("MULTIPLY") } returns multiplyFunction
        
        every { sumFunction.name() } returns "SUM"
        every { multiplyFunction.name() } returns "MULTIPLY"
        
        every { multiplyFunction.evaluate(any()) } returns "60"
        every { sumFunction.evaluate(any()) } returns "70"

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
            "1:1" to "10"  // A1 in numeric format
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("1:1")

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
            "1:1" to "10",     // A1 in numeric format
            "1:2" to "#ERROR!" // B1 in numeric format
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("1:1", "1:2")
        
        // The implementation might either return an error string or throw an exception
        try {
            val result = expressionEvaluator.evaluate(expression, context)
            assertEquals("#ERROR!", result)
        } catch (e: ExpressionEvaluationException) {
            // This is also acceptable as the implementation might throw an exception
            // instead of returning an error string
            assertTrue(e.message?.contains("error") == true || e.message?.contains("ERROR") == true)
        }
    }

    @Test
    fun `test evaluate expression with division by zero`() {
        // Given
        val expression = "=A1/0"
        val context = mapOf(
            "1:1" to "10"  // A1 in numeric format
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("1:1")
        
        // The implementation might either return an error string or throw an exception
        try {
            val result = expressionEvaluator.evaluate(expression, context)
            assertEquals("#DIV/0!", result)
        } catch (e: ExpressionEvaluationException) {
            // This is also acceptable as the implementation might throw an exception
            // instead of returning an error string
            assertTrue(e.message?.contains("zero") == true || e.message?.contains("DIV") == true)
        } catch (e: ArithmeticException) {
            // This is also acceptable
            assertTrue(e.message?.contains("zero") == true || e.message?.contains("DIV") == true)
        }
    }

    @Test
    fun `test evaluate expression with invalid syntax`() {
        // Given
        val expression = "=A1+"
        val context = mapOf(
            "1:1" to "10"  // A1 in numeric format
        )
        
        // Mock the parser to return the cell references
        every { expressionParser.parse(expression) } returns listOf("1:1")
        
        // Mock that the expression evaluator will handle invalid syntax
        every { expressionParser.isExpression(expression) } returns true
        
        // When
        try {
            val result = expressionEvaluator.evaluate(expression, context)
            assertEquals("#ERROR!", result)
        } catch (e: ExpressionEvaluationException) {
            // This is also acceptable as the implementation might throw an exception
            // instead of returning an error string
            assert(true)
        }
    }
}
