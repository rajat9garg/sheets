package com.sheets.services.`cell-management`

import com.sheets.models.domain.Cell
import com.sheets.models.domain.DataType
import com.sheets.repositories.CellRedisRepository
import com.sheets.services.CellAsyncService
import com.sheets.services.CellDependencyService
import com.sheets.services.expression.CircularDependencyDetector
import com.sheets.services.expression.ExpressionEvaluator
import com.sheets.services.expression.ExpressionParser
import com.sheets.services.expression.exception.CircularDependencyException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Processor for expression data type cells
 */
@Component
class ExpressionDataProcessor(
    private val cellRedisRepository: CellRedisRepository,
    private val cellAsyncService: CellAsyncService,
    private val cellDependencyService: CellDependencyService,
    private val expressionParser: ExpressionParser,
    private val expressionEvaluator: ExpressionEvaluator,
    private val circularDependencyDetector: CircularDependencyDetector
) {
    private val logger = LoggerFactory.getLogger(ExpressionDataProcessor::class.java)


    fun processNewCell(cell: Cell, timestamp: Instant, userId: String): Cell {
        logger.debug("Processing expression for new cell: {}", cell.id)
        
        // Process the expression and extract dependencies
        val (dataType, evaluatedValue, dependencies) = processExpression(
            cell.data, 
            cell.sheetId, 
            cell.row, 
            cell.column
        )
        
        // Create the new cell
        val newCell = cell.copy(
            dataType = dataType,
            evaluatedValue = evaluatedValue,
            createdAt = timestamp,
            updatedAt = timestamp
        )
        
        // Update dependencies
        updateDependencies(cell.id, dependencies, cell.sheetId, timestamp)
        
        // Store the cell in Redis with TTL
        logger.debug("Saving new expression cell to Redis: {}", newCell.id)
        cellRedisRepository.saveCell(newCell)
        
        // Save to MongoDB asynchronously
        logger.debug("Saving new expression cell to MongoDB asynchronously: {}", newCell.id)
        cellAsyncService.saveCell(newCell)
        
        logger.info("Successfully created expression cell: {} by user: {}", cell.id, userId)
        return newCell
    }

    /**
     * Process an existing cell with expression data
     */
    fun processExistingCell(
        existingCell: Cell,
        newCellData: Cell,
        timestamp: Instant,
        userId: String
    ): Cell {
        logger.debug("Processing expression for existing cell: {}", existingCell.id)
        
        // Process the expression and extract dependencies
        val (dataType, evaluatedValue, dependencies) = processExpression(
            newCellData.data, 
            newCellData.sheetId, 
            newCellData.row, 
            newCellData.column
        )
        
        // Update the cell
        val updatedCell = existingCell.copy(
            data = newCellData.data,
            dataType = dataType,
            evaluatedValue = evaluatedValue,
            updatedAt = timestamp
        )
        
        // Update dependencies
        updateDependencies(existingCell.id, dependencies, existingCell.sheetId, timestamp)
        
        // Persist the updated cell
        logger.debug("Saving updated expression cell to Redis: {}", updatedCell.id)
        cellRedisRepository.saveCell(updatedCell)
        
        // Save to MongoDB asynchronously
        logger.debug("Saving updated expression cell to MongoDB asynchronously: {}", updatedCell.id)
        cellAsyncService.saveCell(updatedCell)
        
        // Update dependent cells
        updateDependentCells(existingCell.id, userId)
        
        logger.info("Successfully updated expression cell: {} by user: {}", existingCell.id, userId)
        return updatedCell
    }


    fun processExpression(
        expression: String, 
        sheetId: Long, 
        row: Int, 
        column: String
    ): Triple<DataType, String, List<String>> {
        if (expression.isBlank()) {
            return Triple(DataType.PRIMITIVE, "", emptyList())
        }
        
        if (!CellUtils.isExpression(expression)) {
            return Triple(DataType.PRIMITIVE, expression, emptyList())
        }
        
        // Parse the expression
        logger.debug("Parsing expression: {}", expression)
        val cellReferences = expressionParser.parse(expression)
        
        // Get the dependencies
        logger.debug("Getting dependencies for expression: {}", cellReferences)
        val dependencies = cellReferences.map { cellRef ->
            if (cellRef.contains(":")) {
                "$sheetId:$cellRef"
            } else {
                val colLetter = cellRef.takeWhile { it.isLetter() }
                val rowNum = cellRef.dropWhile { it.isLetter() }.toIntOrNull() ?: row
                "$sheetId:$rowNum:$colLetter"
            }
        }
        
        // Check for circular dependencies
        logger.debug("Checking for circular dependencies")
        val cellId = "$sheetId:$row:$column"
        val dependencyMap = CellUtils.buildDependencyMap(cellDependencyService, sheetId, dependencies)
        val cycle = circularDependencyDetector.detectCircularDependency(cellId, dependencyMap)
        
        if (cycle != null) {
            val pathStr = cycle.joinToString(" -> ")
            logger.warn("Circular dependency detected: {}", pathStr)
            throw CircularDependencyException(listOf(pathStr))
        }
        
        // Evaluate the expression
        logger.debug("Evaluating expression: {}", expression)
        val context = buildEvaluationContext(cellReferences, sheetId, row)
        val evaluatedValue = expressionEvaluator.evaluate(expression, context)
        
        return Triple(DataType.EXPRESSION, evaluatedValue, dependencies)
    }


    private fun buildEvaluationContext(cellReferences: List<String>, sheetId: Long, row: Int): Map<String, String> {
        val context = mutableMapOf<String, String>()
        
        // Add sheet ID to the context
        context["sheetId"] = sheetId.toString()
        
        for (cellRef in cellReferences) {
            if (!cellRef.contains(":")) {
                val colLetter = cellRef.takeWhile { it.isLetter() }
                val rowNum = cellRef.dropWhile { it.isLetter() }.toIntOrNull() ?: row
                val cellId = "$sheetId:$rowNum:$colLetter"
                
                logger.debug("Looking up cell with ID: {} for reference: {}", cellId, cellRef)
                val cell = cellRedisRepository.getCell(cellId)
                if (cell != null) {
                    context[cellRef] = cell.evaluatedValue
                } else {
                    context[cellRef] = "#REF!"
                }
            } else {
                val parts = cellRef.split(":")
                if (parts.size == 2) {
                    val rowNum = parts[0].toIntOrNull() ?: row
                    val colNum = parts[1].toIntOrNull()
                    
                    if (colNum != null) {
                        // Convert column number to letter
                        val colLetter = numberToColumnLetter(colNum)
                        val a1Ref = "$colLetter$rowNum"
                        val cellId = "$sheetId:$rowNum:$colLetter"
                        
                        val cell = cellRedisRepository.getCell(cellId)
                        if (cell != null) {
                            context[cellRef] = cell.evaluatedValue
                            // Also add the A1 reference to the context
                            context[a1Ref] = cell.evaluatedValue
                        } else {
                            context[cellRef] = "#REF!"
                            context[a1Ref] = "#REF!"
                        }
                    } else {
                        // Invalid column number
                        context[cellRef] = "#REF!"
                    }
                } else {
                    // Invalid cell reference format
                    context[cellRef] = "#REF!"
                }
            }
        }
        
        context["row"] = row.toString()
        
        return context
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

    private fun collectCellsToLock(cellId: String, dependencies: List<String>): List<String> {
        val cellsToLock = mutableListOf(cellId)
        cellsToLock.addAll(dependencies)
        
        val dependentCells = cellDependencyService.getDependenciesByTargetCellId(cellId)
            .map { it.sourceCellId }
        cellsToLock.addAll(dependentCells)
        
        return cellsToLock
    }

    private fun updateDependencies(cellId: String, dependencies: List<String>, sheetId: Long, timestamp: Instant) {

        // Delete existing dependencies
        cellDependencyService.deleteBySourceCellId(cellId)
        
        // Create new dependencies if any
        if (dependencies.isNotEmpty()) {
            val cellDependencies = CellUtils.createCellDependencies(sheetId, cellId, dependencies, timestamp)
            cellDependencyService.createDependencies(cellDependencies)
        }
    }

    private fun updateDependentCells(cellId: String, userId: String) {
        cellDependencyService.updateDependentCells(cellId, userId)
    }
}
