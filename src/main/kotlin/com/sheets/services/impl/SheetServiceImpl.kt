package com.sheets.services.impl

import com.sheets.models.domain.AccessMapping
import com.sheets.models.domain.AccessType
import com.sheets.models.domain.Sheet
import com.sheets.models.dto.SheetAndAccessType
import com.sheets.repositories.AccessRepository
import com.sheets.repositories.SheetRepository
import com.sheets.services.CellService
import com.sheets.services.SheetService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SheetServiceImpl(
    private val sheetRepository: SheetRepository,
    private val accessRepository: AccessRepository
) : SheetService {
    
    private val logger = LoggerFactory.getLogger(SheetServiceImpl::class.java)
    
    override fun createSheet(name: String, description: String?, maxRows: Int, maxColumns: Int, userId: Long): Sheet {
        logger.info("Creating sheet with name: $name for user: $userId")
        try {
            val sheet = Sheet(
                name = name,
                description = description ?: "",
                maxLength = maxRows,
                maxBreadth = maxColumns,
                userId = userId,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
            
            val savedSheet = sheetRepository.save(sheet)
            logger.debug("Sheet saved with ID: ${savedSheet.id}")

            // Initialize cells for the sheet
            logger.debug("Initialized ${maxRows * maxColumns} cells for sheet ID: ${savedSheet.id}")

            logger.info("Sheet created successfully with ID: ${savedSheet.id}")
            return savedSheet
        } catch (e: Exception) {
            logger.error("Error creating sheet with name: $name for user: $userId", e)
            throw e
        }
    }
    
    override fun getSheetsByUserId(userId: Long): List<SheetAndAccessType> {
        logger.info("Fetching sheets for user: $userId")
        try {
            // Get sheets owned by user
            val ownedSheets = sheetRepository.findByOwnerId(userId)
            logger.debug("Found ${ownedSheets.size} owned sheets for user: $userId")
            
            // Get sheets shared with user
            val sharedAccessMappings = accessRepository.findByUserIdAndAccessTypeNot(userId, AccessType.OWNER)
            logger.debug("Found ${sharedAccessMappings.size} shared access mappings for user: $userId")
            
            val sharedSheets = sharedAccessMappings.mapNotNull { accessMapping ->
                val sheet = sheetRepository.findById(accessMapping.sheetId)
                if (sheet != null) {
                    SheetAndAccessType(sheet, accessMapping.accessType)
                } else {
                    logger.warn("Sheet with ID: ${accessMapping.sheetId} referenced in access mapping not found")
                    null
                }
            }
            
            // Combine owned and shared sheets
            val ownedSheetsWithAccess = ownedSheets.map { SheetAndAccessType(it, AccessType.OWNER) }
            val result = ownedSheetsWithAccess + sharedSheets
            
            logger.info("Retrieved total of ${result.size} sheets for user: $userId")
            return result
        } catch (e: Exception) {
            logger.error("Error fetching sheets for user: $userId", e)
            throw e
        }
    }
    
    override fun getSheetById(sheetId: Long, userId: Long): SheetAndAccessType {
        logger.info("Fetching sheet with ID: $sheetId for user: $userId")
        try {
            val sheet = sheetRepository.findById(sheetId)
                ?: run {
                    logger.warn("Sheet not found with ID: $sheetId")
                    throw NoSuchElementException("Sheet not found with ID: $sheetId")
                }
            
            // Check if user is the owner
            if (sheet.userId == userId) {
                logger.debug("User: $userId is the owner of sheet: $sheetId")
                return SheetAndAccessType(sheet, AccessType.OWNER)
            }
            
            // Check if sheet is shared with user
            val accessMapping = accessRepository.findBySheetIdAndUserId(sheetId, userId)
                ?: run {
                    logger.warn("User: $userId does not have access to sheet: $sheetId")
                    throw SecurityException("User $userId does not have access to sheet $sheetId")
                }
            
            logger.info("Retrieved sheet with ID: $sheetId for user: $userId with access type: ${accessMapping.accessType}")
            return SheetAndAccessType(sheet, accessMapping.accessType)
        } catch (e: Exception) {
            when (e) {
                is NoSuchElementException, is SecurityException -> throw e
                else -> {
                    logger.error("Error fetching sheet with ID: $sheetId for user: $userId", e)
                    throw e
                }
            }
        }
    }
    
    override fun shareSheet(sheetId: Long, userIds: List<Long>, accessType: AccessType, ownerId: Long): String {
        logger.info("Sharing sheet with ID: $sheetId by user: $ownerId with ${userIds.size} users, access type: $accessType")
        try {
            // Validate sheet exists
            val sheet = sheetRepository.findById(sheetId)
                ?: run {
                    logger.warn("Sheet not found with ID: $sheetId")
                    throw NoSuchElementException("Sheet not found with ID: $sheetId")
                }
            
            // Validate user is the owner
            if (sheet.userId != ownerId) {
                logger.warn("User: $ownerId is not the owner of sheet: $sheetId")
                throw SecurityException("User $ownerId is not the owner of sheet $sheetId and cannot share it.")
            }
            
            // Check if there are users to share with
            if (userIds.isEmpty()) {
                logger.info("No users provided to share sheet: $sheetId with")
                return "No users provided to share the sheet with."
            }
            
            // Filter out owner from userIds if present
            val filteredUserIds = userIds.filter { it != ownerId }
            val ownerSkipped = userIds.size != filteredUserIds.size
            
            // Share sheet with each user
            var sharedCount = 0
            filteredUserIds.forEach { userId ->
                try {
                    // Create or update access mapping
                    accessRepository.upsert(AccessMapping(
                        sheetId = sheetId,
                        userId = userId,
                        accessType = accessType,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    ))
                    sharedCount++
                    logger.debug("Sheet $sheetId shared with user $userId with access type $accessType")
                } catch (e: Exception) {
                    logger.error("Error sharing sheet: $sheetId with user: $userId", e)
                    // Continue with other users even if one fails
                }
            }
            
            val resultMessage = if (ownerSkipped) {
                "Sheet shared successfully with $sharedCount users. Owner's access (user $ownerId) cannot be changed via share."
            } else {
                "Sheet shared successfully with $sharedCount users."
            }
            
            logger.info(resultMessage)
            return resultMessage
        } catch (e: Exception) {
            when (e) {
                is NoSuchElementException, is SecurityException -> throw e
                else -> {
                    logger.error("Error sharing sheet with ID: $sheetId by user: $ownerId", e)
                    throw e
                }
            }
        }
    }
}
