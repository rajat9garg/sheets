package com.sheets.services

import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import com.sheets.models.dto.SheetAndAccessType

interface SheetService {

    fun createSheet(name: String, description: String?, maxRows: Int, maxColumns: Int, userId: Long): Sheet

    fun getSheetsByUserId(userId: Long): List<SheetAndAccessType>

    fun getSheetById(sheetId: Long, userId: Long): SheetAndAccessType

    fun shareSheet(sheetId: Long, userIds: List<Long>, accessType: AccessType, ownerId: Long): String
}
