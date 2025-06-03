package com.sheets.services

import com.sheets.models.domain.Cell

/**
 * Service interface for asynchronous cell operations
 */
interface CellAsyncService {
    /**
     * Asynchronously save a cell to MongoDB
     */
    fun saveCell(cell: Cell)
    
    /**
     * Asynchronously save multiple cells to MongoDB
     */
    fun saveCells(cells: List<Cell>)
    
    /**
     * Asynchronously delete a cell from MongoDB
     */
    fun deleteCell(id: String)
}
