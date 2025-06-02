package com.sheets.repositories

import com.sheets.models.domain.Sheet

interface SheetRepository {

    fun save(sheet: Sheet): Sheet

    fun findById(id: Long): Sheet?
    

    fun findByUserId(userId: Long): List<Sheet>

    fun findByOwnerId(ownerId: Long): List<Sheet>
    

    fun findSharedWithUser(userId: Long): List<Sheet>

    fun update(sheet: Sheet): Sheet

    fun delete(id: Long)
}
