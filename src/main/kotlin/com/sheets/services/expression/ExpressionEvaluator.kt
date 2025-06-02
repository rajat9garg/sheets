package com.sheets.services.expression

interface ExpressionEvaluator {
    fun evaluate(expression: String, context: Map<String, String>): String
}
