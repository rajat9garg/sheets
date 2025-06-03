package com.sheets.controllers

import com.sheets.generated.api.HealthApi
import com.sheets.generated.model.HealthResponse
import com.sheets.mappers.HealthMapper
import com.sheets.services.HealthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(
    private val healthService: HealthService,
    private val healthMapper: HealthMapper
): HealthApi {

    override fun healthCheck(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = healthService.checkHealth()
            )
        )
    }
}
