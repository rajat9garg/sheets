package com.sheets.services.expression

interface ExpressionFunction {
    fun name(): String
    fun evaluate(args: List<String>): String
}
