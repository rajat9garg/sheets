package com.sheets.models.dto

import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet

/**
 * Data transfer object that combines a Sheet with its AccessType for a specific user
 * Used to represent a sheet along with the access level a user has to it
 *
 * @property sheet The sheet entity
 * @property accessType The type of access the user has to this sheet
 */
data class SheetAndAccessType(
    val sheet: Sheet,
    val accessType: AccessType
)
