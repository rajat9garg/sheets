package com.sheets.services.expression.impl

import com.sheets.services.expression.CircularDependencyDetector
import org.springframework.stereotype.Component

@Component
class CircularDependencyDetectorImpl : CircularDependencyDetector {
    
    override fun detectCircularDependency(cellId: String, dependencies: Map<String, List<String>>): List<String>? {
        val visited = mutableSetOf<String>()
        val path = mutableListOf<String>()
        
        fun dfs(current: String): List<String>? {
            if (current in path) {
                return path.subList(path.indexOf(current), path.size) + current
            }
            
            if (current in visited) {
                return null
            }
            
            visited.add(current)
            path.add(current)
            
            val dependsOn = dependencies[current] ?: emptyList()
            for (next in dependsOn) {
                val cycle = dfs(next)
                if (cycle != null) {
                    return cycle
                }
            }
            
            path.removeAt(path.size - 1)
            return null
        }
        
        return dfs(cellId)
    }
}
