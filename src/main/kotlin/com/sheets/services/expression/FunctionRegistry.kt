package com.sheets.services.expression

interface FunctionRegistry {
    fun getFunction(name: String): ExpressionFunction?
    fun registerFunction(function: ExpressionFunction)
}
