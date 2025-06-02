package com.sheets.services.expression.functions

import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.exception.FunctionEvaluationException
import org.springframework.stereotype.Component

@Component
class AverageFunction : ExpressionFunction {
    override fun name(): String = "AVERAGE"
    
    override fun evaluate(args: List<String>): String {
        if (args.isEmpty()) {
            throw FunctionEvaluationException("AVERAGE", "No arguments provided")
        }
        
        var sum = 0.0
        var count = 0
        
        for (arg in args) {
            try {
                sum += arg.toDouble()
                count++
            } catch (e: NumberFormatException) {
                throw FunctionEvaluationException("AVERAGE", "Invalid number format: $arg")
            }
        }
        
        if (count == 0) {
            throw FunctionEvaluationException("AVERAGE", "Division by zero")
        }
        
        return (sum / count).toString()
    }
}
