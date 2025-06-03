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
        
        // Extract the sheet ID from the context (last argument)
        var sheetId = 0L
        val argsToProcess = args.toMutableList()
        val sheetIdArg = argsToProcess.find { it.startsWith("sheetId=") }
        
        if (sheetIdArg != null) {
            try {
                val matcher = sheetIdPattern.matcher(sheetIdArg)
                if (matcher.matches()) {
                    sheetId = matcher.group(1).toLong()
                    logger.info("Extracted sheetId: {} from context", sheetId)
                    argsToProcess.remove(sheetIdArg)
                }
            } catch (e: Exception) {
                logger.warn("Failed to extract sheetId from context: {}", e.message)
            }
        }
        
        // If we couldn't extract a sheet ID, log a warning
        if (sheetId == 0L) {
            logger.warn("No sheetId provided in context, using default")
            sheetId = 14L // Default for backward compatibility, but this should be provided
        }
        
        var sum = 0.0
        
        // Process all arguments except the last one if it contains sheetId
        for (arg in argsToProcess) {
            try {
                // Check if the argument is a range in row:col-row:col format (e.g., 1:1-3:3)
                val rangeMatcher = rangePattern.matcher(arg)
                if (rangeMatcher.matches()) {
                    val startRow = rangeMatcher.group(1).toInt()
                    val startCol = rangeMatcher.group(2).toInt()
                    val endRow = rangeMatcher.group(3).toInt()
                    val endCol = rangeMatcher.group(4).toInt()
                    sum += sumRange(startRow, startCol, endRow, endCol, sheetId)
                    continue
                }
                
                // Check if the argument is a cell reference in row:col format (e.g., 1:1)
                val cellMatcher = cellReferencePattern.matcher(arg)
                if (cellMatcher.matches()) {
                    val row = cellMatcher.group(1).toInt()
                    val col = cellMatcher.group(2).toInt()
                    
                    val cellId = "$sheetId:$row:$col"
                    
                    logger.info("SUM: Looking up cell with ID: {}", cellId)
                    val cell = cellService.getCell(cellId)
                    if (cell != null) {
                        logger.info("SUM: Found cell: {}, value: {}", cellId, cell.evaluatedValue)
                        sum += cell.evaluatedValue.toDoubleOrNull() ?: 0.0
                    } else {
                        logger.warn("SUM: Cell not found: {}", cellId)
                    }
                    continue
                }
                
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
                        val startCol = columnLetterToNumber(startColLetter)
                        
                        val endColLetter = endA1Matcher.group(1)
                        val endRow = endA1Matcher.group(2).toInt()
                        val endCol = columnLetterToNumber(endColLetter)
                        
                        sum += sumRange(startRow, startCol, endRow, endCol, sheetId)
                        continue
                    }
                }
                
                // Check if the argument is a cell reference in A1 format
                val a1CellMatcher = a1CellPattern.matcher(arg)
                if (a1CellMatcher.matches()) {
                    val colLetter = a1CellMatcher.group(1)
                    val row = a1CellMatcher.group(2).toInt()
                    val col = columnLetterToNumber(colLetter)
                    
                    val cellId = "$sheetId:$row:$col"
                    
                    logger.info("SUM: Looking up cell with ID: {}", cellId)
                    val cell = cellService.getCell(cellId)
                    if (cell != null) {
                        logger.info("SUM: Found cell: {}, value: {}", cellId, cell.evaluatedValue)
                        sum += cell.evaluatedValue.toDoubleOrNull() ?: 0.0
                    } else {
                        logger.warn("SUM: Cell not found: {}", cellId)
                    }
                    continue
                }
                
                // If it's not a range or cell reference, treat it as a direct number
                sum += arg.toDouble()
            } catch (e: NumberFormatException) {
                throw FunctionEvaluationException("SUM", "Invalid number format: $arg")
            } catch (e: Exception) {
                throw FunctionEvaluationException("SUM", "Error processing argument: $arg - ${e.message}")
            }
        }
        
        return sum.toString()
    }
    
    private fun sumRange(startRow: Int, startCol: Int, endRow: Int, endCol: Int, sheetId: Long): Double {
        var sum = 0.0
        
        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                val cellId = "$sheetId:$row:$col"
                val cell = cellService.getCell(cellId)
                if (cell != null) {
                    sum += cell.evaluatedValue.toDoubleOrNull() ?: 0.0
                }
            }
        }
        
        return sum
    }
    
    private fun columnLetterToNumber(column: String): Int {
        var result = 0
        for (c in column) {
            result = result * 26 + (c - 'A' + 1)
        }
        return result
    }
}
