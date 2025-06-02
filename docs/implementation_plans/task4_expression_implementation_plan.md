# Expression Parsing and Evaluation Implementation Plan

## Overview

This document outlines the detailed implementation plan for the expression parsing and evaluation component of the Cell Management functionality in Task4. This is a critical part of the implementation that will enable cells to contain formulas that reference other cells.

## Table of Contents

1. [Requirements](#1-requirements)
2. [Design Approach](#2-design-approach)
3. [Component Structure](#3-component-structure)
4. [Expression Parser](#4-expression-parser)
5. [Expression Evaluator](#5-expression-evaluator)
6. [Expression Functions](#6-expression-functions)
7. [Cell Reference Resolution](#7-cell-reference-resolution)
8. [Circular Dependency Detection](#8-circular-dependency-detection)
9. [Error Handling](#9-error-handling)
10. [Testing Strategy](#10-testing-strategy)

## 1. Requirements

The expression parsing and evaluation component must:

1. Identify if a cell value is an expression (starts with "=")
2. Parse expressions to identify cell references and functions
3. Evaluate expressions to calculate the result
4. Handle common spreadsheet functions (SUM, AVERAGE, MIN, MAX, COUNT)
5. Detect and handle circular dependencies
6. Provide clear error messages for invalid expressions

## 2. Design Approach

We'll use the following design patterns for this implementation:

1. **Interpreter Pattern**: For parsing and evaluating expressions
2. **Strategy Pattern**: For different expression evaluation strategies
3. **Factory Pattern**: For creating expression function instances
4. **Composite Pattern**: For building expression trees
5. **Visitor Pattern**: For traversing and evaluating expression trees

## 3. Component Structure

The expression parsing and evaluation component will consist of the following parts:

1. **Expression Parser**: Parses expressions and extracts cell references
2. **Expression Evaluator**: Evaluates expressions to calculate results
3. **Expression Functions**: Implements common spreadsheet functions
4. **Cell Reference Resolver**: Resolves cell references to actual values
5. **Circular Dependency Detector**: Detects circular dependencies in expressions

## 4. Expression Parser

### 4.1 ExpressionParser Interface

```kotlin
package com.sheets.services.expression

/**
 * Interface for parsing expressions
 */
interface ExpressionParser {
    /**
     * Parse an expression and extract cell references
     * @param expression The expression to parse
     * @return A list of cell references found in the expression
     */
    fun parse(expression: String): List<String>
    
    /**
     * Check if a string is an expression
     * @param value The string to check
     * @return True if the string is an expression, false otherwise
     */
    fun isExpression(value: String): Boolean
}
```

### 4.2 ExpressionParserImpl

```kotlin
package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionParser
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Implementation of the ExpressionParser interface
 */
@Component
class ExpressionParserImpl : ExpressionParser {
    
    // Pattern for cell references (e.g., A1, B2, etc.)
    private val cellReferencePattern = Pattern.compile("[A-Z]+[0-9]+")
    
    // Pattern for functions (e.g., SUM, AVERAGE, etc.)
    private val functionPattern = Pattern.compile("([A-Z]+)\\(([^)]+)\\)")
    
    override fun isExpression(value: String): Boolean {
        return value.startsWith("=")
    }
    
    override fun parse(expression: String): List<String> {
        if (!isExpression(expression)) {
            return emptyList()
        }
        
        // Remove the leading "=" and any whitespace
        val cleanExpression = expression.substring(1).trim()
        
        // Extract cell references
        val cellReferences = mutableListOf<String>()
        val cellMatcher = cellReferencePattern.matcher(cleanExpression)
        while (cellMatcher.find()) {
            cellReferences.add(cellMatcher.group())
        }
        
        // Extract cell references from functions
        val functionMatcher = functionPattern.matcher(cleanExpression)
        while (functionMatcher.find()) {
            val functionArgs = functionMatcher.group(2)
            
            // Handle range references (e.g., A1:A5)
            if (functionArgs.contains(":")) {
                val rangeParts = functionArgs.split(":")
                if (rangeParts.size == 2) {
                    val startCell = rangeParts[0].trim()
                    val endCell = rangeParts[1].trim()
                    
                    // Extract row and column from start and end cells
                    val startColumn = startCell.replace("\\d".toRegex(), "")
                    val startRow = startCell.replace("\\D".toRegex(), "").toInt()
                    val endColumn = endCell.replace("\\d".toRegex(), "")
                    val endRow = endCell.replace("\\D".toRegex(), "").toInt()
                    
                    // Add all cells in the range
                    for (col in columnToIndex(startColumn)..columnToIndex(endColumn)) {
                        for (row in startRow..endRow) {
                            cellReferences.add("${indexToColumn(col)}$row")
                        }
                    }
                }
            } else {
                // Handle comma-separated cell references
                val args = functionArgs.split(",")
                for (arg in args) {
                    val argCellMatcher = cellReferencePattern.matcher(arg.trim())
                    while (argCellMatcher.find()) {
                        cellReferences.add(argCellMatcher.group())
                    }
                }
            }
        }
        
        return cellReferences.distinct()
    }
    
    /**
     * Convert a column letter to a 0-based index
     * @param column The column letter (e.g., A, B, C, etc.)
     * @return The 0-based index of the column
     */
    private fun columnToIndex(column: String): Int {
        var result = 0
        for (c in column) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result - 1
    }
    
    /**
     * Convert a 0-based index to a column letter
     * @param index The 0-based index of the column
     * @return The column letter (e.g., A, B, C, etc.)
     */
    private fun indexToColumn(index: Int): String {
        var temp = index + 1
        val result = StringBuilder()
        while (temp > 0) {
            val remainder = (temp - 1) % 26
            result.insert(0, ('A' + remainder))
            temp = (temp - remainder) / 26
        }
        return result.toString()
    }
}

## 5. Expression Evaluator

### 5.1 ExpressionEvaluator Interface

```kotlin
package com.sheets.services.expression

/**
 * Interface for evaluating expressions
 */
interface ExpressionEvaluator {
    /**
     * Evaluate an expression
     * @param expression The expression to evaluate
     * @param context The context containing cell values
     * @return The result of the evaluation
     */
    fun evaluate(expression: String, context: Map<String, String>): String
}
```

### 5.2 ExpressionEvaluatorImpl

```kotlin
package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.function.ExpressionFunction
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * Implementation of the ExpressionEvaluator interface
 */
@Component
class ExpressionEvaluatorImpl(
    private val expressionParser: ExpressionParser,
    private val expressionFunctions: List<ExpressionFunction>
) : ExpressionEvaluator {
    
    // Pattern for functions (e.g., SUM, AVERAGE, etc.)
    private val functionPattern = Pattern.compile("([A-Z]+)\\(([^)]+)\\)")
    
    // Pattern for cell references (e.g., A1, B2, etc.)
    private val cellReferencePattern = Pattern.compile("[A-Z]+[0-9]+")
    
    // Pattern for operators
    private val operatorPattern = Pattern.compile("[+\\-*/]")
    
    override fun evaluate(expression: String, context: Map<String, String>): String {
        if (!expressionParser.isExpression(expression)) {
            return expression
        }
        
        // Remove the leading "=" and any whitespace
        var cleanExpression = expression.substring(1).trim()
        
        // Evaluate functions
        val functionMatcher = functionPattern.matcher(cleanExpression)
        while (functionMatcher.find()) {
            val functionName = functionMatcher.group(1)
            val functionArgs = functionMatcher.group(2)
            
            // Find the function implementation
            val function = expressionFunctions.find { it.name() == functionName }
                ?: throw IllegalArgumentException("Unknown function: $functionName")
            
            // Evaluate the function
            val result = evaluateFunction(function, functionArgs, context)
            
            // Replace the function call with its result
            cleanExpression = cleanExpression.replace("$functionName($functionArgs)", result)
        }
        
        // Replace cell references with their values
        val cellMatcher = cellReferencePattern.matcher(cleanExpression)
        while (cellMatcher.find()) {
            val cellReference = cellMatcher.group()
            val cellValue = context[cellReference] ?: "0"
            cleanExpression = cleanExpression.replace(cellReference, cellValue)
        }
        
        // Evaluate the remaining expression
        return evaluateArithmeticExpression(cleanExpression)
    }
    
    /**
     * Evaluate a function
     * @param function The function to evaluate
     * @param args The function arguments
     * @param context The context containing cell values
     * @return The result of the function evaluation
     */
    private fun evaluateFunction(
        function: ExpressionFunction,
        args: String,
        context: Map<String, String>
    ): String {
        // Handle range references (e.g., A1:A5)
        if (args.contains(":")) {
            val rangeParts = args.split(":")
            if (rangeParts.size == 2) {
                val startCell = rangeParts[0].trim()
                val endCell = rangeParts[1].trim()
                
                // Extract row and column from start and end cells
                val startColumn = startCell.replace("\\d".toRegex(), "")
                val startRow = startCell.replace("\\D".toRegex(), "").toInt()
                val endColumn = endCell.replace("\\d".toRegex(), "")
                val endRow = endCell.replace("\\D".toRegex(), "").toInt()
                
                // Get all cell values in the range
                val cellValues = mutableListOf<String>()
                for (col in columnToIndex(startColumn)..columnToIndex(endColumn)) {
                    for (row in startRow..endRow) {
                        val cellReference = "${indexToColumn(col)}$row"
                        val cellValue = context[cellReference] ?: "0"
                        cellValues.add(cellValue)
                    }
                }
                
                // Evaluate the function with the cell values
                return function.evaluate(cellValues)
            }
        }
        
        // Handle comma-separated cell references
        val argList = args.split(",").map { it.trim() }
        val evaluatedArgs = argList.map { arg ->
            if (cellReferencePattern.matcher(arg).matches()) {
                context[arg] ?: "0"
            } else {
                arg
            }
        }
        
        // Evaluate the function with the evaluated arguments
        return function.evaluate(evaluatedArgs)
    }
    
    /**
     * Evaluate an arithmetic expression
     * @param expression The expression to evaluate
     * @return The result of the evaluation
     */
    private fun evaluateArithmeticExpression(expression: String): String {
        // Use a simple expression evaluator for basic arithmetic
        // In a real implementation, you would use a more robust expression evaluator
        return try {
            val result = javax.script.ScriptEngineManager().getEngineByName("JavaScript").eval(expression)
            result.toString()
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
    
    /**
     * Convert a column letter to a 0-based index
     * @param column The column letter (e.g., A, B, C, etc.)
     * @return The 0-based index of the column
     */
    private fun columnToIndex(column: String): Int {
        var result = 0
        for (c in column) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result - 1
    }
    
    /**
     * Convert a 0-based index to a column letter
     * @param index The 0-based index of the column
     * @return The column letter (e.g., A, B, C, etc.)
     */
    private fun indexToColumn(index: Int): String {
        var temp = index + 1
        val result = StringBuilder()
        while (temp > 0) {
            val remainder = (temp - 1) % 26
            result.insert(0, ('A' + remainder))
            temp = (temp - remainder) / 26
        }
        return result.toString()
    }
}

## 6. Expression Functions

### 6.1 ExpressionFunction Interface

```kotlin
package com.sheets.services.expression.function

/**
 * Interface for expression functions
 */
interface ExpressionFunction {
    /**
     * Get the name of the function
     * @return The function name
     */
    fun name(): String
    
    /**
     * Evaluate the function with the given arguments
     * @param args The function arguments
     * @return The result of the function evaluation
     */
    fun evaluate(args: List<String>): String
}
```

### 6.2 Function Implementations

#### 6.2.1 SumFunction

```kotlin
package com.sheets.services.expression.function.impl

import com.sheets.services.expression.function.ExpressionFunction
import org.springframework.stereotype.Component

/**
 * Implementation of the SUM function
 */
@Component
class SumFunction : ExpressionFunction {
    override fun name(): String = "SUM"
    
    override fun evaluate(args: List<String>): String {
        val sum = args.sumOf { arg ->
            try {
                arg.toDouble()
            } catch (e: NumberFormatException) {
                0.0
            }
        }
        
        // Format the result
        return if (sum == sum.toInt().toDouble()) {
            sum.toInt().toString()
        } else {
            sum.toString()
        }
    }
}

#### 6.2.2 AverageFunction

```kotlin
package com.sheets.services.expression.function.impl

import com.sheets.services.expression.function.ExpressionFunction
import org.springframework.stereotype.Component

/**
 * Implementation of the AVERAGE function
 */
@Component
class AverageFunction : ExpressionFunction {
    override fun name(): String = "AVERAGE"
    
    override fun evaluate(args: List<String>): String {
        if (args.isEmpty()) {
            return "0"
        }
        
        val validArgs = args.mapNotNull { arg ->
            try {
                arg.toDouble()
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        if (validArgs.isEmpty()) {
            return "0"
        }
        
        val average = validArgs.sum() / validArgs.size
        
        // Format the result
        return if (average == average.toInt().toDouble()) {
            average.toInt().toString()
        } else {
            average.toString()
        }
    }
}

#### 6.2.3 MinFunction

```kotlin
package com.sheets.services.expression.function.impl

import com.sheets.services.expression.function.ExpressionFunction
import org.springframework.stereotype.Component

/**
 * Implementation of the MIN function
 */
@Component
class MinFunction : ExpressionFunction {
    override fun name(): String = "MIN"
    
    override fun evaluate(args: List<String>): String {
        val validArgs = args.mapNotNull { arg ->
            try {
                arg.toDouble()
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        if (validArgs.isEmpty()) {
            return "0"
        }
        
        val min = validArgs.minOrNull() ?: 0.0
        
        // Format the result
        return if (min == min.toInt().toDouble()) {
            min.toInt().toString()
        } else {
            min.toString()
        }
    }
}

#### 6.2.4 MaxFunction

```kotlin
package com.sheets.services.expression.function.impl

import com.sheets.services.expression.function.ExpressionFunction
import org.springframework.stereotype.Component

/**
 * Implementation of the MAX function
 */
@Component
class MaxFunction : ExpressionFunction {
    override fun name(): String = "MAX"
    
    override fun evaluate(args: List<String>): String {
        val validArgs = args.mapNotNull { arg ->
            try {
                arg.toDouble()
            } catch (e: NumberFormatException) {
                null
            }
        }
        
        if (validArgs.isEmpty()) {
            return "0"
        }
        
        val max = validArgs.maxOrNull() ?: 0.0
        
        // Format the result
        return if (max == max.toInt().toDouble()) {
            max.toInt().toString()
        } else {
            max.toString()
        }
    }
}

#### 6.2.5 CountFunction

```kotlin
package com.sheets.services.expression.function.impl

import com.sheets.services.expression.function.ExpressionFunction
import org.springframework.stereotype.Component

/**
 * Implementation of the COUNT function
 */
@Component
class CountFunction : ExpressionFunction {
    override fun name(): String = "COUNT"
    
    override fun evaluate(args: List<String>): String {
        val count = args.count { arg ->
            try {
                arg.toDouble()
                true
            } catch (e: NumberFormatException) {
                false
            }
        }
        
        return count.toString()
    }
}

### 6.3 Function Factory

```kotlin
package com.sheets.services.expression.function

import org.springframework.stereotype.Component

/**
 * Factory for creating expression functions
 */
@Component
class ExpressionFunctionFactory(
    private val functions: List<ExpressionFunction>
) {
    /**
     * Get a function by name
     * @param name The function name
     * @return The function, or null if not found
     */
    fun getFunction(name: String): ExpressionFunction? {
        return functions.find { it.name() == name }
    }
    
    /**
     * Get all available functions
     * @return A list of all available functions
     */
    fun getAllFunctions(): List<ExpressionFunction> {
        return functions
    }
}

## 7. Cell Reference Resolution

### 7.1 CellReferenceResolver Interface

```kotlin
package com.sheets.services.expression

/**
 * Interface for resolving cell references
 */
interface CellReferenceResolver {
    /**
     * Resolve a cell reference to its value
     * @param sheetId The ID of the sheet
     * @param cellReference The cell reference (e.g., A1, B2, etc.)
     * @return The cell value
     */
    fun resolveCellReference(sheetId: Long, cellReference: String): String
    
    /**
     * Resolve multiple cell references to their values
     * @param sheetId The ID of the sheet
     * @param cellReferences The cell references
     * @return A map of cell references to their values
     */
    fun resolveCellReferences(sheetId: Long, cellReferences: List<String>): Map<String, String>
}

### 7.2 CellReferenceResolverImpl

```kotlin
package com.sheets.services.expression.impl

import com.sheets.repositories.CellRepository
import com.sheets.services.expression.CellReferenceResolver
import org.springframework.stereotype.Component

/**
 * Implementation of the CellReferenceResolver interface
 */
@Component
class CellReferenceResolverImpl(
    private val cellRepository: CellRepository
) : CellReferenceResolver {
    
    override fun resolveCellReference(sheetId: Long, cellReference: String): String {
        // Extract row and column from the cell reference
        val column = cellReference.replace("\\d".toRegex(), "")
        val row = cellReference.replace("\\D".toRegex(), "").toInt()
        
        // Convert column to index
        val columnIndex = columnToIndex(column)
        
        // Create the cell ID
        val cellId = "$sheetId:$row:$columnIndex"
        
        // Find the cell
        val cell = cellRepository.findById(cellId)
        
        // Return the cell value or 0 if not found
        return cell?.evaluatedValue ?: "0"
    }
    
    override fun resolveCellReferences(sheetId: Long, cellReferences: List<String>): Map<String, String> {
        // Create a map of cell references to their values
        val result = mutableMapOf<String, String>()
        
        // Resolve each cell reference
        for (cellReference in cellReferences) {
            result[cellReference] = resolveCellReference(sheetId, cellReference)
        }
        
        return result
    }
    
    /**
     * Convert a column letter to a 0-based index
     * @param column The column letter (e.g., A, B, C, etc.)
     * @return The 0-based index of the column
     */
    private fun columnToIndex(column: String): Int {
        var result = 0
        for (c in column) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result - 1
    }
}

## 8. Circular Dependency Detection

### 8.1 CircularDependencyDetector Interface

```kotlin
package com.sheets.services.expression

/**
 * Interface for detecting circular dependencies
 */
interface CircularDependencyDetector {
    /**
     * Check if adding a dependency would create a circular dependency
     * @param sheetId The ID of the sheet
     * @param sourceCellId The ID of the source cell
     * @param targetCellIds The IDs of the target cells
     * @return True if a circular dependency would be created, false otherwise
     */
    fun wouldCreateCircularDependency(
        sheetId: Long,
        sourceCellId: String,
        targetCellIds: List<String>
    ): Boolean
    
    /**
     * Get the path of a circular dependency
     * @param sheetId The ID of the sheet
     * @param sourceCellId The ID of the source cell
     * @param targetCellIds The IDs of the target cells
     * @return The path of the circular dependency, or an empty list if none exists
     */
    fun getCircularDependencyPath(
        sheetId: Long,
        sourceCellId: String,
        targetCellIds: List<String>
    ): List<String>
}

### 8.2 CircularDependencyDetectorImpl

```kotlin
package com.sheets.services.expression.impl

import com.sheets.repositories.CellDependencyRepository
import com.sheets.services.expression.CircularDependencyDetector
import org.springframework.stereotype.Component

/**
 * Implementation of the CircularDependencyDetector interface
 */
@Component
class CircularDependencyDetectorImpl(
    private val cellDependencyRepository: CellDependencyRepository
) : CircularDependencyDetector {
    
    override fun wouldCreateCircularDependency(
        sheetId: Long,
        sourceCellId: String,
        targetCellIds: List<String>
    ): Boolean {
        // Check if any of the target cells depend on the source cell
        return getCircularDependencyPath(sheetId, sourceCellId, targetCellIds).isNotEmpty()
    }
    
    override fun getCircularDependencyPath(
        sheetId: Long,
        sourceCellId: String,
        targetCellIds: List<String>
    ): List<String> {
        // For each target cell, check if it depends on the source cell
        for (targetCellId in targetCellIds) {
            val path = findDependencyPath(sheetId, targetCellId, sourceCellId, mutableListOf())
            if (path.isNotEmpty()) {
                // Add the source cell to complete the circular path
                return path + sourceCellId
            }
        }
        
        return emptyList()
    }
    
    /**
     * Find a dependency path from a source cell to a target cell
     * @param sheetId The ID of the sheet
     * @param sourceCellId The ID of the source cell
     * @param targetCellId The ID of the target cell
     * @param visited The cells already visited
     * @return The dependency path, or an empty list if none exists
     */
    private fun findDependencyPath(
        sheetId: Long,
        sourceCellId: String,
        targetCellId: String,
        visited: MutableList<String>
    ): List<String> {
        // If we've already visited this cell, stop to avoid infinite recursion
        if (sourceCellId in visited) {
            return emptyList()
        }
        
        // Add the source cell to the visited list
        visited.add(sourceCellId)
        
        // Get the dependencies of the source cell
        val dependencies = cellDependencyRepository.findBySourceCellId(sourceCellId)
        
        // Check if any of the dependencies is the target cell
        for (dependency in dependencies) {
            if (dependency.targetCellId == targetCellId) {
                return listOf(sourceCellId)
            }
            
            // Recursively check the dependencies of the dependency
            val path = findDependencyPath(sheetId, dependency.targetCellId, targetCellId, visited)
            if (path.isNotEmpty()) {
                return listOf(sourceCellId) + path
            }
        }
        
        return emptyList()
    }
}

## 9. Error Handling

### 9.1 Expression Exceptions

```kotlin
package com.sheets.services.expression.exception

/**
 * Base exception for expression-related errors
 */
abstract class ExpressionException(message: String) : RuntimeException(message)

/**
 * Exception thrown when an expression cannot be parsed
 */
class ExpressionParseException(
    val expression: String,
    val reason: String
) : ExpressionException("Failed to parse expression: $expression. Reason: $reason")

/**
 * Exception thrown when an expression cannot be evaluated
 */
class ExpressionEvaluationException(
    val expression: String,
    val reason: String
) : ExpressionException("Failed to evaluate expression: $expression. Reason: $reason")

/**
 * Exception thrown when a circular dependency is detected
 */
class CircularDependencyException(
    val path: List<String>
) : ExpressionException("Circular dependency detected: ${path.joinToString(" -> ")}")

/**
 * Exception thrown when a function is not found
 */
class FunctionNotFoundException(
    val functionName: String
) : ExpressionException("Function not found: $functionName")

/**
 * Exception thrown when a function has invalid arguments
 */
class InvalidFunctionArgumentsException(
    val functionName: String,
    val args: List<String>,
    val reason: String
) : ExpressionException("Invalid arguments for function $functionName: ${args.joinToString(", ")}. Reason: $reason")

### 9.2 Error Handling in ExpressionEvaluator

```kotlin
// Add to ExpressionEvaluatorImpl
private fun evaluateFunction(
    function: ExpressionFunction,
    args: String,
    context: Map<String, String>
): String {
    try {
        // Existing implementation
    } catch (e: Exception) {
        throw ExpressionEvaluationException(
            expression = "${function.name()}($args)",
            reason = e.message ?: "Unknown error"
        )
    }
}

private fun evaluateArithmeticExpression(expression: String): String {
    return try {
        // Existing implementation
    } catch (e: Exception) {
        throw ExpressionEvaluationException(
            expression = expression,
            reason = e.message ?: "Unknown error"
        )
    }
}

## 10. Testing Strategy

### 10.1 Unit Tests for ExpressionParser

```kotlin
package com.sheets.services.expression.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExpressionParserImplTest {
    
    private val expressionParser = ExpressionParserImpl()
    
    @Test
    fun testIsExpression() {
        assertTrue(expressionParser.isExpression("=A1"))
        assertTrue(expressionParser.isExpression("=SUM(A1:A5)"))
        assertFalse(expressionParser.isExpression("A1"))
        assertFalse(expressionParser.isExpression("123"))
    }
    
    @Test
    fun testParseCellReference() {
        val cellReferences = expressionParser.parse("=A1")
        assertEquals(1, cellReferences.size)
        assertEquals("A1", cellReferences[0])
    }
    
    @Test
    fun testParseMultipleCellReferences() {
        val cellReferences = expressionParser.parse("=A1+B2+C3")
        assertEquals(3, cellReferences.size)
        assertTrue(cellReferences.contains("A1"))
        assertTrue(cellReferences.contains("B2"))
        assertTrue(cellReferences.contains("C3"))
    }
    
    @Test
    fun testParseFunction() {
        val cellReferences = expressionParser.parse("=SUM(A1,A2,A3)")
        assertEquals(3, cellReferences.size)
        assertTrue(cellReferences.contains("A1"))
        assertTrue(cellReferences.contains("A2"))
        assertTrue(cellReferences.contains("A3"))
    }
    
    @Test
    fun testParseRangeFunction() {
        val cellReferences = expressionParser.parse("=SUM(A1:A3)")
        assertEquals(3, cellReferences.size)
        assertTrue(cellReferences.contains("A1"))
        assertTrue(cellReferences.contains("A2"))
        assertTrue(cellReferences.contains("A3"))
    }
    
    @Test
    fun testParseNestedFunctions() {
        val cellReferences = expressionParser.parse("=SUM(A1,AVERAGE(B1:B3),C1)")
        assertEquals(5, cellReferences.size)
        assertTrue(cellReferences.contains("A1"))
        assertTrue(cellReferences.contains("B1"))
        assertTrue(cellReferences.contains("B2"))
        assertTrue(cellReferences.contains("B3"))
        assertTrue(cellReferences.contains("C1"))
    }
}

### 10.2 Unit Tests for ExpressionEvaluator

```kotlin
package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.function.ExpressionFunction
import com.sheets.services.expression.function.impl.AverageFunction
import com.sheets.services.expression.function.impl.SumFunction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class ExpressionEvaluatorImplTest {
    
    private lateinit var expressionParser: ExpressionParser
    private lateinit var expressionEvaluator: ExpressionEvaluatorImpl
    private lateinit var sumFunction: ExpressionFunction
    private lateinit var averageFunction: ExpressionFunction
    
    @BeforeEach
    fun setup() {
        expressionParser = mock(ExpressionParser::class.java)
        sumFunction = SumFunction()
        averageFunction = AverageFunction()
        expressionEvaluator = ExpressionEvaluatorImpl(
            expressionParser = expressionParser,
            expressionFunctions = listOf(sumFunction, averageFunction)
        )
        
        `when`(expressionParser.isExpression("=A1")).thenReturn(true)
        `when`(expressionParser.isExpression("=A1+B1")).thenReturn(true)
        `when`(expressionParser.isExpression("=SUM(A1:A3)")).thenReturn(true)
        `when`(expressionParser.isExpression("=AVERAGE(A1:A3)")).thenReturn(true)
        `when`(expressionParser.isExpression("123")).thenReturn(false)
    }
    
    @Test
    fun testEvaluateNonExpression() {
        assertEquals("123", expressionEvaluator.evaluate("123", emptyMap()))
    }
    
    @Test
    fun testEvaluateCellReference() {
        val context = mapOf("A1" to "123")
        assertEquals("123", expressionEvaluator.evaluate("=A1", context))
    }
    
    @Test
    fun testEvaluateArithmeticExpression() {
        val context = mapOf("A1" to "123", "B1" to "456")
        assertEquals("579", expressionEvaluator.evaluate("=A1+B1", context))
    }
    
    @Test
    fun testEvaluateSumFunction() {
        val context = mapOf("A1" to "1", "A2" to "2", "A3" to "3")
        assertEquals("6", expressionEvaluator.evaluate("=SUM(A1:A3)", context))
    }
    
    @Test
    fun testEvaluateAverageFunction() {
        val context = mapOf("A1" to "1", "A2" to "2", "A3" to "3")
        assertEquals("2", expressionEvaluator.evaluate("=AVERAGE(A1:A3)", context))
    }
}

### 10.3 Integration Tests

```kotlin
package com.sheets.services.expression

import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRepository
import com.sheets.services.expression.impl.ExpressionEvaluatorImpl
import com.sheets.services.expression.impl.ExpressionParserImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.mockito.Mockito.`when`
import java.time.Instant

@SpringBootTest
class ExpressionIntegrationTest {
    
    @Autowired
    private lateinit var expressionParser: ExpressionParser
    
    @Autowired
    private lateinit var expressionEvaluator: ExpressionEvaluator
    
    @MockBean
    private lateinit var cellRepository: CellRepository
    
    @BeforeEach
    fun setup() {
        // Set up test cells
        val cell1 = Cell(
            id = "1:1:0", // A1
            sheetId = 1L,
            row = 1,
            column = 0,
            data = "10",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "10",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cell2 = Cell(
            id = "1:2:0", // A2
            sheetId = 1L,
            row = 2,
            column = 0,
            data = "20",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "20",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        val cell3 = Cell(
            id = "1:3:0", // A3
            sheetId = 1L,
            row = 3,
            column = 0,
            data = "30",
            dataType = DataType.PRIMITIVE,
            evaluatedValue = "30",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        
        `when`(cellRepository.findById("1:1:0")).thenReturn(cell1)
        `when`(cellRepository.findById("1:2:0")).thenReturn(cell2)
        `when`(cellRepository.findById("1:3:0")).thenReturn(cell3)
    }
    
    @Test
    fun testParseAndEvaluateExpression() {
        // Parse the expression to get cell references
        val expression = "=SUM(A1:A3)"
        val cellReferences = expressionParser.parse(expression)
        
        // Create a context with cell values
        val context = mapOf(
            "A1" to "10",
            "A2" to "20",
            "A3" to "30"
        )
        
        // Evaluate the expression
        val result = expressionEvaluator.evaluate(expression, context)
        
        // Verify the result
        assertEquals("60", result)
    }
}

## Conclusion

This implementation plan provides a detailed approach to implementing the expression parsing and evaluation component of the Cell Management functionality. By following this plan, we'll create a robust and extensible system for handling cell expressions.

The implementation follows best practices for Spring Boot and leverages design patterns to ensure extensibility and maintainability. The comprehensive testing strategy will ensure that the system works correctly and handles edge cases properly.
