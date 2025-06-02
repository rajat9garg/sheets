package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.exception.ExpressionParseException
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class ExpressionParserImpl : ExpressionParser {
    
    private val cellReferencePattern = Pattern.compile("[A-Z]+[0-9]+")
    
    override fun parse(expression: String): List<String> {
        if (!isExpression(expression)) {
            throw ExpressionParseException(expression, "Not an expression")
        }
        
        val trimmedExpression = expression.substring(1).trim()
        
        val cellReferences = mutableListOf<String>()
        val matcher = cellReferencePattern.matcher(trimmedExpression)
        
        while (matcher.find()) {
            cellReferences.add(matcher.group())
        }
        
        return cellReferences
    }
    
    override fun isExpression(value: String): Boolean {
        return value.trim().startsWith("=")
    }
}
