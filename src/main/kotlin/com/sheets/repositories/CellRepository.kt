package com.sheets.repositories

import com.sheets.models.domain.Cell

/**
 * Repository interface for cell operations
 */
interface CellRepository {
    /**
     * Find a cell by its ID
     * 
     * @param id The ID of the cell to find
     * @return The cell if found, null otherwise
     */
    fun findById(id: String): Cell?
    
    /**
     * Find all cells in a sheet
     * 
     * @param sheetId The ID of the sheet
     * @return A list of cells in the sheet
     */
    fun findBySheetId(sheetId: Long): List<Cell>
    
    /**
     * Save a cell
     * 
     * @param cell The cell to save
     * @return The saved cell
     */
    fun save(cell: Cell): Cell
    
    /**
     * Save multiple cells
     * 
     * @param cells The cells to save
     * @return The saved cells
     */
    fun saveAll(cells: List<Cell>): List<Cell>
    
    /**
     * Delete a cell by its ID
     * 
     * @param id The ID of the cell to delete
     */
    fun deleteById(id: String)
    
    /**
     * Delete all cells in a sheet
     * 
     * @param sheetId The ID of the sheet
     */
    fun deleteBySheetId(sheetId: Long)
}
