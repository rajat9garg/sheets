package com.sheets.services

import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import com.sheets.models.dto.SheetAndAccessType

interface SheetService {
    /**
     * Creates a new sheet for the specified user
     * 
     * @param name The name of the sheet
     * @param description Optional description of the sheet, null if not provided
     * @param userId The ID of the user creating the sheet
     * @return The created Sheet object
     */
    fun createSheet(name: String, description: String?, userId: Long): Sheet
    
    /**
     * Retrieves all sheets that the specified user has access to
     * 
     * @param userId The ID of the user
     * @return List of sheets with their respective access types
     */
    fun getSheetsByUserId(userId: Long): List<SheetAndAccessType>
    
    /**
     * Retrieves a specific sheet by ID if the user has access to it
     * 
     * @param sheetId The ID of the sheet to retrieve
     * @param userId The ID of the user requesting the sheet
     * @return The sheet with access type if the user has access
     * @throws NoSuchElementException if the sheet does not exist
     * @throws SecurityException if the user does not have access to the sheet
     */
    fun getSheetById(sheetId: Long, userId: Long): SheetAndAccessType
    
    /**
     * Shares a sheet with one or more users
     * 
     * @param sheetId The ID of the sheet to share
     * @param userIds List of user IDs to share the sheet with
     * @param accessType The type of access to grant
     * @param ownerId The ID of the user sharing the sheet (must be the owner)
     * @return A message indicating the result of the share operation
     * @throws NoSuchElementException if the sheet does not exist
     * @throws SecurityException if the user is not the owner of the sheet
     */
    fun shareSheet(sheetId: Long, userIds: List<Long>, accessType: AccessType, ownerId: Long): String
}
