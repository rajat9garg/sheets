package com.sheets.services.expression.functions

import com.sheets.services.CellService
import com.sheets.services.expression.ExpressionFunction
import com.sheets.services.expression.exception.FunctionEvaluationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.regex.Pattern

@Component
class SumFunction(private val cellService: CellService) : ExpressionFunction {
    
    private val logger = LoggerFactory.getLogger(SumFunction::class.java)
    private val cellReferencePattern = Pattern.compile("(\\d+):(\\d+)")
    private val rangePattern = Pattern.compile("(\\d+):(\\d+)-(\\d+):(\\d+)")
    private val a1CellPattern = Pattern.compile("([A-Z]+)(\\d+)")
    private val a1RangePattern = Pattern.compile("([A-Z]+\\d+):([A-Z]+\\d+)")
    private val sheetIdPattern = Pattern.compile("sheetId=(\\d+)")
    
    override fun name(): String = "SUM"
    
    override fun evaluate(args: List<String>): String {
        if (args.isEmpty()) {
            throw FunctionEvaluationException("SUM", "No arguments provided")
        }
        
        // Extract sheetId from the last argument if it's in the format "sheetId=X"
        var sheetId = 0L
        val argsToProcess = if (args.last().startsWith("sheetId=")) {
            sheetId = args.last().substring("sheetId=".length).toLong()
            logger.info("SUM: Using sheetId: {}", sheetId)
            args.dropLast(1)
        } else {
            args
        }
        
        var sum = 0.0
        
        // Process all arguments except the last one if it contains sheetId
        for (arg in argsToProcess) {
            try {
                // Check if the argument is a range in A1:B2 format
                val a1RangeMatcher = a1RangePattern.matcher(arg)
                if (a1RangeMatcher.matches()) {
                    val startRef = a1RangeMatcher.group(1)
                    val endRef = a1RangeMatcher.group(2)
                    
                    // Parse the A1 references to get row and column
                    val startA1Matcher = a1CellPattern.matcher(startRef)
                    val endA1Matcher = a1CellPattern.matcher(endRef)
                    
                    if (startA1Matcher.matches() && endA1Matcher.matches()) {
                        val startColLetter = startA1Matcher.group(1)
                        val startRow = startA1Matcher.group(2).toInt()
                        
                        val endColLetter = endA1Matcher.group(1)
                        val endRow = endA1Matcher.group(2).toInt()
                        
                        sum += sumRangeA1(startRow, startColLetter, endRow, endColLetter, sheetId)
                        continue
                    }
                }
                
                // Check if the argument is a cell reference in A1 format
                val a1CellMatcher = a1CellPattern.matcher(arg)
                if (a1CellMatcher.matches()) {
                    val colLetter = a1CellMatcher.group(1)
                    val row = a1CellMatcher.group(2).toInt()
                    
                    val cellId = "$sheetId:$row:$colLetter"
                    
                    logger.info("SUM: Looking up cell with ID: {}", cellId)
                    val cell = cellService.getCell(cellId)
                    if (cell != null) {
                        logger.info("SUM: Found cell: {}, value: {}", cellId, cell.evaluatedValue)
                        val value = cell.evaluatedValue.toDoubleOrNull()
                        if (value != null) {
                            sum += value
                            logger.info("SUM: Added value {} to sum, new sum: {}", value, sum)
                        } else {
                            logger.warn("SUM: Could not convert value '{}' to double", cell.evaluatedValue)
                        }
                    } else {
                        logger.warn("SUM: Cell not found: {}", cellId)
                    }
                    continue
                }
                
                // Check if the argument is a range in row:col-row:col format (e.g., 1:1-3:3)
                // This is legacy format - convert to A1 notation
                val rangeMatcher = rangePattern.matcher(arg)
                if (rangeMatcher.matches()) {
                    val startRow = rangeMatcher.group(1).toInt()
                    val startCol = rangeMatcher.group(2).toInt()
                    val endRow = rangeMatcher.group(3).toInt()
                    val endCol = rangeMatcher.group(4).toInt()
                    
                    // Convert to A1 notation
                    val startColLetter = numberToColumnLetter(startCol)
                    val endColLetter = numberToColumnLetter(endCol)
                    
                    sum += sumRangeA1(startRow, startColLetter, endRow, endColLetter, sheetId)
                    continue
                }
                
                // Check if the argument is a cell reference in row:col format (e.g., 1:1)
                // This is legacy format - convert to A1 notation
                val cellMatcher = cellReferencePattern.matcher(arg)
                if (cellMatcher.matches()) {
                    val row = cellMatcher.group(1).toInt()
                    val col = cellMatcher.group(2).toInt()
                    
                    // Convert column number to letter
                    val colLetter = numberToColumnLetter(col)
                    val cellId = "$sheetId:$row:$colLetter"
                    
                    logger.info("SUM: Looking up cell with ID: {}", cellId)
                    val cell = cellService.getCell(cellId)
                    if (cell != null) {
                        logger.info("SUM: Found cell: {}, value: {}", cellId, cell.evaluatedValue)
                        val value = cell.evaluatedValue.toDoubleOrNull()
                        if (value != null) {
                            sum += value
                            logger.info("SUM: Added value {} to sum, new sum: {}", value, sum)
                        } else {
                            logger.warn("SUM: Could not convert value '{}' to double", cell.evaluatedValue)
                        }
                    } else {
                        logger.warn("SUM: Cell not found: {}", cellId)
                    }
                    continue
                }
                
                // If it's not a range or cell reference, treat it as a direct number
                val numValue = arg.toDouble()
                sum += numValue
                logger.info("SUM: Added direct number {} to sum, new sum: {}", numValue, sum)
            } catch (e: NumberFormatException) {
                logger.error("SUM: Invalid number format: {}", arg, e)
                throw FunctionEvaluationException("SUM", "Invalid number format: $arg")
            } catch (e: Exception) {
                logger.error("SUM: Error processing argument: {}", arg, e)
                throw FunctionEvaluationException("SUM", "Error processing argument: $arg - ${e.message}")
            }
        }
        
        logger.info("SUM: Final result: {}", sum)
        return sum.toString()
    }
    
    private fun sumRangeA1(startRow: Int, startColLetter: String, endRow: Int, endColLetter: String, sheetId: Long): Double {
        var sum = 0.0
        val startCol = columnLetterToNumber(startColLetter)
        val endCol = columnLetterToNumber(endColLetter)
        
        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val colLetter = numberToColumnLetter(col)
                val cellId = "$sheetId:$row:$colLetter"
                val cell = cellService.getCell(cellId)
                if (cell != null) {
                    sum += cell.evaluatedValue.toDoubleOrNull() ?: 0.0
                }
            }
        }
        
        return sum
    }
    
    private fun columnLetterToNumber(columnLetter: String): Int {
        var result = 0
        for (c in columnLetter) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result
    }
    
    private fun numberToColumnLetter(columnNumber: Int): String {
        var dividend = columnNumber
        var columnName = ""
        
        while (dividend > 0) {
            val modulo = (dividend - 1) % 26
            columnName = (modulo + 'A'.code).toChar() + columnName
            dividend = (dividend - modulo) / 26
        }
        
        return columnName
    }
}
