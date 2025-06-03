package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.FunctionRegistry
import com.sheets.services.expression.exception.ExpressionEvaluationException
import com.sheets.services.expression.exception.FunctionEvaluationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.*

@Component
class ExpressionEvaluatorImpl(
    private val expressionParser: ExpressionParser,
    private val functionRegistry: FunctionRegistry
) : ExpressionEvaluator {
    
    private val logger = LoggerFactory.getLogger(ExpressionEvaluatorImpl::class.java)
    
    override fun evaluate(expression: String, context: Map<String, String>): String {
        if (!expressionParser.isExpression(expression)) {
            return expression
        }
        
        val trimmedExpression = expression.substring(1).trim()
        
        try {
            if (isFunctionCall(trimmedExpression)) {
                return evaluateFunction(trimmedExpression, context)
            } else {
                return evaluateArithmeticExpression(trimmedExpression, context)
            }
        } catch (e: Exception) {
            when (e) {
                is FunctionEvaluationException -> throw e
                else -> throw ExpressionEvaluationException(expression, e.message ?: "Unknown error")
            }
        }
    }
    
    private fun isFunctionCall(expression: String): Boolean {
        val functionPattern = "^([A-Z]+)\\(.*\\)$".toRegex()
        return functionPattern.matches(expression)
    }
    
    private fun evaluateFunction(expression: String, context: Map<String, String>): String {
        val functionNameEndIndex = expression.indexOf('(')
        if (functionNameEndIndex == -1) {
            throw ExpressionEvaluationException(expression, "Invalid function syntax: missing opening parenthesis")
        }
        
        val functionName = expression.substring(0, functionNameEndIndex)
        val function = functionRegistry.getFunction(functionName)
            ?: throw ExpressionEvaluationException(expression, "Unknown function: $functionName")
        
        val argsString = expression.substring(functionNameEndIndex + 1, expression.length - 1)
        val args = parseArgs(argsString, context).toMutableList()
        
        // Add sheet ID to the args list if available in context
        if (context.containsKey("sheetId")) {
            args.add("sheetId=${context["sheetId"]}")
            logger.info("Added sheetId=${context["sheetId"]} to function arguments")
        }
        
        return function.evaluate(args)
    }
    
    private fun parseArgs(argsString: String, context: Map<String, String>): List<String> {
        if (argsString.isBlank()) {
            return emptyList()
        }
        
        val args = mutableListOf<String>()
        val currentArg = StringBuilder()
        var parenCount = 0
        
        for (c in argsString) {
            when {
                c == ',' && parenCount == 0 -> {
                    args.add(currentArg.toString().trim())
                    currentArg.clear()
                }
                c == '(' -> {
                    parenCount++
                    currentArg.append(c)
                }
                c == ')' -> {
                    parenCount--
                    currentArg.append(c)
                }
                else -> currentArg.append(c)
            }
        }
        
        if (currentArg.isNotEmpty()) {
            args.add(currentArg.toString().trim())
        }
        
        // Process each argument to replace cell references with their values
        return args.map { arg ->
            processCellReference(arg, context)
        }
    }
    
    private fun processCellReference(arg: String, context: Map<String, String>): String {
        // Check if the argument is a direct cell reference (A1, B2, etc.)
        val a1Pattern = "^([A-Z]+)(\\d+)$".toRegex()
        val a1Match = a1Pattern.find(arg)
        
        if (a1Match != null) {
            val col = a1Match.groupValues[1]
            val row = a1Match.groupValues[2]
            
            // Use the A1 reference directly since we now use alphabetical column names
            logger.info("Processing A1 reference: {}", arg)
            
            if (arg in context) {
                logger.info("Found value for {}: {}", arg, context[arg])
                return context[arg] ?: "0"
            }
        }
        
        // Check if the argument is a numeric cell reference (1:1, 2:3, etc.)
        if (arg in context) {
            logger.info("Found value for direct reference {}: {}", arg, context[arg])
            return context[arg] ?: "0"
        }
        
        // If not a cell reference or not found in context, return as is
        return arg
    }
    
    private fun columnLetterToNumber(column: String): Int {
        var result = 0
        for (c in column) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result
    }
    
    private fun evaluateArithmeticExpression(expression: String, context: Map<String, String>): String {
        var processedExpression = expression
        
        logger.info("Evaluating arithmetic expression: {}", expression)
        logger.debug("Context: {}", context)
        
        // Replace A1-style cell references with their values
        val a1Pattern = "([A-Z]+)(\\d+)".toRegex()
        val a1Matches = a1Pattern.findAll(processedExpression)
        
        for (match in a1Matches) {
            val cellRef = match.value
            
            if (cellRef in context) {
                val value = context[cellRef] ?: "0"
                logger.info("Replacing A1 reference {} with {}", cellRef, value)
                processedExpression = processedExpression.replace(cellRef, value)
            } else {
                logger.warn("No value found for cell reference: {}", cellRef)
                processedExpression = processedExpression.replace(cellRef, "0")
            }
        }
        
        // Replace numeric cell references with their values
        val numericPattern = "(\\d+):(\\d+)".toRegex()
        val numericMatches = numericPattern.findAll(processedExpression)
        
        for (match in numericMatches) {
            val cellRef = match.value
            if (cellRef in context) {
                val value = context[cellRef] ?: "0"
                logger.info("Replacing numeric reference {} with {}", cellRef, value)
                processedExpression = processedExpression.replace(cellRef, value)
            } else {
                logger.warn("No value found for cell reference: {}", cellRef)
                processedExpression = processedExpression.replace(cellRef, "0")
            }
        }
        
        logger.info("Processed expression: {}", processedExpression)
        
        try {
            val result = evaluateExpression(processedExpression)
            logger.info("Evaluation result: {}", result)
            return result.toString()
        } catch (e: Exception) {
            logger.error("Failed to evaluate expression: {}", e.message, e)
            throw ExpressionEvaluationException(expression, "Failed to evaluate arithmetic expression: ${e.message}")
        }
    }
    
    /**
     * Custom expression evaluator that supports basic arithmetic operations
     */
    private fun evaluateExpression(expression: String): Double {
        return try {
            val sanitizedExpression = expression.replace(" ", "")
            val tokens = tokenize(sanitizedExpression)
            val result = evaluateTokens(tokens)
            result
        } catch (e: Exception) {
            throw ExpressionEvaluationException(expression, "Invalid arithmetic expression: ${e.message}")
        }
    }
    
    private fun tokenize(expression: String): List<String> {
        val tokens = mutableListOf<String>()
        var i = 0
        
        while (i < expression.length) {
            when {
                expression[i].isDigit() || (expression[i] == '.' && i + 1 < expression.length && expression[i + 1].isDigit()) -> {
                    val start = i
                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) {
                        i++
                    }
                    tokens.add(expression.substring(start, i))
                }
                expression[i] == '+' || expression[i] == '-' || expression[i] == '*' || expression[i] == '/' || 
                expression[i] == '(' || expression[i] == ')' -> {
                    tokens.add(expression[i].toString())
                    i++
                }
                else -> {
                    i++
                }
            }
        }
        
        return tokens
    }
    
    private fun evaluateTokens(tokens: List<String>): Double {
        val values = Stack<Double>()
        val operators = Stack<String>()
        
        for (token in tokens) {
            when {
                token.toDoubleOrNull() != null -> {
                    values.push(token.toDouble())
                }
                token == "(" -> {
                    operators.push(token)
                }
                token == ")" -> {
                    while (operators.peek() != "(") {
                        values.push(applyOperation(operators.pop(), values.pop(), values.pop()))
                    }
                    operators.pop() // Remove the opening parenthesis
                }
                token == "+" || token == "-" || token == "*" || token == "/" -> {
                    while (!operators.empty() && hasPrecedence(token, operators.peek())) {
                        values.push(applyOperation(operators.pop(), values.pop(), values.pop()))
                    }
                    operators.push(token)
                }
            }
        }
        
        while (!operators.empty()) {
            values.push(applyOperation(operators.pop(), values.pop(), values.pop()))
        }
        
        return values.pop()
    }
    
    private fun hasPrecedence(op1: String, op2: String): Boolean {
        if (op2 == "(" || op2 == ")") {
            return false
        }
        return !((op1 == "*" || op1 == "/") && (op2 == "+" || op2 == "-"))
    }
    
    private fun applyOperation(op: String, b: Double, a: Double): Double {
        return when (op) {
            "+" -> a + b
            "-" -> a - b
            "*" -> a * b
            "/" -> if (b == 0.0) throw ArithmeticException("Division by zero") else a / b
            else -> throw IllegalArgumentException("Unknown operator: $op")
        }
    }
}
