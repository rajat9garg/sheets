package com.sheets.services.`cell-management`

import com.sheets.models.domain.Cell
import com.sheets.models.domain.CellDependency
import com.sheets.services.CellDependencyService
import java.time.Instant

/**
 * Utility class for cell management operations
 */
object CellUtils {

    /**
     * Check if a string is an expression (starts with '=')
     */
    fun isExpression(data: String): Boolean {
        return data.startsWith("=")
    }

    /**
     * Create cell dependencies for a list of target cell IDs
     */
    fun createCellDependencies(
        sheetId: Long,
        sourceCellId: String,
        targetCellIds: List<String>,
        timestamp: Instant
    ): List<CellDependency> {
        return targetCellIds.map { targetCellId ->
            CellDependency(
                sheetId = sheetId,
                sourceCellId = sourceCellId,
                targetCellId = targetCellId,
                createdAt = timestamp,
                updatedAt = timestamp
            )
        }
    }

    /**
     * Build a dependency map for circular dependency detection
     */
    fun buildDependencyMap(
        cellDependencyService: CellDependencyService,
        sheetId: Long,
        dependencies: List<String>
    ): Map<String, List<String>> {
        val dependencyMap = mutableMapOf<String, List<String>>()
        
        for (dependency in dependencies) {
            val dependentCellDependencies = cellDependencyService.getDependenciesBySourceCellId(dependency)
                .map { it.targetCellId }
            
            dependencyMap[dependency] = dependentCellDependencies
        }
        
        return dependencyMap
    }
}
