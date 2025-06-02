package com.sheets.services.expression.functions

import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.exception.FunctionEvaluationException
import org.springframework.stereotype.Component

@Component
class MaxFunction : ExpressionFunction {
    override fun name(): String = "MAX"
    
    override fun evaluate(args: List<String>): String {
        if (args.isEmpty()) {
            throw FunctionEvaluationException("MAX", "No arguments provided")
        }
        
        var max: Double? = null
        
        for (arg in args) {
            try {
                val value = arg.toDouble()
                if (max == null || value > max) {
                    max = value
                }
            } catch (e: NumberFormatException) {
                throw FunctionEvaluationException("MAX", "Invalid number format: $arg")
            }
        }
        
        return max?.toString() ?: "0"
    }
}
