package com.sheets.services

import com.sheets.models.domain.Cell
import com.sheets.models.domain.CellDependency


interface CellAsyncService {

    fun saveCell(cell: Cell)
    

    fun saveCells(cells: List<Cell>)
    

    fun deleteCell(id: String)

    fun updateDependencies(cellId: String, dependencies: List<String>, sheetId: Long, timestamp: java.time.Instant)

    fun updateDependentCells(cellId: String, userId: String)
}
