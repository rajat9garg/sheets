package com.sheets.services.expression.exception


abstract class ExpressionException(message: String) : RuntimeException(message)

class ExpressionParseException(val expression: String, val reason: String) : 
    ExpressionException("Failed to parse expression: $expression. Reason: $reason")


class ExpressionEvaluationException(val expression: String, val reason: String) : 
    ExpressionException("Failed to evaluate expression: $expression. Reason: $reason")

class CircularDependencyException(val path: List<String>) : 
    ExpressionException("Circular dependency detected: ${path.joinToString(" -> ")}")

class FunctionEvaluationException(val functionName: String, val reason: String) : 
    ExpressionException("Failed to evaluate function $functionName: $reason")
