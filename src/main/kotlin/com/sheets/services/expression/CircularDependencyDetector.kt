package com.sheets.services.expression

interface CircularDependencyDetector {
    fun detectCircularDependency(cellId: String, dependencies: Map<String, List<String>>): List<String>?
}
