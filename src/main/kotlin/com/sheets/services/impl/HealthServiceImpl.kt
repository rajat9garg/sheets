package com.sheets.services.impl

import com.sheets.services.HealthService
import org.springframework.stereotype.Service

@Service
class HealthServiceImpl : HealthService {
    override fun checkHealth(): String {
        return "UP"
    }
}
