package com.sheets.services

import com.sheets.models.domain.Cell
import java.util.concurrent.CompletableFuture

interface CellService {
    fun getCell(id: String): Cell?
    fun getCellsBySheetId(sheetId: Long): List<Cell>
    fun updateCell(cell: Cell, userId: String): Cell
    fun deleteCell(id: String, userId: String)
}
