package com.sheets.services.expression


interface ExpressionParser {

    fun parse(expression: String): List<String>

    fun isExpression(value: String): Boolean
}
