package com.sheets

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [FlywayAutoConfiguration::class])
class SheetsApplication

fun main(args: Array<String>) {
    runApplication<SheetsApplication>(*args)
}
