package com.sheets.services.expression.functions

import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.exception.FunctionEvaluationException
import org.springframework.stereotype.Component

@Component
class CountFunction : ExpressionFunction {
    override fun name(): String = "COUNT"
    
    override fun evaluate(args: List<String>): String {
        return args.size.toString()
    }
}
