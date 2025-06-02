package com.sheets.services.expression.functions

import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.exception.FunctionEvaluationException
import org.springframework.stereotype.Component

@Component
class MinFunction : ExpressionFunction {
    override fun name(): String = "MIN"
    
    override fun evaluate(args: List<String>): String {
        if (args.isEmpty()) {
            throw FunctionEvaluationException("MIN", "No arguments provided")
        }
        
        var min: Double? = null
        
        for (arg in args) {
            try {
                val value = arg.toDouble()
                if (min == null || value < min) {
                    min = value
                }
            } catch (e: NumberFormatException) {
                throw FunctionEvaluationException("MIN", "Invalid number format: $arg")
            }
        }
        
        return min?.toString() ?: "0"
    }
}
