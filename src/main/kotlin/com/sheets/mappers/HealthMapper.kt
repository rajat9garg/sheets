package com.sheets.mappers

import com.sheets.models.dto.HealthResponse
import org.springframework.stereotype.Component

@Component
class HealthMapper {
    fun toHealthResponse(status: String): HealthResponse {
        return HealthResponse(status = status)
    }
}
