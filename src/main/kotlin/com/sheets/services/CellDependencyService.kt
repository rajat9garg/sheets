package com.sheets.services

import com.sheets.models.domain.CellDependency

interface CellDependencyService {
    fun getDependenciesBySourceCellId(sourceCellId: String): List<CellDependency>
    fun getDependenciesByTargetCellId(targetCellId: String): List<CellDependency>
    fun getDependencyBySourceAndTargetCellId(sourceCellId: String, targetCellId: String): CellDependency?
    fun getDependenciesBySheetId(sheetId: Long): List<CellDependency>
    fun createDependency(dependency: CellDependency): CellDependency
    fun createDependencies(dependencies: List<CellDependency>): List<CellDependency>
    fun deleteBySourceCellId(sourceCellId: String): Int
    fun deleteByTargetCellId(targetCellId: String): Int
    fun deleteBySheetId(sheetId: Long): Int
    fun detectCircularDependency(cellId: String): List<String>?
}
