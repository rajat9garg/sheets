package com.sheets.models.domain

/**
 * Enum representing the types of data a cell can contain
 */
enum class DataType {
    /**
     * Primitive data type (string, number, boolean)
     */
    PRIMITIVE,
    
    /**
     * Expression data type (formula)
     */
    EXPRESSION
}
