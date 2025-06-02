package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.FunctionRegistry
import com.sheets.services.expression.exception.ExpressionEvaluationException
import com.sheets.services.expression.exception.FunctionEvaluationException
import org.springframework.stereotype.Component
import javax.script.ScriptEngineManager

@Component
class ExpressionEvaluatorImpl(
    private val expressionParser: ExpressionParser,
    private val functionRegistry: FunctionRegistry
) : ExpressionEvaluator {
    
    private val scriptEngine = ScriptEngineManager().getEngineByName("JavaScript")
    
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
            throw ExpressionEvaluationException(expression, "Invalid function call format")
        }
        
        val functionName = expression.substring(0, functionNameEndIndex)
        val function = functionRegistry.getFunction(functionName)
            ?: throw ExpressionEvaluationException(expression, "Unknown function: $functionName")
        
        val argsString = expression.substring(functionNameEndIndex + 1, expression.length - 1)
        val args = parseArgs(argsString, context)
        
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
        
        return args.map { arg ->
            if (arg in context) {
                context[arg] ?: "0"
            } else {
                arg
            }
        }
    }
    
    private fun evaluateArithmeticExpression(expression: String, context: Map<String, String>): String {
        var processedExpression = expression
        
        for ((cellRef, value) in context) {
            processedExpression = processedExpression.replace(cellRef, value)
        }
        
        try {
            val result = scriptEngine.eval(processedExpression)
            return result?.toString() ?: "0"
        } catch (e: Exception) {
            throw ExpressionEvaluationException(expression, "Failed to evaluate arithmetic expression: ${e.message}")
        }
    }
}
