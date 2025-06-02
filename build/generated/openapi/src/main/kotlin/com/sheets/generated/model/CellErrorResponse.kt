package com.sheets.generated.model

import java.util.Objects
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import jakarta.validation.Valid

/**
 * 
 * @param status HTTP status code
 * @param error Error type
 * @param message Error message
 * @param path Request path
 * @param timestamp Time when the error occurred
 * @param details Additional error details
 */
data class CellErrorResponse(

    @get:JsonProperty("status") val status: kotlin.Int? = null,

    @get:JsonProperty("error") val error: kotlin.String? = null,

    @get:JsonProperty("message") val message: kotlin.String? = null,

    @get:JsonProperty("path") val path: kotlin.String? = null,

    @get:JsonProperty("timestamp") val timestamp: java.time.OffsetDateTime? = null,

    @field:Valid
    @get:JsonProperty("details") val details: kotlin.collections.Map<kotlin.String, kotlin.Any>? = null
) {

}

