package com.sheets.services.expression

import org.springframework.context.annotation.Configuration
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener

@Configuration
class FunctionInitializer(
    private val functionRegistry: FunctionRegistry,
    private val functions: List<ExpressionFunction>
) {
    
    @EventListener(ContextRefreshedEvent::class)
    fun initialize() {
        functions.forEach { function ->
            functionRegistry.registerFunction(function)
        }
    }
}
