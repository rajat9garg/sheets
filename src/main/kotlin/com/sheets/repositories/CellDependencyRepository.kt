package com.sheets.repositories

import com.sheets.models.domain.CellDependency

/**
 * Repository interface for cell dependency operations
 */
interface CellDependencyRepository {
    /**
     * Find a cell dependency by its ID
     * 
     * @param id The ID of the cell dependency to find
     * @return The cell dependency if found, null otherwise
     */
    fun findById(id: String): CellDependency?
    
    /**
     * Find all dependencies where the specified cell is the source
     * 
     * @param sourceCellId The ID of the source cell
     * @return A list of cell dependencies
     */
    fun findBySourceCellId(sourceCellId: String): List<CellDependency>
    
    /**
     * Find all dependencies where the specified cell is the target
     * 
     * @param targetCellId The ID of the target cell
     * @return A list of cell dependencies
     */
    fun findByTargetCellId(targetCellId: String): List<CellDependency>
    
    /**
     * Find a specific dependency between source and target cells
     * 
     * @param sourceCellId The ID of the source cell
     * @param targetCellId The ID of the target cell
     * @return The cell dependency if found, null otherwise
     */
    fun findBySourceCellIdAndTargetCellId(sourceCellId: String, targetCellId: String): CellDependency?
    
    /**
     * Find all dependencies in a sheet
     * 
     * @param sheetId The ID of the sheet
     * @return A list of cell dependencies
     */
    fun findBySheetId(sheetId: Long): List<CellDependency>
    
    /**
     * Save a cell dependency
     * 
     * @param dependency The cell dependency to save
     * @return The saved cell dependency
     */
    fun save(dependency: CellDependency): CellDependency
    
    /**
     * Save multiple cell dependencies
     * 
     * @param dependencies The cell dependencies to save
     * @return The saved cell dependencies
     */
    fun saveAll(dependencies: List<CellDependency>): List<CellDependency>
    
    /**
     * Delete a cell dependency by its ID
     * 
     * @param id The ID of the cell dependency to delete
     */
    fun deleteById(id: String)
    
    /**
     * Delete all dependencies where the specified cell is the source
     * 
     * @param sourceCellId The ID of the source cell
     * @return The number of dependencies deleted
     */
    fun deleteBySourceCellId(sourceCellId: String): Int
    
    /**
     * Delete all dependencies where the specified cell is the target
     * 
     * @param targetCellId The ID of the target cell
     * @return The number of dependencies deleted
     */
    fun deleteByTargetCellId(targetCellId: String): Int
    
    /**
     * Delete all dependencies in a sheet
     * 
     * @param sheetId The ID of the sheet
     * @return The number of dependencies deleted
     */
    fun deleteBySheetId(sheetId: Long): Int
}
