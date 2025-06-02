package com.sheets.controllers

import com.sheets.generated.api.SheetApi
import com.sheets.generated.model.CreateSheetRequest
import com.sheets.generated.model.ShareSheetRequest
import com.sheets.generated.model.ShareSheetResponse
import com.sheets.generated.model.SheetDetailsResponse
import com.sheets.generated.model.SheetResponse
import com.sheets.generated.model.SheetSummaryResponse
import com.sheets.mappers.SheetMapper
import com.sheets.services.SheetService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class SheetController(private val sheetService: SheetService) : SheetApi {

    private val logger = LoggerFactory.getLogger(SheetController::class.java)

    override fun createSheet(
        xUserId: Long,
        createSheetRequest: CreateSheetRequest
    ): ResponseEntity<SheetResponse> {
        logger.info("Creating sheet for user: $xUserId with name: ${createSheetRequest.name}")
        try {
            val sheet = SheetMapper.toSheet(createSheetRequest, xUserId)
            val createdSheet = sheetService.createSheet(
                sheet.name,
                sheet.description,
                maxRows = createSheetRequest.maxRows ?: 100,
                maxColumns = createSheetRequest.maxColumns ?: 100,
                userId = xUserId
            )
            
            logger.info("Sheet created successfully with ID: ${createdSheet.id}")
            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(SheetMapper.toSheetResponse(createdSheet))
        } catch (e: Exception) {
            logger.error("Error creating sheet for user: $xUserId", e)
            throw e
        }
    }

    override fun getSheets(xUserId: Long): ResponseEntity<List<SheetSummaryResponse>> {
        logger.info("Fetching sheets for user: $xUserId")
        try {
            val sheetAndAccessTypes = sheetService.getSheetsByUserId(xUserId)
            val response = sheetAndAccessTypes.map { (sheet, accessType) ->
                SheetMapper.toSheetSummaryResponse(sheet, accessType.name)
            }
            
            logger.info("Retrieved ${response.size} sheets for user: $xUserId")
            return ResponseEntity.ok(response)
        } catch (e: Exception) {
            logger.error("Error fetching sheets for user: $xUserId", e)
            throw e
        }
    }

    override fun getSheetDetails(
        xUserId: Long,
        sheetId: Long
    ): ResponseEntity<SheetDetailsResponse> {
        logger.info("Fetching sheet with ID: $sheetId for user: $xUserId")
        try {
            val (sheet, accessType) = sheetService.getSheetById(sheetId, xUserId)
            val response = SheetMapper.toSheetDetailsResponse(sheet, accessType.name)
            
            logger.info("Retrieved sheet with ID: $sheetId for user: $xUserId with access: ${accessType.name}")
            return ResponseEntity.ok(response)
        } catch (e: NoSuchElementException) {
            logger.error("Sheet not found with ID: $sheetId for user: $xUserId", e)
            throw e
        } catch (e: SecurityException) {
            logger.error("Access denied to sheet with ID: $sheetId for user: $xUserId", e)
            throw e
        } catch (e: Exception) {
            logger.error("Error fetching sheet with ID: $sheetId for user: $xUserId", e)
            throw e
        }
    }

    override fun shareSheet(
        xUserId: Long,
        sheetId: Long,
        shareSheetRequest: ShareSheetRequest
    ): ResponseEntity<ShareSheetResponse> {
        logger.info("Sharing sheet with ID: $sheetId by user: $xUserId with ${shareSheetRequest.userIds.size} users")
        try {
            val accessType = SheetMapper.toAccessType(shareSheetRequest.accessType)
            val result = sheetService.shareSheet(sheetId, shareSheetRequest.userIds, accessType, xUserId)
            
            logger.info("Sheet shared successfully: $result")
            return ResponseEntity.ok(SheetMapper.toShareSheetResponse(result))
        } catch (e: NoSuchElementException) {
            logger.error("Sheet not found with ID: $sheetId for sharing by user: $xUserId", e)
            throw e
        } catch (e: SecurityException) {
            logger.error("Access denied to share sheet with ID: $sheetId for user: $xUserId", e)
            throw e
        } catch (e: Exception) {
            logger.error("Error sharing sheet with ID: $sheetId by user: $xUserId", e)
            throw e
        }
    }
}
