package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.exception.ExpressionParseException
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class ExpressionParserImpl : ExpressionParser {
    
    private val numericCellReferencePattern = Pattern.compile("(\\d+):(\\d+)")
    private val a1CellReferencePattern = Pattern.compile("([A-Z]+)(\\d+)")
    
    override fun parse(expression: String): List<String> {
        if (!isExpression(expression)) {
            throw ExpressionParseException(expression, "Not an expression")
        }
        
        val trimmedExpression = expression.substring(1).trim()
        
        val cellReferences = mutableListOf<String>()
        
        // Find numeric cell references (row:column)
        val numericMatcher = numericCellReferencePattern.matcher(trimmedExpression)
        while (numericMatcher.find()) {
            cellReferences.add(numericMatcher.group())
        }
        
        // Find A1 notation cell references
        val a1Matcher = a1CellReferencePattern.matcher(trimmedExpression)
        while (a1Matcher.find()) {
            val colLetter = a1Matcher.group(1)
            val row = a1Matcher.group(2)
            val col = columnLetterToNumber(colLetter)
            
            // Convert A1 notation to row:column format
            cellReferences.add("$row:$col")
        }
        
        return cellReferences
    }
    
    override fun isExpression(value: String): Boolean {
        return value.trim().startsWith("=")
    }
    
    private fun columnLetterToNumber(column: String): Int {
        var result = 0
        for (c in column) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result
    }
}
