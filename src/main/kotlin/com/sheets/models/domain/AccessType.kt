package com.sheets.models.domain

/**
 * Enum representing the types of access a user can have to a sheet
 */
enum class AccessType {
    /**
     * Read-only access to the sheet
     */
    READ,
    
    /**
     * Read and write access to the sheet
     */
    WRITE,
    
    /**
     * Administrative access to the sheet (can share with others)
     */
    ADMIN,
    
    /**
     * Owner of the sheet (full control, cannot be removed)
     */
    OWNER
}
