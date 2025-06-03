package com.sheets.services.expression.impl

import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.exception.ExpressionParseException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.regex.Pattern
import kotlin.text.Regex

@Component
class ExpressionParserImpl : ExpressionParser {
    
    private val logger = LoggerFactory.getLogger(ExpressionParserImpl::class.java)
    private val numericCellReferencePattern = Pattern.compile("(\\d+):(\\d+)")
    private val a1CellReferencePattern = Pattern.compile("([A-Z]+)(\\d+)")
    private val functionPattern = Pattern.compile("([A-Z]+)\\(([^)]+)\\)")
    private val operatorPattern = Pattern.compile("([+\\-*/])")
    
    override fun parse(expression: String): List<String> {
        if (!isExpression(expression)) {
            throw ExpressionParseException(expression, "Not an expression")
        }
        
        val trimmedExpression = expression.substring(1).trim()
        logger.info("Parsing expression: {}", trimmedExpression)
        
        val cellReferences = mutableListOf<String>()
        
        // Find numeric cell references (row:column)
        val numericMatcher = numericCellReferencePattern.matcher(trimmedExpression)
        while (numericMatcher.find()) {
            val cellRef = numericMatcher.group()
            logger.info("Found numeric cell reference: {}", cellRef)
            cellReferences.add(cellRef)
        }
        
        // Find A1 notation cell references (A1, B2, etc.)
        val a1Matcher = a1CellReferencePattern.matcher(trimmedExpression)
        while (a1Matcher.find()) {
            val colLetter = a1Matcher.group(1)
            val row = a1Matcher.group(2)
            val col = columnLetterToNumber(colLetter)
            
            logger.info("Found A1 notation cell reference: {}{} -> {}:{}", colLetter, row, row, col)
            
            // Convert A1 notation to row:column format
            cellReferences.add("$row:$col")
        }
        
        // Check for special variables like 'row' and 'column'
        if (trimmedExpression.contains("row") || trimmedExpression.contains("column")) {
            // These are special variables that will be replaced during evaluation
            // We don't need to add them as cell references
        }
        
        // Parse function arguments for cell references
        val functionMatcher = functionPattern.matcher(trimmedExpression)
        while (functionMatcher.find()) {
            val functionName = functionMatcher.group(1)
            val arguments = functionMatcher.group(2).split(",")
            
            for (arg in arguments) {
                val argTrimmed = arg.trim()
                
                // Check if argument is a cell reference in A1 notation
                val argA1Matcher = a1CellReferencePattern.matcher(argTrimmed)
                if (argA1Matcher.matches()) {
                    val colLetter = argA1Matcher.group(1)
                    val row = argA1Matcher.group(2)
                    val col = columnLetterToNumber(colLetter)
                    
                    // Convert A1 notation to row:column format
                    cellReferences.add("$row:$col")
                }
                
                // Check if argument is a cell reference in numeric notation
                val argNumericMatcher = numericCellReferencePattern.matcher(argTrimmed)
                if (argNumericMatcher.matches()) {
                    cellReferences.add(argTrimmed)
                }
                
                // Check if argument is a range (e.g., A1:B5)
                if (argTrimmed.contains(":") && !argTrimmed.matches(Regex("\\d+:\\d+"))) {
                    val rangeParts = argTrimmed.split(":")
                    if (rangeParts.size == 2) {
                        val startCell = rangeParts[0].trim()
                        val endCell = rangeParts[1].trim()
                        
                        val startA1Matcher = a1CellReferencePattern.matcher(startCell)
                        val endA1Matcher = a1CellReferencePattern.matcher(endCell)
                        
                        if (startA1Matcher.matches() && endA1Matcher.matches()) {
                            val startColLetter = startA1Matcher.group(1)
                            val startRow = startA1Matcher.group(2).toInt()
                            val startCol = columnLetterToNumber(startColLetter)
                            
                            val endColLetter = endA1Matcher.group(1)
                            val endRow = endA1Matcher.group(2).toInt()
                            val endCol = columnLetterToNumber(endColLetter)
                            
                            // Add all cells in the range
                            for (row in startRow..endRow) {
                                for (col in startCol..endCol) {
                                    cellReferences.add("$row:$col")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return cellReferences.distinct()
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
