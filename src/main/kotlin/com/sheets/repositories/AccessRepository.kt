package com.sheets.repositories

import com.sheets.models.domain.AccessMapping
import com.sheets.models.domain.AccessType
import java.util.UUID

interface AccessRepository {

    /**
     * Saves a new access mapping to the database
     * @param accessMapping The access mapping to save
     * @return The saved access mapping with ID and timestamps
     */
    fun save(accessMapping: AccessMapping): AccessMapping

    /**
     * Finds an access mapping by its ID
     * @param id The ID of the access mapping to find
     * @return The access mapping if found, null otherwise
     */
    fun findById(id: UUID): AccessMapping?
    
    /**
     * Finds an access mapping by sheet ID and user ID
     * @param sheetId The ID of the sheet
     * @param userId The ID of the user
     * @return The access mapping if found, null otherwise
     */
    fun findBySheetIdAndUserId(sheetId: Long, userId: Long): AccessMapping?
    
    /**
     * Finds all access mappings for a sheet
     * @param sheetId The ID of the sheet
     * @return List of access mappings for the sheet
     */
    fun findBySheetId(sheetId: Long): List<AccessMapping>

    /**
     * Finds all access mappings for a user
     * @param userId The ID of the user
     * @return List of access mappings for the user
     */
    fun findByUserId(userId: Long): List<AccessMapping>

    /**
     * Finds all access mappings for a user with a specific access type
     * @param userId The ID of the user
     * @param accessType The access type to filter by
     * @return List of access mappings matching the criteria
     */
    fun findByUserIdAndAccessType(userId: Long, accessType: AccessType): List<AccessMapping>
    
    /**
     * Finds all access mappings for a user with an access type other than the specified one
     * @param userId The ID of the user
     * @param accessType The access type to exclude
     * @return List of access mappings matching the criteria
     */
    fun findByUserIdAndAccessTypeNot(userId: Long, accessType: AccessType): List<AccessMapping>
    
    /**
     * Updates an existing access mapping
     * @param accessMapping The access mapping to update
     * @return The updated access mapping
     */
    fun update(accessMapping: AccessMapping): AccessMapping
    
    /**
     * Inserts a new access mapping or updates an existing one if it already exists
     * @param accessMapping The access mapping to upsert
     */
    fun upsert(accessMapping: AccessMapping)

    /**
     * Deletes an access mapping by its ID
     * @param id The ID of the access mapping to delete
     */
    fun delete(id: UUID)
    
    /**
     * Deletes an access mapping by sheet ID and user ID
     * @param sheetId The ID of the sheet
     * @param userId The ID of the user
     */
    fun deleteBySheetIdAndUserId(sheetId: Long, userId: Long)
}
