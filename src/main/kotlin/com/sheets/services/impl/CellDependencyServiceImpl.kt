package com.sheets.services.impl

import com.sheets.models.domain.CellDependency
import com.sheets.repositories.CellDependencyRepository
import com.sheets.services.CellDependencyService
import com.sheets.services.expression.CircularDependencyDetector
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class CellDependencyServiceImpl(
    private val cellDependencyRepository: CellDependencyRepository,
    private val circularDependencyDetector: CircularDependencyDetector
) : CellDependencyService {
    
    override fun getDependenciesBySourceCellId(sourceCellId: String): List<CellDependency> {
        return cellDependencyRepository.findBySourceCellId(sourceCellId)
    }
    
    override fun getDependenciesByTargetCellId(targetCellId: String): List<CellDependency> {
        return cellDependencyRepository.findByTargetCellId(targetCellId)
    }
    
    override fun getDependencyBySourceAndTargetCellId(sourceCellId: String, targetCellId: String): CellDependency? {
        return cellDependencyRepository.findBySourceCellIdAndTargetCellId(sourceCellId, targetCellId)
    }
    
    override fun getDependenciesBySheetId(sheetId: Long): List<CellDependency> {
        return cellDependencyRepository.findBySheetId(sheetId)
    }
    
    override fun createDependency(dependency: CellDependency): CellDependency {
        return cellDependencyRepository.save(dependency)
    }
    
    override fun createDependencies(dependencies: List<CellDependency>): List<CellDependency> {
        return cellDependencyRepository.saveAll(dependencies)
    }
    
    override fun deleteBySourceCellId(sourceCellId: String): Int {
        return cellDependencyRepository.deleteBySourceCellId(sourceCellId)
    }
    
    override fun deleteByTargetCellId(targetCellId: String): Int {
        return cellDependencyRepository.deleteByTargetCellId(targetCellId)
    }
    
    override fun deleteBySheetId(sheetId: Long): Int {
        return cellDependencyRepository.deleteBySheetId(sheetId)
    }
    
    override fun detectCircularDependency(cellId: String): List<String>? {
        val allDependencies = cellDependencyRepository.findBySheetId(extractSheetId(cellId))
        val dependencyMap = buildDependencyMap(allDependencies)
        return circularDependencyDetector.detectCircularDependency(cellId, dependencyMap)
    }
    
    private fun buildDependencyMap(dependencies: List<CellDependency>): Map<String, List<String>> {
        val dependencyMap = mutableMapOf<String, MutableList<String>>()
        
        for (dependency in dependencies) {
            val sourceCellId = dependency.sourceCellId
            val targetCellId = dependency.targetCellId
            
            if (sourceCellId !in dependencyMap) {
                dependencyMap[sourceCellId] = mutableListOf()
            }
            
            dependencyMap[sourceCellId]?.add(targetCellId)
        }
        
        return dependencyMap
    }
    
    private fun extractSheetId(cellId: String): Long {
        val parts = cellId.split(":")
        if (parts.size < 1) {
            throw IllegalArgumentException("Invalid cell ID format: $cellId")
        }
        return parts[0].toLong()
    }
}
