import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
    id("io.gatling.gradle") version "3.10.3.1"
    application
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

dependencies {
    // Core dependencies
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    
    // Gatling
    implementation("io.gatling:gatling-core:3.10.3")
    implementation("io.gatling:gatling-http:3.10.3")
    implementation("io.gatling:gatling-app:3.10.3")
    implementation("io.gatling.highcharts:gatling-charts-highcharts:3.10.3")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Configuration
    implementation("com.typesafe:config:1.4.3")
    
    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

application {
    mainClass.set("com.stress.test.StressTestEngineKt")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Create a fat JAR that includes all dependencies
tasks.register<Jar>("stressTestJar") {
    archiveClassifier.set("stress-test")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    
    manifest {
        attributes["Main-Class"] = "com.stress.test.StressTestEngineKt"
    }
    
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}

// Task to run the stress test with a specific configuration file
tasks.register<JavaExec>("runStressTest") {
    group = "application"
    description = "Run stress test with a specific configuration file"
    
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.stress.test.StressTestEngineKt")
    
    // Allow passing config file path as a system property
    systemProperties(System.getProperties().map { it.key.toString() to it.value }.toMap())
    
    doFirst {
        println("Running stress test with configuration: ${System.getProperty("config") ?: "default"}")
    }
}
