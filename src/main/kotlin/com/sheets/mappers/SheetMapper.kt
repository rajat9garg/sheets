package com.sheets.mappers

import com.sheets.generated.model.CreateSheetRequest
import com.sheets.generated.model.ShareSheetRequest
import com.sheets.generated.model.ShareSheetResponse
import com.sheets.generated.model.SheetDetailsResponse
import com.sheets.generated.model.SheetResponse
import com.sheets.generated.model.SheetSummaryResponse
import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

object SheetMapper {
    
    fun toSheet(request: CreateSheetRequest, userId: Long): Sheet {
        return Sheet(
            name = request.name,
            description = request.description ?: "",
            maxLength = request.maxRows,
            maxBreadth = request.maxColumns,
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
    
    fun toSheetResponse(sheet: Sheet): SheetResponse {
        return SheetResponse(
            id = sheet.id,
            name = sheet.name,
            description = sheet.description,
            maxLength = sheet.maxLength,
            maxBreadth = sheet.maxBreadth,
            userId = sheet.userId,
            createdAt = OffsetDateTime.ofInstant(sheet.createdAt, ZoneOffset.UTC),
            updatedAt = OffsetDateTime.ofInstant(sheet.updatedAt, ZoneOffset.UTC)
        )
    }
    
    fun toSheetSummaryResponse(sheet: Sheet, accessType: String): SheetSummaryResponse {
        val accessTypeEnum = when (accessType) {
            "READ" -> SheetSummaryResponse.AccessType.READ
            "WRITE" -> SheetSummaryResponse.AccessType.WRITE
            "ADMIN" -> SheetSummaryResponse.AccessType.ADMIN
            "OWNER" -> SheetSummaryResponse.AccessType.OWNER
            else -> throw IllegalArgumentException("Invalid access type: $accessType")
        }
        
        return SheetSummaryResponse(
            id = sheet.id,
            name = sheet.name,
            description = sheet.description,
            accessType = accessTypeEnum,
            createdAt = OffsetDateTime.ofInstant(sheet.createdAt, ZoneOffset.UTC),
            updatedAt = OffsetDateTime.ofInstant(sheet.updatedAt, ZoneOffset.UTC)
        )
    }
    
    fun toSheetDetailsResponse(sheet: Sheet, accessType: String): SheetDetailsResponse {
        val accessTypeEnum = when (accessType) {
            "READ" -> SheetDetailsResponse.AccessType.READ
            "WRITE" -> SheetDetailsResponse.AccessType.WRITE
            "ADMIN" -> SheetDetailsResponse.AccessType.ADMIN
            "OWNER" -> SheetDetailsResponse.AccessType.OWNER
            else -> throw IllegalArgumentException("Invalid access type: $accessType")
        }
        
        return SheetDetailsResponse(
            id = sheet.id,
            name = sheet.name,
            description = sheet.description,
            maxLength = sheet.maxLength,
            maxBreadth = sheet.maxBreadth,
            userId = sheet.userId,
            accessType = accessTypeEnum,
            createdAt = OffsetDateTime.ofInstant(sheet.createdAt, ZoneOffset.UTC),
            updatedAt = OffsetDateTime.ofInstant(sheet.updatedAt, ZoneOffset.UTC)
        )
    }
    
    fun toAccessType(accessTypeEnum: ShareSheetRequest.AccessType): AccessType {
        return when (accessTypeEnum) {
            ShareSheetRequest.AccessType.READ -> AccessType.READ
            ShareSheetRequest.AccessType.WRITE -> AccessType.WRITE
            ShareSheetRequest.AccessType.ADMIN -> AccessType.ADMIN
        }
    }
    
    fun toAccessTypeString(accessType: AccessType): String {
        return accessType.name
    }
    
    fun toShareSheetResponse(message: String): ShareSheetResponse {
        return ShareSheetResponse(message = message)
    }
}
