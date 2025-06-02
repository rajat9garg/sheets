package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.FunctionRegistry
import org.springframework.stereotype.Component

@Component
class FunctionRegistryImpl : FunctionRegistry {
    private val functions = mutableMapOf<String, ExpressionFunction>()
    
    override fun getFunction(name: String): ExpressionFunction? {
        return functions[name]
    }
    
    override fun registerFunction(function: ExpressionFunction) {
        functions[function.name()] = function
    }
}
