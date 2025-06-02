package com.sheets.services

interface CellLockService {
    fun acquireLock(cellId: String, userId: String, timeoutMs: Long = 30000): Boolean
    fun releaseLock(cellId: String, userId: String): Boolean
    fun isLocked(cellId: String): Boolean
    fun getLockOwner(cellId: String): String?
}
