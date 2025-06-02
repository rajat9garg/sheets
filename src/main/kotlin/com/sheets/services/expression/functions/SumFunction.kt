package com.sheets.services.expression.functions

import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.exception.FunctionEvaluationException
import org.springframework.stereotype.Component

@Component
class SumFunction : ExpressionFunction {
    override fun name(): String = "SUM"
    
    override fun evaluate(args: List<String>): String {
        if (args.isEmpty()) {
            throw FunctionEvaluationException("SUM", "No arguments provided")
        }
        
        var sum = 0.0
        
        for (arg in args) {
            try {
                sum += arg.toDouble()
            } catch (e: NumberFormatException) {
                throw FunctionEvaluationException("SUM", "Invalid number format: $arg")
            }
        }
        
        return sum.toString()
    }
}
