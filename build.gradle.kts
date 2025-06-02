import org.flywaydb.gradle.task.FlywayCleanTask
import org.flywaydb.gradle.task.FlywayMigrateTask

plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.openapi.generator") version "7.5.0"
    id("nu.studer.jooq") version "7.1"
    id("org.flywaydb.flyway") version "9.16.1"
}

group = "learn.ai"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// Database configuration from gradle.properties
val dbUrl: String by project
val dbUser: String by project
val dbPassword: String by project


val migrateBrahmaTables by tasks.register("migrateTables", FlywayMigrateTask::class) {
    url = "jdbc:postgresql://localhost:5432/sheets"
    user = "postgres"
    password = "postgres"
    locations = arrayOf("filesystem:${project.projectDir}/src/main/resources/db/migration")
    baselineVersion = "0"
    baselineOnMigrate = true
    cleanDisabled = false
    schemas = arrayOf("public")
    table = "flyway_schema_history"
}

val cleanBrahmaFlyway by tasks.register("cleanFlyway", FlywayCleanTask::class) {
    url = "jdbc:postgresql://localhost:5432/sheets"
    user = "postgres"
    password = "postgres"
    locations = arrayOf("filesystem:${project.projectDir}/src/main/resources/db/migration")
    baselineVersion = "0"
    baselineOnMigrate = true
    cleanDisabled = false
    schemas = arrayOf("public")
    table = "flyway_schema_history"
}


// Flyway Configuration
flyway {
    url = "jdbc:postgresql://localhost:5432/sheets"
    user = "postgres"
    password = "postgres"
    schemas = arrayOf("public")
    locations = arrayOf("classpath:db/migration")
    baselineOnMigrate = true
    cleanDisabled = false
    driver = "org.postgresql.Driver"
    loggers = arrayOf("slf4j")
}



// JOOQ Configuration
jooq {
    version.set("3.19.3")
    edition = nu.studer.gradle.jooq.JooqEdition.OSS
    
    configurations {
        create("main") {
            jooqConfiguration.apply {
                logging = org.jooq.meta.jaxb.Logging.WARN
                jdbc.apply {
                    driver = "org.postgresql.Driver"
                    url = "jdbc:postgresql://localhost:5432/sheets"
                    user = "postgres"
                    password = "postgres"
                }
                generator.apply {
                    name = "org.jooq.codegen.DefaultGenerator"
                    database.apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                        excludes = "flyway_schema_history"
                        includes = ".*"
                        recordVersionFields = "version"
                        recordTimestampFields = "created_at|updated_at"
                    }
                    generate.apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = true
                        isFluentSetters = true
                    }
                    target.apply {
                        packageName = "com.sheets.infrastructure.jooq"
                        directory = "build/generated/jooq"
                    }
                    strategy.name = "org.jooq.codegen.DefaultGeneratorStrategy"
                }
            }
        }
    }
}

// OpenAPI Generator Configuration
openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$rootDir/src/main/resources/openapi/api.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.absolutePath)
    apiPackage.set("com.sheets.generated.api")
    modelPackage.set("com.sheets.generated.model")
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useBeanValidation" to "true",
            "documentationProvider" to "none",
            "serializationLibrary" to "jackson",
            "useTags" to "true",
            "enumPropertyNaming" to "UPPERCASE"
        )
    )
    globalProperties.set(
        mapOf(
            "apis" to "",
            "models" to "",
            "modelDocs" to "false"
        )
    )
}

// Add generated sources to the source sets
sourceSets.main {
    kotlin {
        srcDirs(
            layout.buildDirectory.dir("generated/jooq"),
            layout.buildDirectory.dir("generated/openapi/src/main/kotlin")
        )
    }
}

// Ensure code generation tasks run before compilation
tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
    dependsOn("generateJooq")
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Redis
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    
    // MongoDB
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    
    // JOOQ
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jooq:jooq:3.19.3")
    
    // Database
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("org.flywaydb:flyway-core")
    
    // JOOQ Generator
    jooqGenerator("org.postgresql:postgresql:42.6.0")
    
    // OpenAPI / Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0")
    implementation("org.openapitools:jackson-databind-nullable:0.2.6")

	jooqGenerator("org.jooq:jooq-codegen:3.19.3")
    jooqGenerator("org.jooq:jooq-meta:3.19.3")

    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
